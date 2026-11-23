#!/usr/bin/env bash
# One-time setup of the in-app update server on an Ubuntu/Debian VM (e.g. an Azure VM). //gimhan added
#
# What it sets up: nginx, serving the files in /var/www/pos-testing-platform over plain HTTP. That folder
# holds latest.json (which version is newest) and the installers. There is no application, database
# or login on the server; the app only downloads files from it, and publishUpdate uploads them with scp.
#
#   scp deploy/update-server/setup.sh azureuser@<vm-ip>:
#   ssh azureuser@<vm-ip> "sudo bash setup.sh"          # serves on port 80
#   ssh azureuser@<vm-ip> "sudo bash setup.sh 8088"     # or another port, if 80 is taken
#
# Afterwards the app reads   http://<vm-ip>/pos-testing-platform/latest.json   (or http://<vm-ip>:8088/...).
# In Azure, allow inbound TCP on that port in the VM's network security group.
set -euo pipefail

PORT="${1:-80}"
DIR=/var/www/pos-testing-platform
# The account that publishes over scp; it must own the folder. Defaults to whoever ran sudo.
PUBLISHER="${2:-${SUDO_USER:-azureuser}}"
SITE=/etc/nginx/sites-available/pos-testing-platform

if [[ $EUID -ne 0 ]]; then echo "Run with sudo." >&2; exit 1; fi
if ! [[ "$PORT" =~ ^[0-9]+$ ]]; then echo "Port must be a number, got '$PORT'." >&2; exit 1; fi

# Refuse to fight another program for the port (nginx itself is fine: we add a site to it).
holder="$(ss -ltnpH "sport = :$PORT" 2>/dev/null | grep -o 'users:(("[^"]*' | head -1 | cut -d'"' -f2 || true)"
if [[ -n "$holder" && "$holder" != "nginx" ]]; then
  echo "Port $PORT is already used by '$holder'. Run again with a free port, e.g.: sudo bash setup.sh 8088" >&2
  exit 1
fi

echo "==> Installing nginx"
if ! command -v nginx >/dev/null; then
  apt-get update
  apt-get install -y nginx
fi

echo "==> Creating $DIR (owned by $PUBLISHER, readable by nginx)"
mkdir -p "$DIR"
chown "$PUBLISHER":"$PUBLISHER" "$DIR"
chmod 755 "$DIR"

if [[ "$PORT" == "80" ]] && grep -rls "listen 80 default_server" /etc/nginx/sites-enabled/ 2>/dev/null | grep -qv '/default$'; then
  echo "Another nginx site already owns port 80 (default_server). Use a separate port: sudo bash setup.sh 8088" >&2
  exit 1
fi
if [[ "$PORT" == "80" && -L /etc/nginx/sites-enabled/default ]]; then
  echo "==> Turning off nginx's default welcome page on port 80 (it stays in sites-available)"
  rm /etc/nginx/sites-enabled/default
fi

echo "==> Writing $SITE"
cat > "$SITE" <<EOF
# In-app updates for POS Testing Platform / POS Testing Platform. Written by deploy/update-server/setup.sh.
server {
    listen $PORT default_server;
    listen [::]:$PORT default_server;
    server_name _;
    root /var/www;

    location /pos-testing-platform/ {
        autoindex off;
        # Hide uploads that are still in progress.
        location ~ \.(part|tmp)\$ { return 404; }
        # latest.json changes with every release; never serve a cached copy.
        location = /pos-testing-platform/latest.json { add_header Cache-Control "no-cache"; }
    }

    location / { return 404; }
}
EOF
ln -sf "$SITE" /etc/nginx/sites-enabled/pos-testing-platform
if ! nginx -t; then
  # Leave nginx exactly as it was, so the other sites on this server keep working.
  rm -f /etc/nginx/sites-enabled/pos-testing-platform "$SITE"
  echo "nginx rejected the new site; it has been removed again and nothing else was changed." >&2
  exit 1
fi
systemctl enable nginx >/dev/null
systemctl reload nginx || systemctl restart nginx

# Ubuntu's own firewall, if it is switched on (Azure's network security group is separate).
if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then
  ufw allow "$PORT/tcp"
fi

# Azure's instance metadata service knows the public IP (it stays inside Azure). Not every IP type
# is listed there, so fall back to a placeholder.
IP="$(curl -s --max-time 3 -H Metadata:true \
  'http://169.254.169.254/metadata/instance/network/interface/0/ipv4/ipAddress/0/publicIpAddress?api-version=2021-02-01&format=text' || true)"
[[ "$IP" =~ ^[0-9.]+$ ]] || IP='<vm-ip>'
SUFFIX=""; [[ "$PORT" != "80" ]] && SUFFIX=":$PORT"
cat <<EOF

Done. nginx is serving $DIR on port $PORT.

  1. In Azure: VM -> Networking -> add an inbound rule allowing TCP $PORT (if not already allowed).

  2. In the project's gradle.properties put:
       updateSource=http://$IP$SUFFIX/pos-testing-platform
       publishTo=$PUBLISHER@$IP:$DIR

  3. Publish a release from your PC:
       gradlew :composeApp:publishUpdate

  4. Check it from any machine:
       curl http://$IP$SUFFIX/pos-testing-platform/latest.json
EOF
