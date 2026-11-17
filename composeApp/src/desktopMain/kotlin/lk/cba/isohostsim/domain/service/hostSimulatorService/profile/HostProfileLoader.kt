package lk.cba.isohostsim.domain.service.hostSimulatorService.profile

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import lk.cba.isohostsim.data.BitAttribute
import lk.cba.isohostsim.data.BitSpecific
import lk.cba.isohostsim.data.BitTemplate
import lk.cba.isohostsim.data.Iso8583Data
import lk.cba.isohostsim.data.getValue
import lk.cba.isohostsim.data.model.BitLength
import lk.cba.isohostsim.data.model.BitType
import lk.cba.isohostsim.data.model.ConnectionType
import lk.cba.isohostsim.data.model.LoggingOption
import lk.cba.isohostsim.data.model.MessageEncoding
import lk.cba.isohostsim.data.model.TransmissionType
import lk.cba.isohostsim.data.StudioPaths
import lk.cba.isohostsim.data.model.GatewayConfig
import lk.cba.isohostsim.data.model.GatewayType
import lk.cba.isohostsim.data.model.MessageLengthType
import lk.cba.isohostsim.data.updateBit
import lk.cba.isohostsim.domain.service.hostSimulatorService.PlaceholderProcessor
import lk.cba.isohostsim.ui.screens.hostSimulator.Transaction
import java.io.File

/** Outcome of reading a profile: the profile, or every problem found, each naming where it is. */
sealed interface ProfileResult {
    data class Ok(val profile: HostProfile) : ProfileResult
    data class Invalid(val errors: List<String>) : ProfileResult
}

/**
 * Reads, checks and converts Host Simulator profiles.
 *
 * Every check runs before the gateway is touched, so a bad edit never half-applies: QA gets the
 * list of problems and the running gateway keeps its last good profile.
 */
@OptIn(ExperimentalSerializationApi::class)
object HostProfileLoader {

    val json = Json {
        prettyPrint = true
        // A typo such as "respnd" must be reported, not silently ignored.
        ignoreUnknownKeys = false
        allowComments = true
        allowTrailingComma = true
        // Write every setting, so a profile shows the port, framing and defaults it runs with.
        encodeDefaults = true
    }

    private val lengthHeaders = mapOf(
        "HEX_HL" to MessageLengthType.HEX_HL,
        "HEX_LH" to MessageLengthType.HEX_LH,
        "BCD" to MessageLengthType.BCD,
    )

    // ---------- reading and checking ----------

    fun read(file: File): ProfileResult =
        if (!file.isFile) ProfileResult.Invalid(listOf("File not found: ${file.path}"))
        else parse(file.readText())

    fun parse(text: String): ProfileResult {
        val profile = try {
            json.decodeFromString(HostProfile.serializer(), text)
        } catch (e: SerializationException) {
            return ProfileResult.Invalid(listOf("Not a valid profile: ${e.message?.lineSequence()?.first()}"))
        } catch (e: IllegalArgumentException) {
            return ProfileResult.Invalid(listOf("Not a valid profile: ${e.message}"))
        }
        val errors = validate(profile)
        return if (errors.isEmpty()) ProfileResult.Ok(profile) else ProfileResult.Invalid(errors)
    }

    fun validate(p: HostProfile): List<String> {
        val errors = mutableListOf<String>()
        if (p.name.isBlank()) errors += "name: must not be empty"
        when (p.gateway.type.uppercase()) {
            "SERVER", "PROXY" -> {}
            "CLIENT" -> errors += "gateway.type: CLIENT is not supported in a profile; use SERVER, or PROXY with a destination"
            else -> errors += "gateway.type: \"${p.gateway.type}\" must be SERVER or PROXY"
        }
        if (p.gateway.transmission.uppercase() !in setOf("SYNCHRONOUS", "ASYNCHRONOUS")) {
            errors += "gateway.transmission: \"${p.gateway.transmission}\" must be SYNCHRONOUS or ASYNCHRONOUS"
        }
        if (p.gateway.maxConnections < 1) errors += "gateway.maxConnections: must be at least 1"
        if (p.gateway.timeoutSeconds < 1) errors += "gateway.timeoutSeconds: must be at least 1"

        when (p.listen.connection.uppercase()) {
            "TCP" -> if (p.listen.port !in 1..65535) errors += "listen.port: ${p.listen.port} is not a valid port"
            "RS232" -> {
                if (p.listen.serialPort.isBlank()) errors += "listen.serialPort: RS232 needs a port, for example \"/dev/ttyUSB0\" or \"COM3\""
                if (p.listen.baudRate <= 0) errors += "listen.baudRate: must be greater than 0"
            }
            else -> errors += "listen.connection: \"${p.listen.connection}\" must be TCP or RS232"
        }
        if (p.gateway.type.uppercase() == "PROXY") {
            val d = p.destination
            if (d == null) errors += "destination: a PROXY gateway needs a destination address and port"
            else {
                if (d.address.isBlank()) errors += "destination.address: must not be empty"
                if (d.port !in 1..65535) errors += "destination.port: ${d.port} is not a valid port"
                if (d.lengthHeader.uppercase() !in lengthHeaders) errors += "destination.lengthHeader: \"${d.lengthHeader}\" must be one of ${lengthHeaders.keys.joinToString()}"
            }
        }
        if (p.logging.maxSizeMB < 1) errors += "logging.maxSizeMB: must be at least 1"
        if (MessageEncoding.entries.none { it.name.equals(p.logging.encoding, ignoreCase = true) }) {
            errors += "logging.encoding: \"${p.logging.encoding}\" must be one of ${MessageEncoding.entries.joinToString { it.name }}"
        }

        if (p.framing.lengthHeader.uppercase() !in lengthHeaders) {
            errors += "framing.lengthHeader: \"${p.framing.lengthHeader}\" must be one of ${lengthHeaders.keys.joinToString()}"
        }
        when (p.framing.tpdu.lowercase()) {
            "swap", "none" -> {}
            "fixed" -> if (p.framing.fixedTpdu?.matches(Regex("[0-9A-Fa-f]{10}")) != true) {
                errors += "framing.fixedTpdu: \"fixed\" needs 10 hex digits, for example \"6000000096\""
            }
            else -> errors += "framing.tpdu: \"${p.framing.tpdu}\" must be swap, fixed or none"
        }

        p.fields.forEach { (key, spec) ->
            val where = "fields.$key"
            if (fieldNumber(key) == null) errors += "$where: \"$key\" is not a field number from 2 to 128"
            if (spec.type.uppercase() !in setOf("BCD", "AN", "ANS", "BINARY")) {
                errors += "$where.type: \"${spec.type}\" must be BCD, AN, ANS or BINARY"
            }
            val length = spec.length.uppercase()
            if (length !in setOf("FIXED", "LLVAR", "LLLVAR")) {
                errors += "$where.length: \"${spec.length}\" must be FIXED, LLVAR or LLLVAR"
            }
            if (spec.max <= 0) errors += "$where.max: must be greater than 0"
            if (length == "LLVAR" && spec.max > 99) {
                errors += "$where.max: ${spec.max} is too long for LLVAR (its length prefix holds 99 at most); use LLLVAR"
            }
            if (length == "LLLVAR" && spec.max > 999) errors += "$where.max: ${spec.max} is more than LLLVAR's 999"
        }

        val template = templateFor(p)
        val names = mutableSetOf<String>()
        p.transactions.forEachIndexed { i, t ->
            val where = "transactions[$i] (${t.name.ifBlank { "unnamed" }})"
            if (t.name.isBlank()) errors += "transactions[$i].name: must not be empty"
            if (!names.add(t.name)) errors += "$where: another transaction has the same name"
            if (!t.match.mti.matches(Regex("\\d{4}"))) errors += "$where.match.mti: \"${t.match.mti}\" must be 4 digits, for example \"0200\""
            if (t.match.processingCode != "*" && !t.match.processingCode.matches(Regex("\\d{6}"))) {
                errors += "$where.match.processingCode: \"${t.match.processingCode}\" must be 6 digits or \"*\""
            }
            t.respond.mti?.let { if (!it.matches(Regex("\\d{4}"))) errors += "$where.respond.mti: \"$it\" must be 4 digits" }

            t.respond.echo.forEach { key ->
                checkField(key, template, "$where.respond.echo")?.let { errors += it }
                if (key in t.respond.set) errors += "$where.respond: field $key is in both echo and set"
            }
            t.respond.set.forEach { (key, value) ->
                val problem = checkField(key, template, "$where.respond.set")
                if (problem != null) errors += problem
                else checkValue(value, template.getValue(fieldNumber(key)!!))?.let { errors += "$where.respond.set.$key: $it" }
            }
        }

        p.unmatched.responseCode?.let {
            if (it.length != 2) errors += "unmatched.responseCode: \"$it\" must be 2 characters, for example \"96\""
        }
        return errors
    }

    private fun checkField(key: String, template: Map<Int, BitSpecific>, where: String): String? {
        val number = fieldNumber(key) ?: return "$where: \"$key\" is not a field number from 2 to 128"
        val spec = template[number]
        if (spec == null || spec.bitType == BitType.NOT_SPECIFIC) {
            return "$where: field $number has no format; add it under \"fields\" with its type, length and max"
        }
        return null
    }

    private fun checkValue(value: String, spec: BitSpecific): String? {
        if (value in PlaceholderProcessor.holdersList) return null
        val tooLong = { n: Int -> "\"$value\" is $n long, more than the field's ${spec.maxLength}" }
        return when (spec.bitType) {
            BitType.BCD -> when {
                !value.all { it.isDigit() } -> "\"$value\" must be digits for a BCD field, or a placeholder"
                value.length > spec.maxLength -> tooLong(value.length)
                else -> null
            }
            BitType.BINARY -> when {
                !value.matches(Regex("([0-9A-Fa-f]{2})*")) -> "\"$value\" must be hex (an even number of 0-9 A-F) for a BINARY field"
                value.length / 2 > spec.maxLength -> "\"$value\" is ${value.length / 2} bytes, more than the field's ${spec.maxLength}"
                else -> null
            }
            else -> if (value.length > spec.maxLength) tooLong(value.length) else null
        }
    }

    // ---------- converting ----------

    /** Builds a server gateway from a checked profile. [id] keeps an existing gateway's identity. */
    fun toGatewayConfig(p: HostProfile, id: String, path: String?): GatewayConfig =
        GatewayConfig(id = id, name = p.name, gatewayType = GatewayType.SERVER).also { applyTo(it, p, path) }

    /**
     * Writes a checked profile into an existing gateway. Framing, fields and transactions take effect
     * on the next request; a changed address or port needs the gateway restarted.
     */
    fun applyTo(cfg: GatewayConfig, p: HostProfile, path: String?) {
        cfg.name = p.name
        cfg.description = p.description
        cfg.gatewayType = GatewayType.valueOf(p.gateway.type.uppercase())
        cfg.transmissionType = TransmissionType.valueOf(p.gateway.transmission.uppercase())
        cfg.maxConcurrentConnection = p.gateway.maxConnections
        cfg.terminateWhenError = p.gateway.terminateOnError
        cfg.transactionTimeOut = p.gateway.timeoutSeconds
        // The Transmission tab's timeout never reached the sockets; these do.
        cfg.advanceOptions.timeOutFromSource = p.gateway.timeoutSeconds
        cfg.advanceOptions.timeOutFromDest = p.gateway.timeoutSeconds

        cfg.serverConnectionType = if (p.listen.connection.uppercase() == "RS232") ConnectionType.COM else ConnectionType.TCP_IP
        cfg.serverAddress = p.listen.address
        cfg.serverPort = p.listen.port
        cfg.serialPort = p.listen.serialPort
        cfg.baudRate = p.listen.baudRate.toString()
        p.destination?.let { d ->
            cfg.destinationConnectionType = ConnectionType.TCP_IP
            cfg.destinationServer = d.address
            cfg.destinationPort = d.port
            cfg.messageLengthTypeDest = lengthHeaders.getValue(d.lengthHeader.uppercase())
        }

        cfg.logFileName = p.logging.file.ifBlank {
            File(StudioPaths.home, "logs${File.separator}${p.name.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").ifEmpty { "host" }}.log").absolutePath
        }
        File(cfg.logFileName).parentFile?.mkdirs()
        cfg.maxLogSizeInMB = p.logging.maxSizeMB
        cfg.textEncoding = MessageEncoding.entries.first { it.name.equals(p.logging.encoding, ignoreCase = true) }
        cfg.logOptions = LoggingOption.SIMPLE.value or LoggingOption.PARSED_DATA.value or
            (if (p.logging.rawData) LoggingOption.RAW_DATA.value else 0) or
            (if (p.logging.textData) LoggingOption.TEXT_DATA.value else 0)
        cfg.messageLengthTypeSource = lengthHeaders.getValue(p.framing.lengthHeader.uppercase())
        cfg.messageInAsciiSource = p.framing.asciiMti
        when (p.framing.tpdu.lowercase()) {
            "swap" -> { cfg.swapResponseTpdu = true; cfg.doNotUseHeaderSource = false; cfg.fixedResponseHeaderSource = null }
            "fixed" -> { cfg.swapResponseTpdu = false; cfg.doNotUseHeaderSource = false; cfg.fixedResponseHeaderSource = hex(p.framing.fixedTpdu!!) }
            else -> { cfg.swapResponseTpdu = false; cfg.doNotUseHeaderSource = true; cfg.fixedResponseHeaderSource = null }
        }
        cfg.bitTemplateSource = templateFor(p).values.sortedBy { it.bitNumber.toInt() }.toTypedArray()
        cfg.unmatchedResponseCode = p.unmatched.responseCode
        cfg.profilePath = path
        cfg.simulatedTransactionsToSource = p.transactions.mapIndexed { i, t -> toTransaction(cfg, t, i) }
    }

    private fun toTransaction(cfg: GatewayConfig, t: ProfileTransaction, index: Int): Transaction {
        // Fields come typed from the gateway's template, so every value is stored by its format.
        val fields = Iso8583Data(cfg, true).bitAttributes.toMutableList()
        t.respond.echo.forEach { key -> fields[fieldNumber(key)!! - 1].setValue("[SV]") }
        t.respond.set.forEach { (key, value) -> fields[fieldNumber(key)!! - 1].setValue(value) }
        return Transaction(
            id = "PROFILE-${index + 1}",
            // The engine compares MTIs as numbers turned back into text, so "0200" is matched as "200".
            mti = t.match.mti.trimStart('0'),
            proCode = t.match.processingCode,
            description = t.name,
            fields = fields,
            responseMti = t.respond.mti?.trimStart('0'),
        )
    }

    private fun BitAttribute.setValue(value: String) {
        if (typeAtribute == BitType.BINARY && value !in PlaceholderProcessor.holdersList) {
            val bytes = hex(value)
            data = if (lengthAttribute == BitLength.FIXED) ByteArray(maxLength - bytes.size) + bytes else bytes
            length = data!!.size
        } else {
            updateBit(value)
        }
        isSet = true
    }

    /** Writes a gateway out as a profile, so an existing gateway can be moved to a file. */
    fun fromGatewayConfig(cfg: GatewayConfig): HostProfile {
        val template = cfg.bitTemplateSource.associateBy { it.bitNumber.toInt() }
        val standard = BitTemplate.getTemplate_Iso8583().associateBy { it.bitNumber.toInt() }
        val overrides = template.filter { (n, spec) ->
            n > 1 && spec.bitType != BitType.NOT_SPECIFIC && standard[n]?.let { same(it, spec) } != true
        }.toSortedMap().mapKeys { it.key.toString() }.mapValues { (_, s) ->
            FieldSpec(type = s.bitType.name, length = s.bitLength.name, max = s.maxLength)
        }
        val tpdu = when {
            cfg.doNotUseHeaderSource -> "none"
            cfg.swapResponseTpdu -> "swap"
            cfg.fixedResponseHeaderSource?.size == 5 -> "fixed"
            else -> "swap"
        }
        return HostProfile(
            name = cfg.name,
            description = cfg.description,
            gateway = GatewaySettings(
                type = cfg.gatewayType.name.takeIf { it != "CLIENT" } ?: "SERVER",
                transmission = cfg.transmissionType.name,
                maxConnections = cfg.maxConcurrentConnection,
                timeoutSeconds = cfg.transactionTimeOut.coerceAtLeast(1),
                terminateOnError = cfg.terminateWhenError,
            ),
            listen = Listen(
                connection = if (cfg.serverConnectionType == ConnectionType.COM) "RS232" else "TCP",
                address = cfg.serverAddress.ifBlank { "0.0.0.0" },
                port = cfg.serverPort,
                serialPort = cfg.serialPort,
                baudRate = cfg.baudRate.toIntOrNull() ?: 9600,
            ),
            destination = if (cfg.gatewayType == GatewayType.PROXY) Destination(
                address = cfg.destinationServer,
                port = cfg.destinationPort,
                lengthHeader = cfg.messageLengthTypeDest.name.takeIf { it in lengthHeaders } ?: "HEX_HL",
            ) else null,
            logging = Logging(
                file = cfg.logFileName.takeIf { File(it).isAbsolute }.orEmpty(),
                maxSizeMB = cfg.maxLogSizeInMB,
                rawData = cfg.logOptions and LoggingOption.RAW_DATA.value != 0,
                textData = cfg.logOptions and LoggingOption.TEXT_DATA.value != 0,
                encoding = cfg.textEncoding.name,
            ),
            framing = Framing(
                lengthHeader = cfg.messageLengthTypeSource.name.takeIf { it in lengthHeaders } ?: "HEX_HL",
                tpdu = tpdu,
                fixedTpdu = cfg.fixedResponseHeaderSource?.takeIf { tpdu == "fixed" }?.joinToString("") { "%02X".format(it) },
                asciiMti = cfg.messageInAsciiSource,
            ),
            fields = overrides,
            transactions = cfg.simulatedTransactionsToSource.map { t -> fromTransaction(t) },
            unmatched = Unmatched(cfg.unmatchedResponseCode),
        )
    }

    private fun fromTransaction(t: Transaction): ProfileTransaction {
        val echo = mutableListOf<String>()
        val set = linkedMapOf<String, String>()
        t.fields.orEmpty().forEachIndexed { i, f ->
            if (!f.isSet || f.data == null || f.typeAtribute == BitType.NOT_SPECIFIC) return@forEachIndexed
            val text = String(f.data!!, Charsets.ISO_8859_1)
            val value = when {
                text in PlaceholderProcessor.holdersList -> text
                f.typeAtribute == BitType.BINARY -> f.data!!.joinToString("") { "%02X".format(it) }
                else -> f.getValue() ?: return@forEachIndexed
            }
            if (value == "[SV]") echo += (i + 1).toString() else set[(i + 1).toString()] = value
        }
        return ProfileTransaction(
            name = t.description,
            match = Match(t.mti.padStart(4, '0'), t.proCode),
            respond = Respond(mti = t.responseMti?.padStart(4, '0'), echo = echo, set = set),
        )
    }

    fun write(profile: HostProfile, file: File) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(HostProfile.serializer(), profile.copy(schema = profile.schema ?: SCHEMA_NAME)) + "\n")
    }

    // ---------- helpers ----------

    const val SCHEMA_NAME = "host-profile.schema.json"

    /** Writes the JSON schema beside the profiles in [dir], for editor completion. */
    fun writeSchema(dir: File) = HostProfileSchema.writeTo(dir)

    /** The ISO 8583 template ([BitTemplate.getTemplate_Iso8583]) with the profile's field overrides on top. */
    fun templateFor(p: HostProfile): Map<Int, BitSpecific> {
        val map = BitTemplate.getTemplate_Iso8583().associateBy { it.bitNumber.toInt() }.toMutableMap()
        p.fields.forEach { (key, spec) ->
            val n = fieldNumber(key) ?: return@forEach
            val type = runCatching { BitType.valueOf(spec.type.uppercase()) }.getOrNull() ?: return@forEach
            val length = runCatching { BitLength.valueOf(spec.length.uppercase()) }.getOrNull() ?: return@forEach
            map[n] = BitSpecific(n.toByte(), length, type, spec.max)
        }
        return map
    }

    private fun same(a: BitSpecific, b: BitSpecific) =
        a.bitType == b.bitType && a.bitLength == b.bitLength && a.maxLength == b.maxLength

    private fun fieldNumber(key: String): Int? = key.trim().toIntOrNull()?.takeIf { it in 2..128 }

    private fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
