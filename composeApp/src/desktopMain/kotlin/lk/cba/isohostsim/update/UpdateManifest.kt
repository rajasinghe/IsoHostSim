package lk.cba.isohostsim.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

//gimhan added — in-app updates from an internal share.

/**
 * `latest.json` on the update share. Each package format carries its own version, so publishing
 * a Windows build does not advertise an update to Linux users before their package is there.
 *
 * ```json
 * {
 *   "msi": { "version": "1.1.1", "file": "ISO8583Studio-1.1.1.msi", "sha256": "…", "notes": "…" },
 *   "rpm": { "version": "1.1.0", "file": "iso8583studio-1.1.0-1.x86_64.rpm", "sha256": "…" }
 * }
 * ```
 * Written by the `publishUpdate` Gradle task; `file` is relative to the folder holding this file.
 */
@Serializable
data class UpdateManifest(
    val msi: UpdatePackage? = null,
    val rpm: UpdatePackage? = null,
    val deb: UpdatePackage? = null,
) {
    fun packageFor(kind: PackageKind): UpdatePackage? = when (kind) {
        PackageKind.MSI -> msi
        PackageKind.RPM -> rpm
        PackageKind.DEB -> deb
    }

    companion object {
        const val FILE_NAME = "latest.json"
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): UpdateManifest = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class UpdatePackage(
    val version: String,
    val file: String,
    val sha256: String,
    val notes: String = "",
)

/** The installer format this installation was made from, and so the one it updates from. */
enum class PackageKind { MSI, RPM, DEB }

object Versions {
    /**
     * Compares dotted numeric versions: `1.10.0` > `1.9.2`, `1.1` == `1.1.0`. A non-numeric part
     * (`1.2.0-beta`) compares by its leading digits, so a pre-release never ranks above its release.
     */
    fun compare(a: String, b: String): Int {
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").split('.').map { p -> p.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}
