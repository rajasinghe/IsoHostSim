package lk.cba.isohostsim.hostprofile

import lk.cba.isohostsim.data.BitTemplate
import lk.cba.isohostsim.data.Iso8583Data
import lk.cba.isohostsim.data.model.GatewayConfig
import lk.cba.isohostsim.data.model.MessageLengthType
import kotlin.test.Test
import kotlin.test.assertEquals

/** The ISO8583 Parser reads with a copy of a format, its length-header and TPDU switches applied. */
class ParserFormatTest {

    private val standard = GatewayConfig(id = "p", name = "Standard").apply { bitTemplateSource = BitTemplate.getTemplate_Standard() }

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    // 0200 with fields 3, 11, 41.
    private val message = hex("0200" + "2020000000800000" + "000000" + "000042") + "80503875".toByteArray()

    private fun parse(packet: ByteArray, length: MessageLengthType, tpdu: Boolean): Iso8583Data {
        val cfg = standard.copy(messageLengthTypeSource = length, doNotUseHeaderSource = !tpdu).apply {
            bitTemplateSource = standard.bitTemplateSource
        }
        return Iso8583Data(cfg, true).apply { unpack(packet) }
    }

    @Test
    fun `a terminal packet with a binary length and tpdu parses`() {
        val body = hex("6000960000") + message
        val packet = byteArrayOf(0, body.size.toByte()) + body
        val iso = parse(packet, MessageLengthType.HEX_HL, tpdu = true)
        assertEquals("200", iso.messageType)
        assertEquals("000042", iso.getValue(10))
        assertEquals("80503875", iso.getValue(40))
        assertEquals("2020000000800000", iso.bitmap!!.joinToString("") { "%02X".format(it) }, "the bitmap the analysis panel shows")
    }

    @Test
    fun `a bare message with no length header or tpdu parses`() {
        val iso = parse(message, MessageLengthType.NONE, tpdu = false)
        assertEquals("200", iso.messageType)
        assertEquals("000042", iso.getValue(10))
    }

    @Test
    fun `the switches never change the saved format`() {
        parse(message, MessageLengthType.NONE, tpdu = false)
        assertEquals(MessageLengthType.BCD, standard.messageLengthTypeSource)
        assertEquals(false, standard.doNotUseHeaderSource)
    }
}
