#!/usr/bin/env bash
# One-time setup of the in-app update server on an Ubuntu/Debian VM (e.g. an Azure VM). //gimhan added
#
# Installs Caddy, which serves /var/www/iso8583studio over HTTPS and renews the Let's Encrypt
# certificate on its own. The app then reads https://<host>/iso8583studio/latest.json.
#
#   scp deploy/update-server/setup.sh azureuser@<vm-ip>:
#   ssh azureuser@<vm-ip> 'sudo bash setup.sh <name>.<region>.cloudapp.azure.com'
#
# Before running it:
#   * Give the VM's public IP a DNS name: Azure portal -> the public IP resource -> Configuration ->
#     "DNS name label". Let's Encrypt needs a name, not a bare IP.
#   * Allow inbound TCP 80 and 443 in the VM's network security group. Port 80 is needed for the
#     certificate check and redirects to HTTPS.
#   * Nothing else may be listening on 80/443 (nginx, apache). If something is, serve the folder from
#     that server instead, with the same layout.
set -euo pipefail

HOST="${1:?usage: sudo bash setup.sh <dns-name>   e.g. posplatform.southeastasia.cloudapp.azure.com}"
DIR=/var/www/iso8583studio
# The account that publishes over scp; it must own the folder. Defaults to whoever ran sudo.
PUBLISHER="${2:-${SUDO_USER:-azureuser}}"

if [[ $EUID -ne 0 ]]; then echo "Run with sudo." >&2; exit 1; fi

echo "==> Installing Caddy"
if ! command -v caddy >/dev/null; then
  apt-get update
  apt-get install -y debian-keyring debian-archive-keyring apt-transport-https curl gnupg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
    | gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
    > /etc/apt/sources.list.d/caddy-stable.list
  chmod o+r /usr/share/keyrings/caddy-stable-archive-keyring.gpg /etc/apt/sources.list.d/caddy-stable.list
  apt-get update
  apt-get install -y caddy
fi

echo "==> Creating $DIR (owned by $PUBLISHER, readable by Caddy)"
mkdir -p "$DIR"
chown "$PUBLISHER":"$PUBLISHER" "$DIR"
chmod 755 "$DIR"

echo "==> Writing /etc/caddy/Caddyfile"
if [[ -f /etc/caddy/Caddyfile && ! -f /etc/caddy/Caddyfile.before-iso8583studio ]]; then
  cp /etc/caddy/Caddyfile /etc/caddy/Caddyfile.before-iso8583studio
fi
cat > /etc/caddy/Caddyfile <<EOF
# In-app updates for POS Testing Platform / ISO8583Studio.
$HOST {
	handle_path /iso8583studio/* {
		root * $DIR
		# latest.json changes with every release; never let a proxy cache it.
		header /latest.json Cache-Control "no-cache"
		# Hide work-in-progress uploads.
		@partial path *.part *.tmp
		respond @partial 404
		file_server
	}
	respond 404
}
EOF
caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
systemctl enable caddy >/dev/null
systemctl reload caddy || systemctl restart caddy

cat <<EOF

Done. Caddy fetches the HTTPS certificate on the first request; that can take a minute.

  In the project's gradle.properties put:
    updateSource=https://$HOST/iso8583studio
    publishTo=$PUBLISHER@<this VM's IP or $HOST>:$DIR

  Then publish a release from your PC:
    gradlew :composeApp:publishUpdate -PreleaseNotes="..."

  Check it with:
    curl https://$HOST/iso8583studio/latest.json
EOF
