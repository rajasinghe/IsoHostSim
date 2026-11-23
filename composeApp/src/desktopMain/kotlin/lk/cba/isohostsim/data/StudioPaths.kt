package lk.cba.isohostsim.data

import lk.cba.isohostsim.compat.LegacyNames
import java.io.File
import java.nio.file.Files

/**
 * Where the app keeps its files: `~/.pos-testing-platform`.
 *
 * Everything that reads or writes the app's own files goes through [home], which adopts the folder
 * of a copy installed under the app's previous name the first time it is asked (see [adoptLegacyHome]).
 *
 * Saved simulator configs used to live in the process working directory, which for an installed
 * app is wherever the launcher happened to start. [simulatorsFile] moves them here, copying an
 * old file the first time so existing gateways are not lost.
 */
object StudioPaths {

    /** Folder name under the user's home. */
    const val HOME_DIR = ".pos-testing-platform"

    /** Prefix of the saved-simulators file, `<prefix>_simulators.json`. */
    const val SIMULATORS_NAME = "PosTestingPlatform"

    val home: File
        get() {
            val userHome = File(System.getProperty("user.home"))
            val dir = File(userHome, HOME_DIR)
            if (!dir.exists()) adoptLegacyHome(File(userHome, LegacyNames.HOME_DIR), dir)
            return dir
        }

    /** Folder for Host Simulator JSON profiles. */
    val hostsDir: File get() = File(home, "hosts")

    /** Folder for the CBA ECR library's logs and key file (passed as `cba.ecr.dir`). */
    val ecrDir: File get() = File(home, "ecr")

    /**
     * Gives [target] the contents of [legacy], the settings folder of a copy installed under the
     * old name. Does nothing when [target] already exists or there is no [legacy] folder.
     *
     * The folder is renamed when possible. Right after an in-app update on Windows the old folder
     * still holds the running update script, so the rename fails; it is then copied instead and the
     * old folder is left behind. Returns true when [target] now holds the old data.
     */
    fun adoptLegacyHome(legacy: File, target: File): Boolean {
        if (target.exists() || !legacy.isDirectory) return false
        if (runCatching { Files.move(legacy.toPath(), target.toPath()) }.isSuccess) return true
        return runCatching { legacy.copyRecursively(target, overwrite = false) }.getOrDefault(false)
    }

    /**
     * The saved-configs file for [name] in [homeDir]. When it does not exist yet, the newest older
     * copy is brought in: the file under the app's previous name in [homeDir] (renamed), then
     * `<workingDir>/<name>_simulators.json`, then the previous name in [workingDir] (both copied,
     * the original is left in place).
     */
    fun simulatorsFile(
        name: String,
        homeDir: File = home,
        workingDir: File = File(System.getProperty("user.dir")),
    ): File {
        val fileName = "${name}_simulators.json"
        val target = File(homeDir, fileName)
        if (target.exists()) return target
        val legacyFileName = if (name == SIMULATORS_NAME) "${LegacyNames.SIMULATORS_NAME}_simulators.json" else null

        val renamed = legacyFileName?.let { File(homeDir, it) }
        if (renamed != null && renamed.isFile && renamed.renameTo(target)) return target

        val source = listOfNotNull(
            renamed,
            File(workingDir, fileName),
            legacyFileName?.let { File(workingDir, it) },
        ).firstOrNull { it.isFile }
        if (source != null) {
            runCatching {
                homeDir.mkdirs()
                source.copyTo(target)
            }
        }
        return target
    }
}
