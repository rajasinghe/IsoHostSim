package lk.cba.isohostsim.update

import lk.cba.isohostsim.BuildInfo
import lk.cba.isohostsim.data.StudioPaths
import lk.cba.isohostsim.data.model.AppSettings
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit

//gimhan added — in-app updates from an internal share.

/**
 * Finds, downloads and installs a newer build published to the update share.
 *
 * The share is either a folder (a UNC path such as `\\server\share\ISO8583Studio`, or a mounted
 * folder on Linux) or an http(s) URL. It holds `latest.json` and the installers next to it; the
 * `publishUpdate` Gradle task writes both.
 *
 * Installing hands off to a small script that waits for this process to exit, runs the native
 * installer (MSI per-user upgrade on Windows, dnf/apt through pkexec on Linux) and starts the app
 * again. The installer is what replaces the files, so settings in `~/.iso8583studio` are untouched.
 */
object UpdateService {

    /** Environment override, handy for testing a build against a different share. */
    const val SOURCE_ENV = "ISO8583STUDIO_UPDATE_SOURCE"

    val currentVersion: String get() = BuildInfo.VERSION

    /** Where updates come from: the Settings value, else the environment, else the build default. */
    val source: String
        get() = AppSettings.updateSource.trim().ifEmpty {
            System.getenv(SOURCE_ENV)?.trim().orEmpty().ifEmpty { BuildInfo.UPDATE_SOURCE.trim() }
        }

    private val osName = System.getProperty("os.name").orEmpty().lowercase()
    private val isWindows = osName.contains("win")
    private val isLinux = osName.contains("linux")

    /** The installed launcher, set by the jpackage launcher. Null when run from Gradle or an IDE. */
    val installedLauncher: File?
        get() = System.getProperty("jpackage.app-path")?.let(::File)?.takeIf { it.isFile }

    /** Which installer format this installation came from, or null if it can't update itself. */
    val packageKind: PackageKind? by lazy {
        val launcher = installedLauncher ?: return@lazy null
        when {
            isWindows -> PackageKind.MSI
            isLinux && succeeds("rpm", "-qf", launcher.absolutePath) -> PackageKind.RPM
            isLinux && succeeds("dpkg", "-S", launcher.absolutePath) -> PackageKind.DEB
            else -> null
        }
    }

    sealed interface CheckResult {
        data object NotConfigured : CheckResult
        data object UpToDate : CheckResult
        data class Available(val pkg: UpdatePackage, val kind: PackageKind) : CheckResult
        /** A newer version exists, but this copy can't install it (run from source, or macOS). */
        data class AvailableManualOnly(val version: String) : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    fun check(): CheckResult {
        val src = source
        if (src.isEmpty()) return CheckResult.NotConfigured
        val manifest = try {
            UpdateManifest.parse(openSource(src, UpdateManifest.FILE_NAME).first.use { it.readBytes().decodeToString() })
        } catch (e: Exception) {
            return CheckResult.Failed("Could not read ${UpdateManifest.FILE_NAME} from $src: ${e.message ?: e::class.simpleName}")
        }
        val kind = packageKind
        if (kind == null) {
            // Not an installed copy: report the newest version on offer, but don't try to install.
            val newest = listOfNotNull(manifest.msi, manifest.rpm, manifest.deb)
                .maxWithOrNull { a, b -> Versions.compare(a.version, b.version) }
            return if (newest != null && Versions.isNewer(newest.version, currentVersion)) {
                CheckResult.AvailableManualOnly(newest.version)
            } else CheckResult.UpToDate
        }
        val pkg = manifest.packageFor(kind) ?: return CheckResult.UpToDate
        return if (Versions.isNewer(pkg.version, currentVersion)) CheckResult.Available(pkg, kind) else CheckResult.UpToDate
    }

    /**
     * Copies the installer into `~/.iso8583studio/updates`, checking its SHA-256 against the
     * manifest so a half-copied or corrupted file is never run.
     */
    fun download(pkg: UpdatePackage, onProgress: (copied: Long, total: Long) -> Unit = { _, _ -> }): File {
        val dir = File(StudioPaths.home, "updates").apply { mkdirs() }
        val name = File(pkg.file).name
        val target = File(dir, name)
        val part = File(dir, "$name.part")
        val digest = MessageDigest.getInstance("SHA-256")
        val (stream, total) = openSource(source, pkg.file)
        stream.use { input ->
            part.outputStream().use { out ->
                val buf = ByteArray(1 shl 16)
                var copied = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    digest.update(buf, 0, n)
                    copied += n
                    onProgress(copied, total)
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(pkg.sha256.trim(), ignoreCase = true)) {
            part.delete()
            throw IllegalStateException("Checksum mismatch for $name — the file on the share is incomplete or changed. Try again.")
        }
        target.delete()
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true); part.delete()
        }
        return target
    }

    /**
     * Starts the installer once this process has exited, then relaunches the app. The caller must
     * exit the app straight after this returns, or the installer will wait for it.
     */
    fun installAfterExit(installer: File, kind: PackageKind) {
        val launcher = installedLauncher ?: error("Only an installed copy can update itself.")
        val pid = ProcessHandle.current().pid()
        val dir = installer.parentFile
        val log = File(dir, "install-${installer.nameWithoutExtension}.log")
        when (kind) {
            PackageKind.MSI -> startWindowsInstall(installer, launcher, pid, dir, log)
            PackageKind.RPM, PackageKind.DEB -> startLinuxInstall(installer, kind, launcher, pid, dir, log)
        }
    }

    // ---------------------------------------------------------------- Windows

    private fun startWindowsInstall(msi: File, launcher: File, pid: Long, dir: File, log: File) {
        val installDir = launcher.parentFile.absolutePath.trimEnd('\\')
        val script = File(dir, "install-update.cmd")
        script.writeText(
            listOf(
                "@echo off",
                "rem Written by POS Testing Platform to install an update. Safe to delete.",
                ":wait",
                "tasklist /FI \"PID eq $pid\" /NH | find \"$pid\" >nul",
                "if not errorlevel 1 ( ping -n 2 127.0.0.1 >nul & goto wait )",
                // The launcher process outlives the JVM by a moment.
                "ping -n 3 127.0.0.1 >nul",
                "msiexec /i \"${msi.absolutePath}\" INSTALLDIR=\"$installDir\" /passive /norestart /l*v \"${log.absolutePath}\"",
                "start \"\" \"${launcher.absolutePath}\"",
            ).joinToString("\r\n", postfix = "\r\n"),
        )
        // The jpackage launcher runs the JVM inside a job object that kills every child process when
        // the app exits, so a plain ProcessBuilder child would die before it could install anything.
        // A process created through WMI belongs to no job and survives the exit.
        val cmdLine = "cmd.exe /c \"${script.absolutePath}\"".replace("'", "''")
        val ps = "\$si = New-CimInstance -ClassName Win32_ProcessStartup -ClientOnly -Property @{ ShowWindow = [uint16]0 }; " +
            "\$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = '$cmdLine'; ProcessStartupInformation = \$si }; " +
            "exit [int]\$r.ReturnValue"
        val encoded = Base64.getEncoder().encodeToString(ps.toByteArray(Charsets.UTF_16LE))
        val proc = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
            .redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        if (!proc.waitFor(60, TimeUnit.SECONDS) || proc.exitValue() != 0) {
            proc.destroyForcibly()
            throw IllegalStateException("Could not start the installer (${proc.exitValue()}): ${output.trim()}")
        }
    }

    // ---------------------------------------------------------------- Linux

    private fun startLinuxInstall(pkg: File, kind: PackageKind, launcher: File, pid: Long, dir: File, log: File) {
        val q = { s: String -> "'" + s.replace("'", "'\\''") + "'" }
        val install = when (kind) {
            // pkexec shows the desktop's password prompt; dnf/apt treat a newer local package as an upgrade.
            PackageKind.RPM -> "pkexec dnf install -y ${q(pkg.absolutePath)} || pkexec rpm -U ${q(pkg.absolutePath)}"
            else -> "pkexec apt-get install -y ${q(pkg.absolutePath)}"
        }
        val script = File(dir, "install-update.sh")
        script.writeText(
            """
            |#!/bin/sh
            |# Written by POS Testing Platform to install an update. Safe to delete.
            |while kill -0 $pid 2>/dev/null; do sleep 1; done
            |$install
            |nohup ${q(launcher.absolutePath)} >/dev/null 2>&1 &
            |""".trimMargin(),
        )
        script.setExecutable(true)
        ProcessBuilder("setsid", "-f", "sh", script.absolutePath)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
            .start()
    }

    // ---------------------------------------------------------------- source access

    private fun isUrl(src: String) = src.startsWith("http://", true) || src.startsWith("https://", true)

    private fun resolveUrl(src: String, name: String): URI = URI(src.trimEnd('/') + "/").resolve(name)

    /** Opens [name] under [src]; the size is -1 when the server doesn't say. */
    private fun openSource(src: String, name: String): Pair<InputStream, Long> =
        if (isUrl(src)) {
            val conn = resolveUrl(src, name).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.useCaches = false
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode} for $name")
            conn.inputStream to conn.contentLengthLong
        } else {
            val file = File(src, name)
            file.inputStream() to file.length()
        }

    private fun succeeds(vararg cmd: String): Boolean = runCatching {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        p.inputStream.readBytes()
        p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0
    }.getOrDefault(false)
}
