# cba-ecr-lib

The company's Java ECR class library (`TCPJAVA_CBA_ClassLibrary`), the same library the
Swing ECR demo uses, vendored as source so POS Testing Platform's ECR Simulator can drive a PAX
terminal over **TCP** and **USB** on Linux as well as Windows.

## Origin

- Copied from: `~/Downloads/ECR LATEST CODES (2)/JAVA/USB JAVA DUAL ECR COMMUNICATION/TCPJAVA_CBA_ClassLibrary_DUAL_log/src/TCPJAVA_CBA_ClassLibrary/`
- Copied on: 2026-11-16
- Only the `.java` files were copied (25 files). `*.java.bak`, `comm.jar`, `libcbaecr64.dll`
  and the bundled third-party jars were not taken; jSerialComm and org.json come from Maven.
- Matches the built jar `dist/TCPJAVA_CBA_ClassLibrary.jar` in that project (same class set,
  including `SettleRequest`, `LastSettleRequest`, `TranCancelRequest` and `Communication/PaxPort`;
  jar sha256 `36bba25a4d51cd3e0c8fe423c18f5c956f158ceb201f434c5ff15f9ee0249ba1`).
  It is **not** the older `cbaPaxEcr_V1.06.jar`.
- Package name `TCPJAVA_CBA_ClassLibrary` and folder layout are kept as-is.

## Protocol behaviour

Unchanged. Request names (`sale`, `void`, `settleRequest`, `active`, ...), the SHA-256
checksum, ACK/NAK handling, buffer sizes, timeouts and error codes are exactly as in the
original source. The only edits are the Linux portability patches listed in
[PATCHES.md](PATCHES.md), each marked in the code with `// POS Testing Platform patch:`.

## Using it

Facade: `TCPJAVA_CBA_ClassLibrary.TCPJAVA_CBA_ClassLibrary` (methods return `org.json.JSONObject`).

- TCP: `RequestDoSale(ip, port, json, encrypt, timeoutSec)`, `RequestDoSettlement(ip, port, encrypt)`, ...
- USB: the no-IP overloads. On Linux the port is auto-discovered (`ttyACM*`/`ttyUSB*`), or force it
  with `PaxPort.setPreferredPortName("/dev/ttyACM0")`. Call `TCPJAVA_CBA_ClassLibrary.closeUsb()`
  to release the shared port.
- Logs (`PAX_EDC_ECR_logs/`) and the key file (`PAX_EDC_ECR_Key.log`) go to `-Dcba.ecr.dir=<folder>`
  when set (the folder must exist), otherwise to the jar's folder / working directory as before.

Linux USB needs the user in the `dialout` group (or a udev rule) to open `/dev/ttyACM*`.

## Build / test

```bash
JAVA_HOME=~/.jdks/jbr-21.0.11 ./gradlew :cba-ecr-lib:test
```

Compiled with `--release 17`. Not yet a dependency of `composeApp`.
