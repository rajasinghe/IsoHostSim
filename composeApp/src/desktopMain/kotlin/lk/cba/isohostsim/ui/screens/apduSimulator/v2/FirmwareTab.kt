package lk.cba.isohostsim.ui.screens.apduSimulator.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import lk.cba.isohostsim.logging.LogEntry
import lk.cba.isohostsim.logging.LogType
import lk.cba.isohostsim.ui.SuccessGreen
import lk.cba.isohostsim.ui.screens.config.apduSimulator.v2.SectionCard
import lk.cba.isohostsim.ui.screens.hostSimulator.LogTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.JFileChooser

/**
 * Firmware tab — build / flash the stm32-card firmware. All persistent state (the log entries,
 * detected paths, current job) is hoisted to the screen so the tab survives switching away and
 * back. The log itself uses the same [LogTab] component the Trace Log tab uses, giving it the
 * shared filter / auto-scroll / stats chrome.
 */
@Composable
fun FirmwareTab(
    log: SnapshotStateList<LogEntry>,
    firmwareDir: MutableState<Path?>,
    pioPath: MutableState<Path?>,
    currentJob: MutableState<Job?>,
    running: MutableState<Boolean>,
) {
    val scope = rememberCoroutineScope()
    val isRunning by running
    val canRun by derivedStateOf { firmwareDir.value != null && pioPath.value != null && !isRunning }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ----- Setup -----
        SectionCard(
            icon = Icons.Default.Memory,
            title = "Firmware setup",
            subtitle = "Build / flash stm32-card to the connected Nucleo over ST-Link.",
        ) {
            PathRow("Firmware dir:", firmwareDir.value, onPick = { pickDir()?.let { firmwareDir.value = it } })
            PathRow("PlatformIO:", pioPath.value, onPick = { pickFile()?.let { pioPath.value = it } })
            if (pioPath.value == null) {
                Text(
                    "Install: pip3 install --user platformio  (or  brew install platformio).",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                )
            }
        }

        // ----- Actions -----
        SectionCard(
            icon = Icons.Default.Build,
            title = "Actions",
            subtitle = "Build compiles only. Flash uploads via ST-Link (Nucleo plugged in).",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = canRun,
                    onClick = {
                        running.value = true
                        currentJob.value = scope.launch {
                            try {
                                runPio(pioPath.value!!, firmwareDir.value!!, listOf("run"), log)
                            } finally {
                                running.value = false
                                currentJob.value = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Build")
                }
                Button(
                    enabled = canRun,
                    onClick = {
                        running.value = true
                        currentJob.value = scope.launch {
                            try {
                                runPio(pioPath.value!!, firmwareDir.value!!, listOf("run", "--target", "upload"), log)
                            } finally {
                                running.value = false
                                currentJob.value = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = SuccessGreen, contentColor = Color.White),
                ) {
                    Icon(Icons.Default.FlashOn, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Build + Flash")
                }
                OutlinedButton(
                    enabled = isRunning,
                    onClick = { currentJob.value?.cancel() },
                ) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stop")
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isRunning) "Running…"
                    else if (log.isNotEmpty()) "Idle  •  ${log.size} log entries"
                    else "Idle",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                )
            }
        }

        // ----- Common log view (same component the Trace Log tab uses) -----
        LogTab(
            label = "Firmware build / flash log",
            onClearClick = { log.clear() },
            connectionCount = log.size,
            concurrentConnections = if (isRunning) 1 else 0,
            bytesIncoming = 0L,
            bytesOutgoing = 0L,
            logEntries = log,
        )
    }
}

@Composable
private fun PathRow(label: String, path: Path?, onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(100.dp), style = MaterialTheme.typography.caption)
        Text(
            path?.toString() ?: "(not found — pick manually)",
            style = MaterialTheme.typography.caption,
            fontFamily = FontFamily.Monospace,
            color = if (path == null) MaterialTheme.colors.error else MaterialTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onPick) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Browse")
        }
    }
}

// ---------------------------------------------------------------------------
// Process runner — appends LogEntry directly into the shared list
// ---------------------------------------------------------------------------

private val timestampFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

private suspend fun runPio(
    pio: Path,
    workDir: Path,
    args: List<String>,
    log: SnapshotStateList<LogEntry>,
) {
    log += entry(
        type = LogType.WARNING,
        message = "Started: ${pio.fileName} ${args.joinToString(" ")}",
        details = "cwd: $workDir",
    )
    val exit = withContext(Dispatchers.IO) {
        runCatching {
            val pb = ProcessBuilder(listOf(pio.toString()) + args)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
            pb.environment().putIfAbsent(
                "PATH",
                // File.pathSeparator: ';' on Windows, ':' elsewhere. //gimhan added
                listOf(
                    System.getenv("PATH") ?: "",
                    "/usr/local/bin",
                    "/opt/homebrew/bin",
                    System.getProperty("user.home") + "/.platformio/penv/bin",
                    System.getProperty("user.home") + "\\.platformio\\penv\\Scripts",
                ).joinToString(File.pathSeparator),
            )
            val proc = pb.start()
            proc.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    log += entry(
                        type = classify(line),
                        message = line,
                    )
                }
            }
            proc.waitFor()
        }.getOrElse { ex ->
            log += entry(LogType.ERROR, "Execution failed: ${ex.message ?: ex::class.simpleName}")
            -1
        }
    }
    log += entry(
        type = if (exit == 0) LogType.AUTHORIZATION else LogType.ERROR,
        message = if (exit == 0) "SUCCESS — process exited 0" else "FAILED — exit code $exit",
    )
}

private fun entry(type: LogType, message: String, details: String? = null) = LogEntry(
    timestamp = LocalDateTime.now().format(timestampFmt),
    type = type,
    message = message,
    details = details,
    source = "PlatformIO",
)

private fun classify(line: String): LogType = when {
    line.contains("error", ignoreCase = true) || line.startsWith("***") -> LogType.ERROR
    line.contains("warning", ignoreCase = true) -> LogType.WARNING
    line.contains("[SUCCESS]") || line.contains("SUCCESS") -> LogType.AUTHORIZATION
    else -> LogType.INFO
}

// ---------------------------------------------------------------------------
// Auto-detection — same as before
// ---------------------------------------------------------------------------

internal fun detectFirmwareDir(): Path? {
    val candidates = listOf(
        "firmware/stm32-card",
        "../firmware/stm32-card",
        "../../firmware/stm32-card",
        "../../../firmware/stm32-card",
    )
    for (rel in candidates) {
        val p = Paths.get(rel).toAbsolutePath().normalize()
        if (Files.exists(p.resolve("platformio.ini"))) return p
    }
    return null
}

internal fun detectPio(): Path? {
    val home = System.getProperty("user.home")
    val candidates = mutableListOf(
        "/usr/local/bin/pio",
        "/opt/homebrew/bin/pio",
        "$home/.platformio/penv/bin/pio",
        "/usr/bin/pio",
        "$home/.local/bin/pio",
        // PlatformIO's Windows installer puts pio.exe under penv\Scripts. //gimhan added
        "$home\\.platformio\\penv\\Scripts\\pio.exe",
    )
    val pyVersionsRoot = Paths.get("$home/Library/Python")
    if (Files.isDirectory(pyVersionsRoot)) {
        runCatching {
            Files.list(pyVersionsRoot).use { stream ->
                stream.forEach { ver -> candidates += "$ver/bin/pio" }
            }
        }
    }
    for (c in candidates) {
        val p = Paths.get(c)
        if (Files.exists(p) && Files.isExecutable(p)) return p
    }
    return runCatching {
        // Windows has no /bin/sh; `where` is its `command -v`. //gimhan added
        val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
        val lookup = if (isWindows) listOf("where", "pio") else listOf("/bin/sh", "-c", "command -v pio")
        val proc = ProcessBuilder(lookup).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText().trim().lineSequence().firstOrNull()?.trim().orEmpty()
        if (proc.waitFor() != 0) return@runCatching null
        if (output.isNotEmpty()) Paths.get(output) else null
    }.getOrNull()
}

private fun pickDir(): Path? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Pick firmware directory (containing platformio.ini)"
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.toPath()
    } else null
}

private fun pickFile(): Path? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.FILES_ONLY
        dialogTitle = "Pick the pio executable"
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.toPath()
    } else null
}

