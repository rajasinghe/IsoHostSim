# Logcat tab

The Host Simulator has a **Logcat** tab next to **Logs**. It shows the live log the way Android Studio's
Logcat does: one coloured line per entry, a query box with suggestions, and a history of recent queries.

It reads the same in-memory buffer as the Logs tab (the most recent *Max live log entries* lines, 5,000 by
default). The Logs tab remains the place to scroll the complete history kept on disk.

## Columns

`timestamp  pid-tid  tag  package  level  message`

| Column | Where it comes from |
|---|---|
| timestamp | wall-clock time the entry was logged |
| pid-tid | the simulator's process id and the id of the thread that logged (one thread per connection) |
| tag | the class that logged, for example `GatewayClient` or `Iso8583Data`; an explicit source wins |
| package | the gateway's name |
| level | V D I W E A, folded from the simulator's own log types |

Levels: `VERBOSE` = trace, field and bitmap detail; `DEBUG` = socket, protocol and metrics;
`INFO` = connections, transactions, messages and the rest; `WARN` = warnings; `ERROR` = errors and security.

## Query language

```
tag:GatewayClient          substring, case-insensitive
tag=:GatewayClient         exact match
tag~:"Gateway|Iso"         regular expression (Java syntax); quote values containing spaces, | & ( )
-tag:Bitmap                negation, for any key or group
level:WARN                 that level and above (V D I W E A, or the full word)
message:"wrong parsed"     text in the message; a bare word searches message and tag
age:5m                     last 30s / 5m / 2h / 1d
is:crash   is:stacktrace
type:TRANSACTION           the simulator's own log type
thread:34                  thread id or name
package:mine               this gateway (always true here)
line:"..."                 the whole formatted line
```

Terms are ANDed. `|` or `OR` ORs them, AND binds tighter than OR, and parentheses group:

```
(tag:GatewayClient | tag:Iso8583Data) level:INFO -message:Bitmap
```

A `word:` that is not a key (a URL, for instance) is read as plain text. A query that does not parse keeps
the previous result and shows the reason under the box.

## Using it

* **Ctrl+Space** or typing opens suggestions: keys first, then values drawn from the buffer (tags, threads).
  Up/Down move, Enter or Tab accepts, Esc closes.
* Enter commits the query to the history (the clock icon). Recent queries are kept in
  `~/.iso8583studio/logcat_history.txt`.
* Right-click a line: copy it or its message, show only or hide its tag, show its level and above, show only
  its thread.
* Click selects, Shift+click extends, Ctrl+A selects all, Ctrl+C copies the selected lines.
* Toolbar: clear, scroll to the end (follows new lines until you scroll up), soft-wrap, pause and resume,
  export the visible lines to a file.
* **Standard**, **Compact** (`I/Tag: message`) and **Custom** (choose columns) views.
