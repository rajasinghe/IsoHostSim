package lk.cba.isohostsim.parser

import lk.cba.isohostsim.domain.service.parser.EmvTagComparer
import lk.cba.isohostsim.domain.service.parser.FieldDiff
import kotlin.test.Test
import kotlin.test.assertEquals

//gimhan added
class EmvTagComparerTest {
    @Test
    fun tagsAreComparedOneByOne() {
        // 9F26 cryptogram differs, 95 TVR is the same, 9F36 only in the first, 8A only in the second.
        val a = "9F2608 1111111111111111 9505 0000008000 9F3602 0012"
        val b = "9F2608 2222222222222222 9505 0000008000 8A02 3030"
        val rows = EmvTagComparer.compare(a, b)
        assertEquals(listOf("9F26", "95", "9F36", "8A"), rows.map { it.tag })
        assertEquals(
            listOf(FieldDiff.CHANGED, FieldDiff.SAME, FieldDiff.REQUEST_ONLY, FieldDiff.RESPONSE_ONLY),
            rows.map { it.diff },
        )
        assertEquals("Application cryptogram", rows[0].name)
        assertEquals("1111111111111111", rows[0].request)
        assertEquals("2222222222222222", rows[0].response)
    }

    @Test
    fun repeatedTagsArePairedByOccurrence() {
        val a = "7103 AABBCC 7102 DDEE"
        val b = "7103 AABBCC 7102 FFFF"
        val rows = EmvTagComparer.compare(a, b)
        assertEquals(listOf(FieldDiff.SAME, FieldDiff.CHANGED), rows.map { it.diff })
    }

    @Test
    fun aMissingField55ShowsEveryTagAsOneSided() {
        val rows = EmvTagComparer.compare("9F2701 80", null)
        assertEquals(listOf(FieldDiff.REQUEST_ONLY), rows.map { it.diff })
    }
}
