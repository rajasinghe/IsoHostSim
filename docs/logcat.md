# Logcat

**Logcat** is a tool of its own in the studio. It sits in the **Payment Simulators** group on the home
screen next to the Host and ECR simulators, and in the menus under Simulators → Transaction Processing and
Tools → Device Tools. Each opens in its own tab, so you can run a transaction from the ECR Simulator, watch
the request arrive in the Host Simulator and read the terminal's log here, switching tabs as you go.

It streams the log of a connected Android terminal over adb and shows it the way Android Studio's Logcat
does: one coloured line per entry, a query box with suggestions, and a history of recent queries.

## Connecting a terminal

1. Connect the terminal by USB with USB debugging on, or over Wi-Fi with `adb connect <ip>:5555`.
2. Open Logcat, pick the device from the list (Refresh re-reads `adb devices`) and press **Start**.
3. **Stop** ends the stream. **Clear device log** runs `adb logcat -c` on the terminal and empties the view.

The tool runs `adb -s <serial> logcat -v threadtime`, starting with the device's last 2,000 lines, and keeps
the most recent 20,000. It uses the adb from the Android SDK the studio finds, or `adb` on the PATH. The
stream keeps running while you switch to another tab, and the adb process is stopped when the studio exits.

## Columns

`timestamp  pid-tid  tag  package  level  message`

| Column | Where it comes from |
|---|---|
| timestamp | the device's time for the line |
| pid-tid | the process and thread that logged it |
| tag | the Android log tag |
| package | the app running as that process, read from `ps` on the device every 5 seconds |
| level | V D I W E A (logcat's F, fatal, is shown as A) |

## Query language

```
tag:TcpIso8583Online       substring, case-insensitive
tag=:EmvOnline             exact match
tag~:"Online|ECR"          regular expression (Java syntax); quote values containing spaces, | & ( )
-tag:InputDispatcher       negation, for any key or group
level:WARN                 that level and above (V D I W E A, or the full word)
message:"response tpdu"    text in the message; a bare word searches message and tag
age:5m                     last 30s / 5m / 2h / 1d
is:crash   is:stacktrace
thread:34                  thread id or name
package:com.pax.edc        the app that logged the line
line:"..."                 the whole formatted line
```

Terms are ANDed. `|` or `OR` ORs them, AND binds tighter than OR, and parentheses group:

```
(tag:TcpIso8583Online | tag:EmvOnline) level:INFO -message:greenDAO
```

A `word:` that is not a key (a URL, for instance) is read as plain text. A query that does not parse keeps
the previous result and shows the reason under the box.

## Using it

* **Ctrl+Space** or typing opens suggestions: keys first, then values drawn from the buffer (tags, threads).
  Up/Down move, Enter or Tab accepts, Esc closes.
* Enter commits the query to the history (the clock icon). Recent queries are kept in
  `~/.pos-testing-platform/logcat_history.txt`.
* Right-click a line: copy it or its message, show only or hide its tag, show its level and above, show only
  its thread.
* Click selects, Shift+click extends, Ctrl+A selects all, Ctrl+C copies the selected lines.
* Toolbar: clear, scroll to the end (follows new lines until you scroll up), soft-wrap, pause and resume,
  export the visible lines to a file.
* **Standard**, **Compact** (`I/Tag: message`) and **Custom** (choose columns) views.
