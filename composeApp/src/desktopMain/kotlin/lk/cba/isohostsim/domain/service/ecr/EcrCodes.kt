package lk.cba.isohostsim.domain.service.ecr

/**
 * Plain-language meanings for every code an ECR exchange can end with.
 *
 * Three sources: NAKs the CBA ECR library raises itself (11–28, from its `Error Codes.txt`),
 * NAKs the HNB terminal app sends, and the terminal's `rspCode` on a rejected transaction.
 * Code 10 means "terminal busy" from the terminal and "request dataType mismatch" from the
 * library; the app always builds the right dataType, so 10 is read as the terminal's.
 */
object EcrCodes {

    val libraryNak: Map<String, String> = mapOf(
        "11" to "Library: checksum calculation failed",
        "12" to "Library: the reply's dataType did not match the request",
        "13" to "Library: invalid reply from the terminal",
        "14" to "Library: TCP error (terminal unreachable, refused or timed out)",
        "15" to "Library: PAX USB port not found",
        "16" to "Library: could not open the USB port",
        "17" to "Library: USB write error",
        "18" to "Library: no ACK from the terminal",
        "19" to "Library: could not convert the reply to text",
        "20" to "Library: no final response before the timeout",
        "21" to "Library: expected an encrypted reply, got plain text",
        "22" to "Library: expected a plain reply, got an encrypted one",
        "23" to "Library: the key file held no key",
        "24" to "Library: could not decrypt the reply",
        "25" to "Library: key missing or wrong length",
        "26" to "Library: key encryption failed",
        "27" to "Library: could not write the key file",
        "28" to "Library: could not decrypt the key file",
    )

    val terminalNak: Map<String, String> = mapOf(
        "1" to "Terminal: checksum error",
        "2" to "Terminal: command not accepted",
        "3" to "Terminal: no key injected",
        "4" to "Terminal: currency not found",
        "5" to "Terminal: settlement pending (the terminal starts it automatically)",
        "7" to "Terminal: TLE key missing",
        "8" to "Terminal: invalid JSON",
        "9" to "Terminal: QR payments disabled",
        "10" to "Terminal: busy",
        "51" to "Terminal: QR profile not loaded",
        "52" to "Terminal: other error",
        "53" to "Terminal: battery low",
        "PU" to "Terminal: parameter update in progress",
    )

    val responseCode: Map<String, String> = mapOf(
        "00" to "Approved",
        "TO" to "Timed out on the terminal",
        "CN" to "Cancelled on the terminal",
        "E1" to "Void not allowed",
        "E2" to "Duplicate ECR reference number",
        "E4" to "Invalid amount",
        "A1" to "Amount error",
        "C1" to "Connection error", "C2" to "Connection error", "C3" to "Connection error",
        "C4" to "Connection error", "C5" to "Connection error", "C6" to "Connection error",
        "C7" to "Connection error",
        "F1" to "Function not supported",
        "H1" to "Host error", "H2" to "Host error",
        "I1" to "Packet error",
        "L1" to "Batch error",
        "N1" to "Batch or settlement error", "N2" to "Batch or settlement error",
        "N3" to "Batch or settlement error", "N4" to "Batch or settlement error",
        "N5" to "Batch or settlement error", "N6" to "Batch or settlement error",
        "N7" to "Batch or settlement error", "N8" to "Batch or settlement error",
        "S1" to "Send or receive error",
        "S2" to "Settlement error", "S3" to "Settlement error", "S4" to "Settlement error",
        "S5" to "Settlement error",
        "T1" to "Trace error", "T2" to "Trace error", "T3" to "Trace error", "T4" to "Trace error",
        "P1" to "PIN error", "P2" to "PIN error", "P3" to "PIN error", "P4" to "PIN error",
        "P5" to "PIN error",
        "R1" to "Send or receive error",
        "W1" to "Wrong password",
        "U1" to "Unpack error", "U2" to "Unpack error", "U3" to "Unpack error",
        "U4" to "Unpack error", "U5" to "Unpack error", "U6" to "Unpack error",
        "U7" to "Unpack error", "U8" to "Unpack error", "U9" to "Unpack error",
        "PU" to "Parameter update in progress",
        "ER" to "Undefined terminal error",
    )

    fun isLibraryNak(code: String): Boolean = code in libraryNak

    fun describeNak(code: String): String =
        libraryNak[code] ?: terminalNak[code] ?: "Unknown NAK code $code"

    fun describeResponse(code: String?): String =
        code?.let { responseCode[it] } ?: "Unknown response code ${code ?: "(none)"}"
}
