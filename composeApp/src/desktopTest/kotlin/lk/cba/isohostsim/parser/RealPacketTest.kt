package lk.cba.isohostsim.parser

import lk.cba.isohostsim.data.Iso8583Data
import lk.cba.isohostsim.data.model.MessageLengthType
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.HostProfileLoader
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.HostProfileStarter
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.ProfileResult
import lk.cba.isohostsim.domain.service.parser.Framing
import lk.cba.isohostsim.domain.service.parser.IsoPacketDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A settlement request captured from the PAX IM30, read with a starter Host Simulator profile's formats. */
class RealPacketTest {

    private val settlement =
        "0074600096000005002020010000C0001292000000000900963830353033383735313335303630303120202020202020" +
            "000630303030303100603030313030303030303030313030303030303030303030303030303030303030303030303030" +
            "30303030303030303030303030303030303030303030"

    private val bytes = IsoPacketDecoder.parseHex(settlement)

    @Test
    fun `the IM30 settlement request decodes with auto-detect and the parser's standard formats`() {
        val best = IsoPacketDecoder.detect(bytes, IsoPacketDecoder.standardTemplate()).first()
        val p = best.packet
        assertEquals(Framing(MessageLengthType.HEX_HL, true), best.framing)
        assertEquals("0500", p.mti)
        assertEquals("6000960000", p.tpdu)
        assertEquals(listOf(3, 11, 24, 41, 42, 60, 63), p.fields.map { it.number })
        assertEquals("920000", p[3]!!.value)
        assertEquals("80503875", p[41]!!.value)
        assertEquals("000001", p[60]!!.value, "batch number, sent as text")
        assertEquals(60, p[63]!!.length)
        assertNull(p.error)
        assertEquals(0, p.trailingBytes)
        assertTrue(p.warnings.isEmpty(), p.warnings.toString())
    }

    @Test
    fun `a starter profile reads fields 60 and 63 as text, the way the IM30 sends them`() {
        val profile = assertIs<ProfileResult.Ok>(HostProfileLoader.parse(HostProfileStarter.text("IM30"))).profile
        val cfg = HostProfileLoader.toGatewayConfig(profile, "t", null)
        val p = IsoPacketDecoder.detect(bytes, cfg.bitTemplateSource).first().packet
        assertNull(p.error)
        assertEquals("000001", p[60]!!.value)
        assertEquals(60, p[63]!!.length)
    }

    @Test
    fun `the Host Simulator unpacks the IM30 settlement with a starter profile`() {
        val profile = assertIs<ProfileResult.Ok>(HostProfileLoader.parse(HostProfileStarter.text("IM30"))).profile
        val cfg = HostProfileLoader.toGatewayConfig(profile, "t", null)
        val iso = Iso8583Data(cfg, true).apply { unpack(bytes) }
        assertEquals("000001", iso.getValue(59))
        assertEquals("80503875", iso.getValue(40))
        assertEquals(60, iso.bitAttributes[62].length)
    }

    @Test
    fun `fields 57 to 63 are not written out as overrides when exporting a gateway`() {
        val profile = assertIs<ProfileResult.Ok>(HostProfileLoader.parse(HostProfileStarter.text("IM30"))).profile
        val exported = HostProfileLoader.fromGatewayConfig(HostProfileLoader.toGatewayConfig(profile, "t", null))
        assertTrue(exported.fields.keys.none { it.toInt() in 58..63 }, exported.fields.keys.toString())
    }
}
