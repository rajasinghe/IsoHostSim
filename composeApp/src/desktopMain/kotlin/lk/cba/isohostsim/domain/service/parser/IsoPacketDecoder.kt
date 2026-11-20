package lk.cba.isohostsim.domain.service.parser

import lk.cba.isohostsim.data.BitSpecific
import lk.cba.isohostsim.data.BitTemplate
import lk.cba.isohostsim.data.model.BitLength
import lk.cba.isohostsim.data.model.BitType
import lk.cba.isohostsim.data.model.MessageLengthType

/** What comes before the message type in a packet. */
data class Framing(val lengthHeader: MessageLengthType, val tpdu: Boolean) {
    val label: String
        get() = (if (lengthHeader == MessageLengthType.NONE) "No length" else "${lengthHeader.name} length") +
            " · " + (if (tpdu) "TPDU" else "no TPDU")

    companion object {
        val LENGTH_HEADERS = listOf(MessageLengthType.HEX_HL, MessageLengthType.HEX_LH, MessageLengthType.BCD, MessageLengthType.NONE)

        /** Every framing the parser can read, in the order auto-detect prefers on a tie. */
        val ALL: List<Framing> = LENGTH_HEADERS.flatMap { h -> listOf(Framing(h, true), Framing(h, false)) }
    }
}

/** One decoded data element. */
data class DecodedField(
    val number: Int,
    val name: String,
    val type: BitType,
    val lengthFormat: BitLength,
    val maxLength: Int,
    /** Digits for BCD, characters for AN/ANS, bytes for BINARY. */
    val length: Int,
    /** The field's bytes as hex, without its length prefix. */
    val rawHex: String,
    /** The value as the Host Simulator reads it: digits, text, or hex for binary. */
    val value: String,
    /** Byte offset of the field (its length prefix, if any) in the pasted packet. */
    val offset: Int,
) {
    val format: String
        get() = "${type.name} " + when (lengthFormat) {
            BitLength.FIXED -> "$maxLength"
            BitLength.LLVAR -> "..$maxLength"
            BitLength.LLLVAR -> "...$maxLength"
        }
}

data class DecodedPacket(
    val framing: Framing,
    /** The length the header states, or null without one. */
    val declaredLength: Int?,
    val tpdu: String?,
    val mti: String,
    val bitmapHex: String,
    val fields: List<DecodedField>,
    /** Bytes left after the last field. */
    val trailingBytes: Int,
    /** Things that look wrong but did not stop the decode. */
    val warnings: List<String>,
    /** Why decoding stopped before the end; [fields] holds what was read up to there. */
    val error: String? = null,
) {
    operator fun get(number: Int): DecodedField? = fields.firstOrNull { it.number == number }
}

class DecodeException(message: String) : Exception(message)

/** A decode with a framing, scored by how well that framing fits the bytes. */
data class FramingCandidate(val framing: Framing, val score: Int, val packet: DecodedPacket)

/**
 * Decodes ISO 8583 packets field by field with a bit template, the way the Host Simulator unpacks
 * them (BCD lengths and MTI, binary bitmap), but tracking every offset and stopping with a clear
 * message instead of reading past the end. It never changes the template.
 */
object IsoPacketDecoder {

    /** Reads hex typed or pasted in any common form: spaces, new lines, `0x`, `:` or `-` separators. */
    fun parseHex(text: String): ByteArray {
        val clean = text.replace(Regex("0[xX]"), "").replace(Regex("[\\s:,\\-]"), "")
        if (clean.isEmpty()) throw DecodeException("Paste a packet as hex.")
        clean.firstOrNull { it !in "0123456789abcdefABCDEF" }?.let {
            throw DecodeException("'$it' is not a hex digit.")
        }
        if (clean.length % 2 != 0) throw DecodeException("The hex has an odd number of digits (${clean.length}); a byte is two digits.")
        return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    fun decode(bytes: ByteArray, framing: Framing, template: Array<BitSpecific>): DecodedPacket {
        val r = Reader(bytes)
        val warnings = mutableListOf<String>()

        val declared: Int? = when (framing.lengthHeader) {
            MessageLengthType.NONE -> null
            MessageLengthType.HEX_HL -> r.take(2, "length header").let { (it[0].u shl 8) or it[1].u }
            MessageLengthType.HEX_LH -> r.take(2, "length header").let { (it[1].u shl 8) or it[0].u }
            MessageLengthType.BCD -> r.take(2, "length header").let {
                bcdDigits(it).toIntOrNull() ?: throw DecodeException("The length header ${hex(it)} is not decimal digits.")
            }
            MessageLengthType.STRING_4 -> r.take(4, "length header").let {
                String(it, Charsets.US_ASCII).toIntOrNull() ?: throw DecodeException("The length header is not 4 ASCII digits.")
            }
        }
        if (declared != null && declared != bytes.size - r.pos) {
            warnings += "The length header says $declared bytes but ${bytes.size - r.pos} follow it."
        }

        val tpdu = if (framing.tpdu) hex(r.take(5, "TPDU")) else null

        val mtiBytes = r.take(2, "message type")
        val mti = bcdDigits(mtiBytes)
        if (!mti.all { it.isDigit() }) throw DecodeException("The message type ${hex(mtiBytes)} is not 4 digits.")

        val primary = r.take(8, "bitmap")
        val secondary = if (primary[0].u and 0x80 != 0) r.take(8, "secondary bitmap") else null
        val bitmap = if (secondary != null) primary + secondary else primary
        val present = (2..bitmap.size * 8).filter { bit -> bitmap[(bit - 1) / 8].u and (0x80 ushr ((bit - 1) % 8)) != 0 }

        val fields = mutableListOf<DecodedField>()
        var error: String? = null
        for (n in present) try {
            val spec = template.firstOrNull { it.bitNumber.toInt() and 0xFF == n }
                ?: throw DecodeException("Field $n is in the bitmap but not in the format, so it can't be read. Pick the Host Simulator whose template has it.")
            if (spec.bitType == BitType.NOT_SPECIFIC) {
                throw DecodeException("Field $n is in the bitmap but the format gives it no type.")
            }
            val offset = r.pos
            val length = when (spec.bitLength) {
                BitLength.FIXED -> spec.maxLength
                BitLength.LLVAR -> r.take(1, "field $n length").let { lengthOf(it, n) }
                BitLength.LLLVAR -> r.take(2, "field $n length").let { lengthOf(it, n) }
            }
            if (spec.bitLength != BitLength.FIXED && spec.maxLength > 0 && length > spec.maxLength) {
                warnings += "Field $n is $length long; the format allows ${spec.maxLength}."
            }
            val size = if (spec.bitType == BitType.BCD) (length + 1) / 2 else length
            val data = r.take(size, "field $n (${fieldName(n)})")
            fields += DecodedField(
                number = n,
                name = fieldName(n),
                type = spec.bitType,
                lengthFormat = spec.bitLength,
                maxLength = spec.maxLength,
                length = length,
                rawHex = hex(data),
                value = valueOf(data, spec.bitType, length),
                offset = offset,
            )
        } catch (e: DecodeException) {
            error = e.message
            break
        }

        return DecodedPacket(
            framing = framing,
            declaredLength = declared,
            tpdu = tpdu,
            mti = mti,
            bitmapHex = hex(bitmap),
            fields = fields,
            trailingBytes = if (error == null) bytes.size - r.pos else 0,
            warnings = warnings + if (error == null && bytes.size > r.pos) listOf("${bytes.size - r.pos} byte(s) left over after the last field.") else emptyList(),
            error = error,
        )
    }

    /**
     * Tries every framing and ranks the ones that decode. The best one has a length header that
     * matches the packet size, a TPDU starting 60, and fields that end exactly at the last byte.
     */
    fun detect(bytes: ByteArray, template: Array<BitSpecific>): List<FramingCandidate> =
        Framing.ALL.mapNotNull { framing ->
            val packet = runCatching { decode(bytes, framing, template) }.getOrNull() ?: return@mapNotNull null
            var score = 0
            score += when {
                packet.error != null -> -5
                packet.trailingBytes == 0 -> 4
                else -> 0
            }
            if (packet.declaredLength != null) {
                score += if (packet.declaredLength == bytes.size - framing.lengthHeader.value) 4 else -6
            }
            if (packet.tpdu != null) score += if (packet.tpdu.startsWith("60")) 2 else -3
            if (packet.mti[0] in "0129" && packet.mti[2] in "0123456789" && packet.mti[1] in "1234567890") score += 1
            if (packet.fields.isNotEmpty()) score += 1
            FramingCandidate(framing, score, packet)
        }.sortedWith(compareByDescending<FramingCandidate> { it.score }.thenByDescending { it.packet.fields.size })
    // The sort is stable, so Framing.ALL's order breaks any remaining tie.

    /** The template the parser's "Standard ISO 8583" format reads with, the one host profiles start from. */
    fun standardTemplate(): Array<BitSpecific> = BitTemplate.getTemplate_Iso8583()

    /** Breaks field 55 (or any BER-TLV data) into tags. Stops quietly at anything malformed. */
    fun tlv(hexData: String): List<Tlv> {
        val b = runCatching { parseHex(hexData) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<Tlv>()
        var i = 0
        while (i < b.size) {
            if (b[i].u == 0x00 || b[i].u == 0xFF) { i++; continue }
            val tagStart = i
            if (b[i++].u and 0x1F == 0x1F) {
                while (i < b.size && b[i].u and 0x80 != 0) i++
                i++
            }
            if (i >= b.size) break
            val tag = hex(b.copyOfRange(tagStart, i))
            var len = b[i++].u
            if (len and 0x80 != 0) {
                val n = len and 0x7F
                if (n == 0 || n > 2 || i + n > b.size) break
                len = 0
                repeat(n) { len = (len shl 8) or b[i++].u }
            }
            if (i + len > b.size) break
            out += Tlv(tag, EMV_TAGS[tag] ?: "", hex(b.copyOfRange(i, i + len)))
            i += len
        }
        return out
    }

    data class Tlv(val tag: String, val name: String, val valueHex: String)

    fun fieldName(n: Int): String = FIELD_NAMES[n] ?: if (n > 64) "Field $n" else "Private field $n"

    private fun lengthOf(b: ByteArray, n: Int): Int =
        bcdDigits(b).toIntOrNull() ?: throw DecodeException("Field $n's length ${hex(b)} is not decimal digits.")

    private fun valueOf(data: ByteArray, type: BitType, length: Int): String = when (type) {
        // An odd number of digits has one pad nibble: a trailing F (Track 2, PAN) or a leading 0.
        BitType.BCD -> bcdDigits(data).let {
            when {
                it.length <= length || length <= 0 -> it
                it.last() == 'F' -> it.take(length)
                else -> it.takeLast(length)
            }
        }
        BitType.AN, BitType.ANS -> data.joinToString("") { b -> if (b.u in 0x20..0x7E) b.u.toChar().toString() else "." }
        else -> hex(data)
    }

    private fun bcdDigits(b: ByteArray) = hex(b)

    fun hex(b: ByteArray): String = b.joinToString("") { "%02X".format(it) }

    private val Byte.u get() = toInt() and 0xFF

    private class Reader(val bytes: ByteArray) {
        var pos = 0
        fun take(n: Int, what: String): ByteArray {
            if (pos + n > bytes.size) {
                throw DecodeException("The packet ends before $what: it needs $n byte(s) at offset $pos but only ${bytes.size - pos} remain.")
            }
            return bytes.copyOfRange(pos, pos + n).also { pos += n }
        }
    }

    private val FIELD_NAMES = mapOf(
        2 to "Primary account number", 3 to "Processing code", 4 to "Amount", 5 to "Amount, settlement",
        6 to "Amount, cardholder billing", 7 to "Transmission date & time", 10 to "Conversion rate",
        11 to "STAN", 12 to "Local time", 13 to "Local date", 14 to "Expiry date", 15 to "Settlement date",
        18 to "Merchant category code", 19 to "Acquirer country code", 22 to "POS entry mode",
        23 to "Card sequence number", 24 to "NII", 25 to "POS condition code", 26 to "PIN capture code",
        32 to "Acquirer ID", 33 to "Forwarding institution ID", 35 to "Track 2", 36 to "Track 3",
        37 to "Retrieval reference number", 38 to "Authorization code", 39 to "Response code",
        41 to "Terminal ID", 42 to "Merchant ID", 43 to "Merchant name/location", 44 to "Additional response data",
        45 to "Track 1", 48 to "Additional data (private)", 49 to "Currency code", 52 to "PIN block",
        53 to "Security control info", 54 to "Additional amounts", 55 to "ICC (EMV) data",
        57 to "Reserved (national)", 60 to "Reserved (batch / original data)", 61 to "Reserved (private)",
        62 to "Invoice number / private", 63 to "Reserved (private)", 64 to "MAC", 70 to "Network management code",
        90 to "Original data elements", 128 to "MAC (secondary)",
    )

    private val EMV_TAGS = mapOf(
        "4F" to "AID", "50" to "Application label", "57" to "Track 2 equivalent", "5A" to "PAN",
        "5F20" to "Cardholder name", "5F24" to "Expiry date", "5F2A" to "Currency code", "5F34" to "PAN sequence number",
        "82" to "AIP", "84" to "DF name", "8A" to "Authorisation response code", "91" to "Issuer authentication data",
        "95" to "TVR", "9A" to "Transaction date", "9B" to "TSI", "9C" to "Transaction type",
        "9F02" to "Amount, authorised", "9F03" to "Amount, other", "9F06" to "AID (terminal)", "9F09" to "App version",
        "9F10" to "Issuer application data", "9F1A" to "Terminal country code", "9F1E" to "IFD serial number",
        "9F26" to "Application cryptogram", "9F27" to "Cryptogram information data", "9F33" to "Terminal capabilities",
        "9F34" to "CVM results", "9F35" to "Terminal type", "9F36" to "ATC", "9F37" to "Unpredictable number",
        "9F41" to "Transaction sequence counter", "9F53" to "Transaction category code", "9F6E" to "Form factor",
        "71" to "Issuer script (71)", "72" to "Issuer script (72)",
    )
}

/** How one field compares between a request and its response. */
enum class FieldDiff(val label: String) { SAME("Same"), CHANGED("Changed"), REQUEST_ONLY("Request only"), RESPONSE_ONLY("Response only") }

data class ComparedField(val number: Int, val name: String, val request: DecodedField?, val response: DecodedField?) {
    val diff: FieldDiff
        get() = when {
            request == null -> FieldDiff.RESPONSE_ONLY
            response == null -> FieldDiff.REQUEST_ONLY
            request.rawHex == response.rawHex && request.length == response.length -> FieldDiff.SAME
            else -> FieldDiff.CHANGED
        }
}

object PacketComparer {
    fun compare(request: DecodedPacket, response: DecodedPacket): List<ComparedField> =
        (request.fields.map { it.number } + response.fields.map { it.number }).distinct().sorted().map { n ->
            ComparedField(n, IsoPacketDecoder.fieldName(n), request[n], response[n])
        }
}

/** How one EMV tag in field 55 compares between the two packets. //gimhan added */
data class ComparedTag(val tag: String, val name: String, val request: String?, val response: String?) {
    val diff: FieldDiff
        get() = when {
            request == null -> FieldDiff.RESPONSE_ONLY
            response == null -> FieldDiff.REQUEST_ONLY
            request == response -> FieldDiff.SAME
            else -> FieldDiff.CHANGED
        }
}

/**
 * Compares field 55 tag by tag. Tags keep the request's order, then the response-only ones in the
 * response's order. A tag that appears more than once (two 71 scripts, say) is paired by occurrence.
 * //gimhan added
 */
object EmvTagComparer {
    fun compare(requestHex: String?, responseHex: String?): List<ComparedTag> {
        fun keyed(hex: String?): List<Pair<String, IsoPacketDecoder.Tlv>> {
            val seen = mutableMapOf<String, Int>()
            return IsoPacketDecoder.tlv(hex.orEmpty()).map { t ->
                val n = seen.merge(t.tag, 1, Int::plus)!!
                "${t.tag}#$n" to t
            }
        }
        val req = keyed(requestHex)
        val res = keyed(responseHex).toMap()
        val reqKeys = req.map { it.first }.toSet()
        return req.map { (key, t) -> ComparedTag(t.tag, t.name, t.valueHex, res[key]?.valueHex) } +
            keyed(responseHex).filter { it.first !in reqKeys }.map { (_, t) -> ComparedTag(t.tag, t.name, null, t.valueHex) }
    }
}
