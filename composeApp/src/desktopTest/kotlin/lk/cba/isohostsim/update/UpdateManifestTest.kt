package lk.cba.isohostsim.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

//gimhan added
class UpdateManifestTest {

    @Test fun `versions compare numerically, part by part`() {
        assertTrue(Versions.isNewer("1.1.1", "1.1.0"))
        assertTrue(Versions.isNewer("1.10.0", "1.9.9"))
        assertTrue(Versions.isNewer("2.0", "1.99.99"))
        assertFalse(Versions.isNewer("1.1.0", "1.1.0"))
        assertFalse(Versions.isNewer("1.1", "1.1.0"))
        assertFalse(Versions.isNewer("1.0.9", "1.1.0"))
        assertEquals(0, Versions.compare("v1.2.0", "1.2"))
    }

    @Test fun `each package format has its own entry`() {
        val m = UpdateManifest.parse(
            """
            {
              "msi": { "version": "1.1.1", "file": "POS-Testing-Platform-1.1.1.msi", "sha256": "ab", "notes": "Fixes" },
              "rpm": { "version": "1.1.0", "file": "pos-testing-platform-1.1.0-1.x86_64.rpm", "sha256": "cd" },
              "future": { "ignored": true }
            }
            """.trimIndent(),
        )
        assertEquals("1.1.1", m.packageFor(PackageKind.MSI)?.version)
        assertEquals("Fixes", m.msi?.notes)
        assertEquals("", m.rpm?.notes)
        assertNull(m.packageFor(PackageKind.DEB))
    }
}
