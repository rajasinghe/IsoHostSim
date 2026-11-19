# Host Simulator profiles (JSON)

A host profile is one JSON file that holds everything a Host Simulator gateway needs: where to listen,
how messages are framed, field formats, and the transactions to answer.

## The built-in profile (developers only)

The app has one profile built in, `composeApp/src/desktopMain/resources/host-profile/all-apps.json`,
and every server host answers from it. Testers can't pick, open or edit it in the app; the Local Host
screen and the host list show it read only, and testers change replies with overrides instead.

To change what the host answers:

1. Edit `composeApp/src/desktopMain/resources/host-profile/all-apps.json`. The schema in
   `host-profile.schema.json` gives VS Code and IntelliJ completion and inline checks.
2. Rebuild the app. When it loads, every server host takes the new profile; the tester's port is kept.

A profile with problems fails the build's tests (`BuiltInHostProfileTest`) and stops the app at
startup with the list of problems, so a broken file never reaches testers.

The port in the file is only the default: testers pick the port on the Local Host screen, and the
host binds to the Wi-Fi hotspot's address.

## Format

```jsonc
{
  "$schema": "host-profile.schema.json",
  "name": "HNB IM30",
  "gateway": {
    "type": "SERVER",                 // SERVER answers terminals; PROXY forwards to "destination"
    "transmission": "SYNCHRONOUS",    // or ASYNCHRONOUS
    "maxConnections": 100,
    "timeoutSeconds": 30,             // wait for a request (and a PROXY destination's reply)
    "terminateOnError": false
  },
  "listen": { "connection": "TCP", "address": "10.42.0.1", "port": 8000 },
  // RS232: { "connection": "RS232", "serialPort": "/dev/ttyUSB0", "baudRate": 9600 }
  // PROXY only: "destination": { "address": "172.19.25.141", "port": 51837, "lengthHeader": "HEX_HL" },
  "logging": {
    "file": "",                       // empty: ~/.iso8583studio/logs/<name>.log
    "maxSizeMB": 10,
    "rawData": false,                 // also log raw bytes
    "textData": false,                // also log messages as text
    "encoding": "ASCII"
  },
  "framing": {
    "lengthHeader": "HEX_HL",                                   // HEX_HL, HEX_LH or BCD
    "tpdu": "swap"                                              // swap, fixed (+ "fixedTpdu") or none
  },
  "fields": {                                                   // only formats that differ from standard
    "57": { "type": "ANS", "length": "LLLVAR", "max": 999 }
  },
  "transactions": [
    {
      "name": "SALE",
      "match": { "mti": "0200", "processingCode": "000000" },   // processingCode "*" matches any
      "respond": {
        "echo": ["3", "4", "11", "24", "41", "42"],             // copied from the request
        "set": { "12": "[TIME]", "13": "[DATE]", "37": "[RAND]", "38": "[RAND]", "39": "00" }
      }
    }
  ],
  "unmatched": { "responseCode": "96" }                         // null: close the connection instead
}
```

* `respond.mti` defaults to the request MTI + 10 (0200 → 0210).
* `set` values are a literal or one placeholder: `[SV]` copy from request, `[TIME]`, `[DATE]` (formatted by
  the field's length), `[RAND]`, `[AUTO]` counter, `[GUID]`, `[SIMRC]` (success/decline per Simulation).
* BINARY values are hex; `max` for BINARY is in bytes (field 64: `"max": 8`, value of 16 hex digits).
* LLVAR holds at most 99; use LLLVAR above that.
* Fields you leave out use the ISO 8583 formats. Fields 57 to 63 default to text (`ANS`, `LLLVAR`, max 999),
  which is how terminals such as the IM30 send them (60 is the batch number, 63 the settlement totals).
* Comments (`//`) and trailing commas are allowed. Unknown keys are reported, so typos do not pass silently.
* Parsed logging is always on: request matching depends on it (the old Log Settings tab could turn it off,
  which made every request fail to match).
* Not in profiles: CLIENT gateways, dial-up and REST connections, and encryption keys.

## Response control (QA)

Above the gateway's tabs, **Response** sets what happens to every response from the next request on:

| Setting | Effect |
|---|---|
| Respond normally | Sent straight away |
| Don't respond | Never sent; the connection stays open so the terminal times out (reversal tests) |
| Delay response | Sent after the seconds you type, which you can change at any time |
