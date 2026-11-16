package `in`.aicortex.iso8583studio.data

import java.io.File

/**
 * Where the app keeps its files: `~/.iso8583studio`, the folder AppSettings already uses.
 *
 * Saved simulator configs used to live in the process working directory, which for an installed
 * app is wherever the launcher happened to start. [simulatorsFile] moves them here, copying an
 * old working-directory file the first time so existing gateways are not lost.
 */
object StudioPaths {

    val home: File get() = File(System.getProperty("user.home"), ".iso8583studio")

    /** Folder for the CBA ECR library's logs and key file (passed as `cba.ecr.dir`). */
    val ecrDir: File get() = File(home, "ecr")

    /**
     * The saved-configs file for [name], migrating `<workingDir>/<name>_simulators.json` into
     * [homeDir] when the new file does not exist yet. The old file is left in place.
     */
    fun simulatorsFile(
        name: String,
        homeDir: File = home,
        workingDir: File = File(System.getProperty("user.dir")),
    ): File {
        val fileName = "${name}_simulators.json"
        val target = File(homeDir, fileName)
        if (!target.exists()) {
            val legacy = File(workingDir, fileName)
            if (legacy.isFile) {
                runCatching {
                    homeDir.mkdirs()
                    legacy.copyTo(target)
                }
            }
        }
        return target
    }
}
