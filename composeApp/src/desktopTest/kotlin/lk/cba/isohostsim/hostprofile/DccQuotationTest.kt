package lk.cba.isohostsim.hostprofile

import lk.cba.isohostsim.data.Iso8583Data
import lk.cba.isohostsim.domain.service.hostSimulatorService.SimulatedResponse
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.BuiltInHostProfile
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.Dcc
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.DccQuote
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.HostProfile
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.HostProfileLoader
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.ProfileResult
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The built-in Local Host profile answers DCC rate inquiries the way HNB 1.07, SBC and CBC v2 read them. */
class DccQuotationTest {

    /**
     * HNB 1.07's PackDccInquiry for LKR 1,000.00 (fields 2 3 4 11 12 13 14 24 41 42 43 48 49, TPDU
     * 6004350000, F48 "HNBDCC"), without the length header. Same bytes as the remote host's test.
     */
    private val hnbInquiry = "60043500000100703C010000E1800016421689270020230400000000000010000000004210153011252402" +
        "043538303530333837353133353036303031303030303030303134340006484E42444343313434"

    /** The remote (Node) host's reply to [hnbInquiry]; F12 and F13 (time and date) differ per run. */
    private val remoteReply = "6000000435011030380000020000020000000000001000000000421611281125303000754443636863613D5553" +
        "443B6368636E3D3834303B726F65783D37303033333333333B636874763D323030303030303030303333333B636F6D" +
        "6E3D302E3030303B6D726B703D332E3530303B"

    private val cfg get() = BuiltInHostProfile.newConfig("dcc-test")

    private fun framed(hex: String): ByteArray {
        val body = hex(hex)
        return byteArrayOf((body.size shr 8).toByte(), body.size.toByte()) + body
    }

    private fun reply(request: ByteArray): Pair<SimulatedResponse, Iso8583Data> {
        val c = cfg
        val req = Iso8583Data(c, true).apply { unpack(request) }
        val out = SimulatedResponse(req, c, true)
        val parsed = Iso8583Data(c, true).apply { unpack(out.pack()!!) }
        return out to parsed
    }

    private fun Iso8583Data.setFields() = bitAttributes.indices.filter { bitAttributes[it].isSet }.map { it + 1 }

    /** DccData.fromRawString in HNB 1.07: drop 2 leading and 1 trailing characters, split on ";" and "=". */
    private fun parseLikeHnb(raw: String): Map<String, String> =
        raw.substring(2, raw.length - 1).split(';').associate { it.substringBefore('=') to it.substringAfter('=') }

    @Test
    fun `an HNB rate inquiry gets the offer in F63, byte for byte as the remote host sends it`() {
        val (out, rsp) = reply(framed(hnbInquiry))
        assertEquals("DCC QUOTATION", out.ruleName)
        assertEquals("110", rsp.messageType)
        assertEquals(listOf(3, 4, 11, 12, 13, 39, 63), rsp.setFields(), "fields outside the terminal's template make the reply unreadable")
        assertEquals("00", rsp.getValue(38))
        assertEquals("000000100000", rsp.getValue(3))
        assertEquals("000042", rsp.getValue(10))

        val raw = rsp.getValue(62)!!
        assertEquals("DCchca=USD;chcn=840;roex=70033333;chtv=2000000000333;comn=0.000;mrkp=3.500;", raw)
        val offer = parseLikeHnb(raw)
        assertEquals(listOf("chca", "chcn", "roex", "chtv", "comn", "mrkp"), offer.keys.toList(), "CBC v2 reads them by position")
        assertEquals(0.0033333, offer.getValue("roex").drop(1).toDouble() / Math.pow(10.0, offer.getValue("roex").take(1).toDouble()), 1e-12)

        // Same bytes as the remote host, once its F12 and F13 are put in.
        val bytes = out.pack()!!.copyOfRange(2, out.pack()!!.size)
        val remote = hex(remoteReply)
        bytes.copyInto(remote, 27, 27, 32)
        assertEquals(remote.toHex(), bytes.toHex())
    }

    @Test
    fun `the DCC sale that follows is answered as a normal sale with the cardholder amount`() {
        // The inquiry turned into a sale: MTI 0200 and F4 = chtv without its first digit.
        val sale = hnbInquiry.replaceFirst("0100703C", "0200703C").replace("000000100000", "000000000333")
        val (out, rsp) = reply(framed(sale))
        assertEquals("SALE", out.ruleName)
        assertEquals("000000000333", rsp.getValue(3))
        assertFalse(63 in rsp.setFields())
    }

    @Test
    fun `an inquiry without an amount is told DCC is not available`() {
        // Drop F4 from the bitmap (byte 1 bit 4) and its 6 bytes.
        val noAmount = hnbInquiry.replace("0100703C", "0100603C").replace("000000000000100000", "000000")
        val (out, rsp) = reply(framed(noAmount))
        assertEquals("DCC QUOTATION", out.ruleName)
        assertEquals(Dcc.NOT_AVAILABLE, rsp.getValue(38))
        assertFalse(63 in rsp.setFields())
    }

    @Test
    fun `an override of 99 turns DCC off`() {
        val (out, _) = reply(framed(hnbInquiry))
        out.setResponseCode("99")
        val parsed = Iso8583Data(cfg, true).apply { unpack(out.pack()!!) }
        assertEquals("99", parsed.getValue(38))
    }

    @Test
    fun `rates and amounts are converted and encoded`() {
        val usd = DccQuote(currency = "USD", currencyNumeric = "840", rate = "0.0033333")
        assertEquals("70033333", Dcc.encodeRate("0.0033333"))
        assertEquals("50121930", Dcc.encodeRate("1.21930"))
        // LKR 1.50 = USD 0.0049999... rounds to 0.00; LKR 2.00 = 0.0066666 rounds to 0.01.
        assertEquals(BigInteger.ZERO, Dcc.cardholderAmount(BigInteger.valueOf(150), usd))
        assertEquals(BigInteger.ONE, Dcc.cardholderAmount(BigInteger.valueOf(200), usd))
        // A zero-decimal currency (JPY): LKR 1,000.00 at 0.5 = JPY 500.
        assertEquals(BigInteger.valueOf(500), Dcc.cardholderAmount(BigInteger.valueOf(100000), usd.copy(rate = "0.5", exponent = 0)))
        assertNull(Dcc.block("abc", usd))
        assertNull(Dcc.block(null, usd))
        // The longest block that fits (12-digit chtv) is within the F63 size the loader asks for; one more digit is no offer.
        assertEquals(Dcc.MAX_BLOCK_LENGTH, Dcc.block("99999999999", usd.copy(rate = "9.999999"))!!.length)
        assertNull(Dcc.block("999999999999", usd.copy(rate = "9.999999")))
    }

    @Test
    fun `a DCC block the terminals could not read is rejected`() {
        val text = BuiltInHostProfile::class.java.getResource(BuiltInHostProfile.RESOURCE)!!.readText()
        fun errors(t: String) = assertIs<ProfileResult.Invalid>(HostProfileLoader.parse(t)).errors
        assertTrue(errors(text.replace("\"rate\": \"0.0033333\"", "\"rate\": \"0.00333333333\"")).any { "respond.dcc.rate" in it })
        assertTrue(errors(text.replace("\"markup\": \"3.500\"", "\"markup\": \"3.5\"")).any { "respond.dcc.markup" in it })
        assertTrue(errors(text.replace("\"39\": \"00\" },\n        // LKR", "\"39\": \"00\", \"63\": \"X\" },\n        // LKR")).any { "field 63 carries the DCC offer" in it })
        assertTrue(errors(text.replace("\"63\": { \"type\": \"AN\",  \"length\": \"LLLVAR\", \"max\": 999 }", "\"63\": { \"type\": \"AN\",  \"length\": \"LLVAR\", \"max\": 40 }")).any { "field 63 must be" in it })
    }

    @Test
    fun `the offer survives exporting the host to a profile`() {
        val exported = HostProfileLoader.fromGatewayConfig(cfg)
        val again = assertIs<ProfileResult.Ok>(HostProfileLoader.parse(HostProfileLoader.json.encodeToString(HostProfile.serializer(), exported))).profile
        assertEquals(
            BuiltInHostProfile.profile.transactions.single { it.name == "DCC QUOTATION" }.respond.dcc,
            again.transactions.single { it.name == "DCC QUOTATION" }.respond.dcc,
        )
    }

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }
}
