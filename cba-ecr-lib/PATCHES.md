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
