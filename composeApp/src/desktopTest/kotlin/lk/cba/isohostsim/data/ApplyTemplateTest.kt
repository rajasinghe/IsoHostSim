package lk.cba.isohostsim.data

import lk.cba.isohostsim.data.model.BitLength
import lk.cba.isohostsim.data.model.BitType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplyTemplateTest {

    private val template = arrayOf(
        BitSpecific(bitNumber = 57, bitLength = BitLength.LLLVAR, bitType = BitType.ANS, maxLength = 999),
        BitSpecific(bitNumber = 64, bitLength = BitLength.FIXED, bitType = BitType.BINARY, maxLength = 8),
    )

    @Test
    fun `an untyped field drops its value, which is the bug`() {
        val field = BitAttribute()
        field.updateBit("ABC123")
        assertNull(field.data, "an untyped field stores nothing")
    }

    @Test
    fun `a field typed from the template keeps the value`() {
        val field = BitAttribute()
        field.applyTemplate(template, 57)
        field.updateBit("ABC123")

        assertEquals(BitType.ANS, field.typeAtribute)
        assertEquals(BitLength.LLLVAR, field.lengthAttribute)
        assertEquals(999, field.maxLength)
        assertEquals("ABC123", String(field.data!!))
    }

    @Test
    fun `a field missing from the template is left alone`() {
        val field = BitAttribute()
        field.applyTemplate(template, 48)
        assertEquals(BitType.NOT_SPECIFIC, field.typeAtribute)
        assertTrue(field.maxLength == 0)
    }
}
