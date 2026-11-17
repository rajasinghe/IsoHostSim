package lk.cba.isohostsim.hostprofile

import lk.cba.isohostsim.data.Iso8583Data
import lk.cba.isohostsim.data.model.MessageLengthType
import lk.cba.isohostsim.domain.service.hostSimulatorService.SimulatedResponse
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.HostProfile
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.HostProfileLoader
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.ProfileResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HostProfileTest {

    private val hnb = """
        {
          // comments are allowed
          "name": "HNB IM30",
          "listen": { "address": "10.42.0.1", "port": 8000 },
          "framing": { "lengthHeader": "HEX_HL", "tpdu": "swap" },
          "fields": { "57": { "type": "ANS", "length": "LLLVAR", "max": 999 } },
          "transactions": [
            {
              "name": "SALE",
              "match": { "mti": "0200", "processingCode": "000000" },
              "respond": {
                "echo": ["3", "4", "11", "24", "41", "42"],
                "set": { "12": "[TIME]", "13": "[DATE]", "37": "[RAND]", "38": "123456", "39": "00" }
              }
            },
            {
              "name": "REVERSAL",
              "match": { "mti": "0400" },
              "respond": {
                "echo": ["3", "4", "11", "41", "42"],
                "set": { "39": "00", "57": "REVERSED", "64": "0102030405060708" }
              }
            },
          ],
          "unmatched": { "responseCode": "96" }
        }
    """.trimIndent()

    private fun load(text: String = hnb): HostProfile = assertIs<ProfileResult.Ok>(HostProfileLoader.parse(text)).profile

    /** A request in the IM30's framing: 2-byte binary length, TPDU 6000960000, then the message. */
    private fun request(mti: String, fields: Map<Int, ByteArray>): ByteArray {
        val bitmap = ByteArray(8)
        fields.keys.forEach { b -> bitmap[(b - 1) / 8] = (bitmap[(b - 1) / 8].toInt() or (0x80 shr ((b - 1) % 8))).toByte() }
        val body = hex("6000960000") + hex(mti) + bitmap + fields.toSortedMap().values.fold(ByteArray(0)) { a, b -> a + b }
        return byteArrayOf((body.size shr 8).toByte(), body.size.toByte()) + body
    }

    private val common = mapOf(
        3 to hex("000000"), 4 to hex("000000010000"), 11 to hex("000042"), 22 to hex("0071"),
        24 to hex("0096"), 25 to hex("00"), 41 to "80503875".toByteArray(), 42 to "13506001       ".toByteArray(),
    )

    private fun respond(mti: String, extra: Map<Int, ByteArray> = emptyMap()): Pair<Iso8583Data, ByteArray> {
        val cfg = HostProfileLoader.toGatewayConfig(load(), "test", null)
        val req = Iso8583Data(cfg, true).apply { unpack(request(mti, common + extra)) }
        val bytes = SimulatedResponse(req, cfg, true).pack()!!
        val parsed = Iso8583Data(cfg, true).apply { unpack(bytes) }
        return parsed to bytes
    }

    private fun Iso8583Data.setFields() = bitAttributes.indices.filter { bitAttributes[it].isSet }.map { it + 1 }

    @Test
    fun `sale is answered with the echoed and set fields and a swapped tpdu`() {
        val (rsp, bytes) = respond("0200")
        assertEquals("210", rsp.messageType)
        assertEquals("6000000096", bytes.copyOfRange(2, 7).joinToString("") { "%02X".format(it) })
        assertEquals(bytes.size - 2, ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF), "HEX_HL length")
        assertEquals(listOf(3, 4, 11, 12, 13, 24, 37, 38, 39, 41, 42), rsp.setFields())
        assertEquals("000042", rsp.getValue(10))
        assertEquals("00", rsp.getValue(38))
        assertEquals("123456", rsp.getValue(37))
    }

    @Test
    fun `reversal carries field 57 and a binary 64`() {
        val (rsp, _) = respond("0400")
        assertEquals("410", rsp.messageType)
        assertTrue(57 in rsp.setFields(), "field 57 is sent")
        assertEquals("REVERSED", rsp.getValue(56))
        assertEquals("0102030405060708", rsp.bitAttributes[63].data!!.joinToString("") { "%02X".format(it) })
    }

    @Test
    fun `an unmatched request gets a decline instead of a dropped connection`() {
        val (rsp, _) = respond("0100")
        assertEquals("110", rsp.messageType)
        assertEquals("96", rsp.getValue(38))
        assertEquals("000042", rsp.getValue(10), "STAN echoed")
    }

    @Test
    fun `framing is applied to the gateway`() {
        val cfg = HostProfileLoader.toGatewayConfig(load(), "test", "/tmp/p.json")
        assertEquals(MessageLengthType.HEX_HL, cfg.messageLengthTypeSource)
        assertTrue(cfg.swapResponseTpdu)
        assertEquals("10.42.0.1", cfg.serverAddress)
        assertEquals(8000, cfg.serverPort)
        assertEquals("/tmp/p.json", cfg.profilePath)
    }

    @Test
    fun `problems are reported with where they are`() {
        val bad = hnb
            .replace("\"lengthHeader\": \"HEX_HL\"", "\"lengthHeader\": \"BINARY\"")
            .replace("\"39\": \"00\", \"57\"", "\"39\": \"000\", \"57\"")
            .replace("\"max\": 999", "\"max\": 999, \"length2\": 1")
        assertIs<ProfileResult.Invalid>(HostProfileLoader.parse(bad)) // unknown key

        val errors = assertIs<ProfileResult.Invalid>(HostProfileLoader.parse(
            hnb.replace("\"lengthHeader\": \"HEX_HL\"", "\"lengthHeader\": \"BINARY\"")
                .replace("\"39\": \"00\", \"57\"", "\"39\": \"000\", \"57\"")
                .replace("\"LLLVAR\", \"max\": 999", "\"LLVAR\", \"max\": 999")
                .replace("\"48\"", "\"48\"")
                .replace("\"echo\": [\"3\", \"4\", \"11\", \"41\", \"42\"]", "\"echo\": [\"3\", \"50\"]")
        )).errors
        assertTrue(errors.any { it.startsWith("framing.lengthHeader") }, errors.toString())
        assertTrue(errors.any { "respond.set.39" in it && "3 long" in it }, errors.toString())
        assertTrue(errors.any { it.startsWith("fields.57.max") && "LLLVAR" in it }, errors.toString())
        assertTrue(errors.any { "echo" in it && "field 50 has no format" in it }, errors.toString())
    }

    @Test
    fun `a gateway exports to a profile and loads back the same`() {
        val cfg = HostProfileLoader.toGatewayConfig(load(), "test", null)
        val exported = HostProfileLoader.fromGatewayConfig(cfg)
        val text = HostProfileLoader.json.encodeToString(HostProfile.serializer(), exported)
        val again = load(text)

        assertEquals(load().transactions.map { it.name }, again.transactions.map { it.name })
        assertEquals(load().transactions[1].respond.set, again.transactions[1].respond.set)
        assertEquals(load().transactions[0].respond.echo.sorted(), again.transactions[0].respond.echo.sorted())
        // Same formats in effect; an override equal to the ISO default (57 as ANS LLLVAR 999) is dropped.
        val formats = { p: HostProfile -> HostProfileLoader.templateFor(p).mapValues { (_, b) -> Triple(b.bitType, b.bitLength, b.maxLength) } }
        assertEquals(formats(load()), formats(again))
        assertFalse(text.contains("m_Data"))
    }

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
