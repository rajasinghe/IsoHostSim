# ISO8583Studio patches to TCPJAVA_CBA_ClassLibrary

Every change is marked in the source with `// ISO8583Studio patch:`
(`grep -rn "ISO8583Studio patch" src/main`). On Windows each one behaves exactly as the
original code. Nothing in the wire protocol was touched.

## 1. USB port matching (Linux device names)

- **New** `Communication/PortMatch.java` (pure, no I/O):
  - `isPaxPort(name, description)`: description contains `USB Virtual Serial Port (PAX)`, **or**
    the name is `ttyACM*` / `ttyUSB*` (with or without `/dev/`).
  - `isUsablePortName(name)`: replaces the old `ComPort.contains("COM")` post-discovery check;
    true for names containing `COM` (original rule) or Linux `ttyACM*`/`ttyUSB*`. Null/empty is false.
- `Communication/PaxPort.java`:
  - `setPreferredPortName(String)` / `getPreferredPortName()`: when set, `discover()` returns it
    without scanning. Null or blank clears it.
  - `discover()`: the original PAX-description loop runs first and is unchanged; only when it finds
    nothing, `PortMatch.pickLinuxPort` picks the lowest-numbered ttyACM*/ttyUSB* port (Linux fallback). A PAX terminal exposes several CDC ports (an A910S shows ttyACM0..2) and only the first answers ECR; jSerialComm lists them newest first, so "first in the list" would pick the wrong one.
- `ComPort.contains("COM")` → `PortMatch.isUsablePortName(ComPort)` in `BinRequest`,
  `CheckStatusRequest`, `DoSaleRequest`, `LastTranDataRequest`, `SettleRequest`,
  `TranCancelRequest`, `TransferKeyRequest`, `VoidRequest` (plus the `PortMatch` import).
- `Communication/USbConnector.java` `getPaxDevicePort()`: uses `PaxPort.discover()` +
  `PortMatch.isUsablePortName()` instead of its own copy of the description loop. One difference:
  with no device the original threw a `NullPointerException` here, now it throws the intended
  `DeviceExceptions` code 15. (See "Known upstream issue" — this method is not reachable today.)

## 1b. Reopen the USB port when the device changes

- `PaxPort.acquire(comPort)`: the original reused the cached port whenever it reported
  `isOpen()`, so after unplugging and plugging in a device that came back under a different
  name (Linux `ttyACM0` → `ttyACM1` while the old handle is held; Windows a new COM number),
  every command kept using the dead handle (NAK 17/18) until restart. Now the cached port is
  reused only if `PortMatch.isSamePort(cached, discovered)`; otherwise the stale handle is
  closed, cancel state is cleared, and the newly discovered port is opened.
- `PortMatch.isSamePort(a, b)`: compares names ignoring `/dev/` and `\\.\` prefixes; an empty
  or null discovered name counts as the same (a failed scan never drops an open port).
- Same name, dead handle (seen on Linux: after a replug every write failed with "port appears to
  have been shutdown or disconnected" until restart): the cached port is now also reused only if
  `port.bytesAvailable() >= 0` (jSerialComm returns -1 for a dead handle) and it has not been
  flagged with the new `PaxPort.markStale()`; otherwise it is closed and reopened.
  ISO8583Studio's `EcrLibraryClient` calls `markStale()` after any USB NAK 17 (write failure) as
  a backstop. Bytes sent to the terminal, timeouts and error codes are unchanged.

## 1c. BIN response returns as soon as it is complete (`BinRequest.java`)

- **New** `JsonFrame.isCompleteJson(String)`: pure check that the text received so far is one
  complete JSON object (braces balanced outside strings, NUL/whitespace padding ignored).
- `RequestBin_TCP`: the original always made one more read with a 1 s timeout after the
  response, so every BIN result came back ~1 s late (measured 1003 ms → 2 ms on localhost).
  That read is now skipped once the response is complete JSON; if it is incomplete, reading
  continues until it is (1 s of silence still ends it). Encrypted (non-JSON) responses get the
  original single 1 s drain.
- `RequestBin_USB` final-response loop: the original polled once a second and always slept 1 s
  after reading, adding 1–2 s. Now it polls every 100 ms, appends chunks, and stops when the
  buffer holds complete JSON, or 1 s after the first data arrived (the original wait, used for
  encrypted/incomplete data). The overall limit is still `intTo` seconds; the A920 `ack`
  confirmation is sent right after the response instead of 1 s later.
- Timeout check changed from `y == intTo` to `!check1`, so a response read on the last poll is
  no longer reported as NAK 20.
- Unchanged: commands sent, ACK handling, the `flushIOBuffers()` after the ACK, error codes.
  The USB path has not been tested against a real terminal. Other request types (sale, void,
  last, etc.) still have their original waits.

## 1d. Recording the frames the terminal sent (`TerminalFrames.java`)

- **New** `TerminalFrames`: while a caller has called `TerminalFrames.begin()` on a thread, every
  frame the library reads from the terminal on that thread is recorded as received (trailing NUL
  padding removed); `TerminalFrames.end()` returns them. Kinds: `ACK` (the first reply, which
  carries `bankName`, `bankCode` and `serialNo`), `FINAL` (the full reply, checksum included) and
  `INVALID` (data the library rejected).
- `Utility.LogWrite` calls `TerminalFrames.fromLog(logMessage)` first. Frames are taken from the
  messages the library already logs right after each read ("Terminal ACK Response :",
  "Terminal Process Response… :", "Terminal Response not valid :", "Terminal Final Response
  Received data not valid… :").
- Why: the library returns only the final reply without its checksum, and for USB Check Status a
  rebuilt `{"dataType":"ACK"}`, so the ACK frame's bank name and serial number never reached the
  caller. ISO8583Studio's ECR simulator shows the recorded frames unchanged.
- Unchanged: return values, bytes sent and read, timeouts, error codes. With no recording started
  it does nothing.

## 2. File paths

- `Utility.java`: the five `"\\"` joins (log folder, daily log file, old-log delete, key file
  write, key file read) now use `File.separator`. Identical on Windows.
- `ProgramDirectoryUtilities.getProgramDirectory()`: returns system property `cba.ecr.dir`
  when set and non-blank; otherwise the original jar-folder / working-directory logic.
  (`Logger/Log.java` already used `File.separator` + `user.dir` and is untouched.)

## 3. Closing the USB port

- `TCPJAVA_CBA_ClassLibrary.closeUsb()` (new, static): calls the existing `PaxPort.shutdown()`.

## 4. USB Check Status (bug fix, approved by the project owner)

- `TCPJAVA_CBA_ClassLibrary.CheckStatus()` (USB) now calls the library's existing
  `CheckStatusRequest.CheckStatus_USB()`, which uses the shared `PaxPort`, instead of
  `Requests.CheckStatus` over `USbConnector`.
- Why: `USbConnector.send()/receive()` call `serialPort.isOpen()` before `serialPort` is ever
  assigned, so the old path always returned `{"dataType":"NAK","error":"17"}` without writing
  anything, on Windows too. The wire format is unchanged: `{"dataType":"active"}` plus `\n`, and the
  reply is the terminal's ACK (or its NAK).
- `USbConnector` and `Requests.CheckStatus` are left in place but are no longer reachable.

## Known upstream issue (superseded by patch 4)

`USbConnector.send()/receive()` call `serialPort.isOpen()` before `serialPort` is ever assigned,
so the USB `CheckStatus()` facade overload (the only user of `USbConnector`) always fails with a
`NullPointerException`, reported as `{"dataType":"NAK","error":"17"}`. This is the same on
Windows, so it was left as-is to keep behaviour identical; the other USB requests use `PaxPort`
and are not affected.
