package lk.cba.isohostsim.domain.service.hostSimulatorService.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A Host Simulator profile as QA writes it: one readable JSON file per terminal or acquirer.
 *
 * It is the whole configuration of a server gateway: where to listen, how messages are framed,
 * any field formats that differ from the standard template, and the transactions to answer.
 * [HostProfileLoader] checks it and turns it into the engine's GatewayConfig.
 */
@Serializable
data class HostProfile(
    @SerialName("\$schema") val schema: String? = null,
    val name: String,
    val description: String = "",
    val gateway: GatewaySettings = GatewaySettings(),
    val listen: Listen = Listen(),
    /** Where a PROXY gateway forwards requests. */
    val destination: Destination? = null,
    val framing: Framing = Framing(),
    val logging: Logging = Logging(),
    /** Field formats that differ from the standard template, keyed by field number ("57"). */
    val fields: Map<String, FieldSpec> = emptyMap(),
    val transactions: List<ProfileTransaction> = emptyList(),
    val unmatched: Unmatched = Unmatched(),
)

/** What the gateway is and how it handles connections (the old Gateway Type and Transmission tabs). */
@Serializable
data class GatewaySettings(
    /** SERVER answers terminals from [HostProfile.transactions]; PROXY forwards them to [HostProfile.destination]. */
    val type: String = "SERVER",
    /** SYNCHRONOUS: one request at a time per connection. ASYNCHRONOUS: several in flight. */
    val transmission: String = "SYNCHRONOUS",
    val maxConnections: Int = 100,
    /** Seconds to wait for a request (and, for PROXY, for the destination's reply). */
    val timeoutSeconds: Int = 30,
    /** Close a connection after an error instead of carrying on. */
    val terminateOnError: Boolean = false,
)

@Serializable
data class Listen(
    /** TCP, or RS232 for a terminal on a serial cable. */
    val connection: String = "TCP",
    /** Address to listen on; the laptop's hotspot address, or 0.0.0.0 for every interface. */
    val address: String = "0.0.0.0",
    val port: Int = 8000,
    /** RS232 only, e.g. "/dev/ttyUSB0" or "COM3". */
    val serialPort: String = "",
    /** RS232 only. */
    val baudRate: Int = 9600,
)

@Serializable
data class Destination(
    val address: String,
    val port: Int,
    /** Length header towards the destination. */
    val lengthHeader: String = "HEX_HL",
)

/** The old Log Settings tab. Parsed logging is always on: request matching depends on it. */
@Serializable
data class Logging(
    /** Log file; empty means ~/.pos-testing-platform/logs/<name>.log. */
    val file: String = "",
    val maxSizeMB: Int = 10,
    /** Also log each message's raw bytes. */
    val rawData: Boolean = false,
    /** Also log each message as text. */
    val textData: Boolean = false,
    /** ASCII, UTF8, ANSI, Unicode, BigEndianUnicode, UTF32 or UTF7. */
    val encoding: String = "ASCII",
)

@Serializable
data class Framing(
    /** Message length header: HEX_HL (2-byte binary, most terminals), HEX_LH or BCD. */
    val lengthHeader: String = "HEX_HL",
    /**
     * Response TPDU: "swap" (the request's TPDU with destination and origin swapped), "fixed"
     * (always [fixedTpdu]) or "none" (messages carry no TPDU).
     */
    val tpdu: String = "swap",
    /** 10 hex digits, used when [tpdu] is "fixed", for example "6000000096". */
    val fixedTpdu: String? = null,
    /** MTI and LLVAR/LLLVAR lengths in ASCII instead of BCD. */
    val asciiMti: Boolean = false,
)

@Serializable
data class FieldSpec(
    /** BCD, AN, ANS or BINARY. */
    val type: String,
    /** FIXED, LLVAR or LLLVAR. */
    val length: String = "FIXED",
    /** Digits or characters; bytes for BINARY. */
    val max: Int,
)

@Serializable
data class ProfileTransaction(
    val name: String,
    val match: Match,
    val respond: Respond = Respond(),
)

@Serializable
data class Match(
    /** Request MTI, for example "0200". */
    val mti: String,
    /** Request field 3, or "*" for any. */
    val processingCode: String = "*",
)

@Serializable
data class Respond(
    /** Response MTI; defaults to the request MTI + 10 (0200 → 0210). */
    val mti: String? = null,
    /** Fields copied from the request as they are. */
    val echo: List<String> = emptyList(),
    /**
     * Fields to send with a value: a literal ("00"; hex for BINARY) or one placeholder:
     * [SV] [TIME] [DATE] [RAND] [AUTO] [GUID] [SIMRC].
     */
    val set: Map<String, String> = emptyMap(),
    /**
     * A DCC quotation offer, sent in F63 for the request's F4 amount. When F4 is missing or the
     * amount can't be offered, F39 is set to 99 ("DCC not available") instead. See [Dcc].
     */
    val dcc: DccQuote? = null,
)

@Serializable
data class Unmatched(
    /** Field 39 for a request no transaction matches; null closes the connection instead. */
    val responseCode: String? = "96",
)
