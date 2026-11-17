package lk.cba.isohostsim.parser

import lk.cba.isohostsim.data.BitSpecific
import lk.cba.isohostsim.data.BitTemplate
import lk.cba.isohostsim.data.model.BitLength
import lk.cba.isohostsim.data.model.BitType
import lk.cba.isohostsim.data.model.MessageLengthType
import lk.cba.isohostsim.domain.service.parser.DecodeException
import lk.cba.isohostsim.domain.service.parser.FieldDiff
import lk.cba.isohostsim.domain.service.parser.Framing
import lk.cba.isohostsim.domain.service.parser.IsoPacketDecoder
import lk.cba.isohostsim.domain.service.parser.PacketComparer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IsoPacketDecoderTest {

    /** The standard template with field 57 as the HNB IM30 profile has it. */
    private val template: Array<BitSpecific> = BitTemplate.getTemplate_Standard().map {
        if (it.bitNumber.toInt() == 57) BitSpecific(57, BitLength.LLLVAR, BitType.ANS, 999) else it
    }.toTypedArray()

    private fun hex(s: String) = IsoPacketDecoder.parseHex(s)

    /** Builds a message (MTI, bitmap, fields) from already-encoded field bytes. */
    private fun message(mti: String, fields: Map<Int, String>): ByteArray {
        val bitmap = ByteArray(8)
        fields.keys.forEach { n -> bitmap[(n - 1) / 8] = (bitmap[(n - 1) / 8].toInt() or (0x80 ushr ((n - 1) % 8))).toByte() }
        return hex(mti) + bitmap + fields.toSortedMap().values.map(::hex).fold(ByteArray(0)) { a, b -> a + b }
    }

    private fun ascii(s: String) = s.toByteArray().joinToString("") { "%02X".format(it) }

    private val saleFields = mapOf(
        3 to "000000",
        4 to "000000001000",
        11 to "000123",
        22 to "0051",
        24 to "0096",
        25 to "00",
        35 to "37" + "4761739001010119D25122011000000000000F",
        41 to ascii("80503875"),
        42 to ascii("000000000012345"),
        55 to "0014" + "9F02060000000010009F360200A1",
        57 to "0005" + ascii("HELLO"),
    )
    private val sale = message("0200", saleFields)

    /** The IM30 sends a two-byte length (high byte first), then TPDU 60 00 96 00 00. */
    private fun framed(body: ByteArray, tpdu: String = "6000960000"): ByteArray {
        val b = hex(tpdu) + body
        return byteArrayOf((b.size shr 8).toByte(), b.size.toByte()) + b
    }

    @Test
    fun `decodes every field of a terminal sale`() {
        val p = IsoPacketDecoder.decode(framed(sale), Framing(MessageLengthType.HEX_HL, true), template)
        assertEquals("0200", p.mti)
        assertEquals("6000960000", p.tpdu)
        assertEquals(listOf(3, 4, 11, 22, 24, 25, 35, 41, 42, 55, 57), p.fields.map { it.number })
        assertEquals("000000001000", p[4]!!.value)
        assertEquals("051", p[22]!!.value, "an odd-length BCD field drops its pad nibble, as the simulator does")
        assertEquals("4761739001010119D25122011000000000000", p[35]!!.value)
        assertEquals("80503875", p[41]!!.value)
        assertEquals(14, p[55]!!.length)
        assertEquals("HELLO", p[57]!!.value)
        assertEquals(0, p.trailingBytes)
        assertTrue(p.warnings.isEmpty(), p.warnings.toString())
    }

    @Test
    fun `auto-detect finds the IM30 framing`() {
        val best = IsoPacketDecoder.detect(framed(sale), template).first()
        assertEquals(Framing(MessageLengthType.HEX_HL, true), best.framing)
    }

    @Test
    fun `auto-detect finds a bare message`() {
        val best = IsoPacketDecoder.detect(sale, template).first()
        assertEquals(Framing(MessageLengthType.NONE, false), best.framing)
        assertNull(best.packet.tpdu)
    }

    @Test
    fun `auto-detect finds a TPDU with no length header`() {
        val best = IsoPacketDecoder.detect(hex("6000960000") + sale, template).first()
        assertEquals(Framing(MessageLengthType.NONE, true), best.framing)
    }

    @Test
    fun `auto-detect finds a BCD length header`() {
        val body = hex("6000000096") + sale
        val len = body.size.toString().padStart(4, '0')
        val best = IsoPacketDecoder.detect(hex(len) + body, template).first()
        assertEquals(Framing(MessageLengthType.BCD, true), best.framing)
    }

    @Test
    fun `hex is accepted with spaces, new lines and 0x`() {
        assertEquals("00CF60", IsoPacketDecoder.hex(IsoPacketDecoder.parseHex("0x00 cf\n60")))
        assertFailsWith<DecodeException> { IsoPacketDecoder.parseHex("00C") }
        assertFailsWith<DecodeException> { IsoPacketDecoder.parseHex("00ZZ") }
    }

    @Test
    fun `a cut-off packet says which field is short`() {
        val cut = sale.copyOfRange(0, sale.size - 3)
        val p = IsoPacketDecoder.decode(cut, Framing(MessageLengthType.NONE, false), template)
        assertTrue("field 57" in p.error!!, p.error)
        assertEquals(10, p.fields.size, "the fields before it are kept")
    }

    @Test
    fun `a wrong length header is a warning, not a failure`() {
        val bad = framed(sale).also { it[1] = (it[1] + 1).toByte() }
        val p = IsoPacketDecoder.decode(bad, Framing(MessageLengthType.HEX_HL, true), template)
        assertTrue(p.warnings.any { "length header" in it })
    }

    @Test
    fun `field 55 breaks into EMV tags`() {
        val p = IsoPacketDecoder.decode(sale, Framing(MessageLengthType.NONE, false), template)
        val tags = IsoPacketDecoder.tlv(p[55]!!.rawHex)
        assertEquals(listOf("9F02", "9F36"), tags.map { it.tag })
        assertEquals("000000001000", tags[0].valueHex)
        assertEquals("Amount, authorised", tags[0].name)
    }

    @Test
    fun `compare marks missing, changed and echoed fields`() {
        val response = message(
            "0210",
            mapOf(3 to "000000", 4 to "000000001000", 11 to "000124", 24 to "0096", 39 to ascii("00"), 41 to ascii("80503875")),
        )
        val req = IsoPacketDecoder.decode(sale, Framing(MessageLengthType.NONE, false), template)
        val res = IsoPacketDecoder.decode(response, Framing(MessageLengthType.NONE, false), template)
        val diff = PacketComparer.compare(req, res).associate { it.number to it.diff }
        assertEquals(FieldDiff.SAME, diff[3])
        assertEquals(FieldDiff.CHANGED, diff[11])
        assertEquals(FieldDiff.RESPONSE_ONLY, diff[39])
        assertEquals(FieldDiff.REQUEST_ONLY, diff[42])
    }
}
