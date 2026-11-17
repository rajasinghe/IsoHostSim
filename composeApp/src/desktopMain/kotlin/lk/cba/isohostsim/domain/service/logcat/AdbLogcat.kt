package lk.cba.isohostsim.domain.service.logcat

import lk.cba.isohostsim.domain.service.posSimulatorService.avd.AndroidSdkLocator
import lk.cba.isohostsim.domain.service.posSimulatorService.avd.ManagedProcess
import lk.cba.isohostsim.domain.service.posSimulatorService.avd.ProcessRunner
import lk.cba.isohostsim.domain.service.posSimulatorService.avd.SdkResolution
import lk.cba.isohostsim.logging.LogEntry
import lk.cba.isohostsim.logging.LogStamp
import lk.cba.isohostsim.logging.LogType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/** A device `adb devices -l` reports. */
data class AdbDevice(
    val serial: String,
    val state: String,
    val model: String = "",
    val product: String = "",
) {
    val isOnline: Boolean get() = state == "device"
    val label: String get() = listOf(model.ifBlank { product }, serial).filter { it.isNotBlank() }.joinToString(" · ")
}

/** One parsed `logcat -v threadtime` line. */
data class LogcatLine(
    val epochMs: Long,
    val pid: Long,
    val tid: Long,
    /** V, D, I, W, E or A (logcat's F is shown as A, as Android Studio does). */
    val level: Char,
    val tag: String,
    val message: String,
)

/**
 * Parsing for adb's output. Pure functions, so they can be tested without a device.
 */
object AdbParsing {

    /** `serial  state  usb:3-6.1 product:IM30 model:IM30 device:IM30 transport_id:8` */
    fun parseDevices(output: List<String>): List<AdbDevice> = output
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("List of devices") && !it.startsWith("*") }
        .mapNotNull { line ->
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) return@mapNotNull null
            val props = parts.drop(2).mapNotNull { p ->
                p.split(':', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
            }.toMap()
            AdbDevice(parts[0], parts[1], props["model"].orEmpty(), props["product"].orEmpty())
        }

    private val threadtime = Regex(
        """^(\d{2})-(\d{2})\s+(\d{2}):(\d{2}):(\d{2})\.(\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEFAS])\s+(.*?)\s*: ?(.*)$"""
    )

    /**
     * Parses `11-04 13:12:35.302 11599 11612 W SEND PACKET_TCPISO8583: 00CF6000…`.
     * Logcat lines carry no year; [year] supplies it. Returns null for banner lines such as
     * `--------- beginning of main`.
     */
    fun parseThreadtime(line: String, year: Int, zone: ZoneId = ZoneId.systemDefault()): LogcatLine? {
        val m = threadtime.matchEntire(line) ?: return null
        val g = m.groupValues
        val time = runCatching {
            LocalDateTime.of(year, g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt() * 1_000_000)
                .atZone(zone).toInstant().toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
        val level = when (val c = g[9][0]) {
            'F' -> 'A'
            'S' -> 'V'
            else -> c
        }
        return LogcatLine(time, g[7].toLong(), g[8].toLong(), level, g[10].trim(), g[11])
    }

    /** `ps -A -o PID,NAME` output, header skipped. */
    fun parseProcesses(output: List<String>): Map<Long, String> = output.mapNotNull { line ->
        val parts = line.trim().split(Regex("\\s+"), limit = 2)
        val pid = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val name = parts.getOrNull(1)?.trim().orEmpty()
        if (name.isEmpty()) null else pid to name
    }.toMap()

    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    /**
     * Turns a device line into the [LogEntry] the Logcat viewer renders. The logcat fields go into
     * the same metadata keys [LogStamp] uses, so the viewer needs no device-specific code.
     */
    fun toLogEntry(line: LogcatLine, packageName: String?, zone: ZoneId = ZoneId.systemDefault()): LogEntry {
        val type = when (line.level) {
            'V' -> LogType.VERBOSE
            'D' -> LogType.DEBUG
            'W' -> LogType.WARNING
            'E', 'A' -> LogType.ERROR
            else -> LogType.INFO
        }
        val meta = buildMap<String, Any> {
            put(LogStamp.TAG, line.tag)
            put(LogStamp.TS, line.epochMs)
            put(LogStamp.PID, line.pid)
            put(LogStamp.TID, line.tid)
            put(LogStamp.LEVEL, line.level.toString())
            if (!packageName.isNullOrBlank()) put(LogStamp.PACKAGE, packageName)
        }
        return LogEntry(
            timestamp = clock.format(java.time.Instant.ofEpochMilli(line.epochMs).atZone(zone)),
            type = type,
            message = line.message,
            source = line.tag,
            metadata = meta,
        )
    }
}

/** Finds adb: the Android SDK the app already locates, else `adb` on the PATH. */
object AdbTool {
    fun path(): String =
        (AndroidSdkLocator.locate() as? SdkResolution.Found)?.sdk?.adb?.toString() ?: "adb"

    suspend fun devices(): List<AdbDevice> {
        val result = ProcessRunner.run(listOf(path(), "devices", "-l"), timeoutMs = 10_000)
        return if (result.ok) AdbParsing.parseDevices(result.lines) else emptyList()
    }
}

enum class LogcatStreamState { STOPPED, RUNNING, ENDED }

/**
 * Streams `adb -s <serial> logcat -v threadtime` from one device into a capped, newest-last list
 * of [LogEntry], refreshing a pid → package map every few seconds so each line shows its app.
 *
 * Lines arrive on a reader thread and are published in batches, so a chatty device does not
 * recompose the view once per line. The adb process is registered with the app's process
 * registry, so it is killed if the app exits while streaming.
 */
class AdbLogcatSession(
    private val maxEntries: Int = 20_000,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val _state = MutableStateFlow(LogcatStreamState.STOPPED)
    val state: StateFlow<LogcatStreamState> = _state.asStateFlow()

    private val _device = MutableStateFlow<AdbDevice?>(null)
    val device: StateFlow<AdbDevice?> = _device.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val pending = ConcurrentLinkedQueue<String>()
    private val packages = ConcurrentHashMap<Long, String>()
    private var process: ManagedProcess? = null
    private var jobs: List<Job> = emptyList()

    /** Starts streaming [device]; any earlier stream is stopped first. */
    fun start(device: AdbDevice, tailLines: Int = 2_000) {
        stop()
        _device.value = device
        _lastError.value = null
        val adb = AdbTool.path()
        val year = LocalDateTime.now().year
        runCatching {
            process = ProcessRunner.start(
                argv = listOf(adb, "-s", device.serial, "logcat", "-v", "threadtime", "-T", tailLines.toString()),
                label = "logcat-${device.serial}",
                onLine = { pending += it },
            )
        }.onFailure {
            _lastError.value = "Could not start adb: ${it.message}"
            _state.value = LogcatStreamState.STOPPED
            return
        }
        _state.value = LogcatStreamState.RUNNING
        jobs = listOf(
            scope.launch { publishLoop(year) },
            scope.launch { packageLoop(adb, device.serial) },
        )
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        process?.stop(graceMs = 1_000)
        process = null
        if (_state.value == LogcatStreamState.RUNNING) _state.value = LogcatStreamState.STOPPED
    }

    /** Clears the view only. */
    fun clearView() {
        _entries.value = emptyList()
    }

    /** Clears the device's own log buffer (`adb logcat -c`) and the view. */
    suspend fun clearDevice() {
        val serial = _device.value?.serial ?: return
        ProcessRunner.run(listOf(AdbTool.path(), "-s", serial, "logcat", "-c"), timeoutMs = 10_000)
        clearView()
    }

    fun close() {
        stop()
    }

    private suspend fun publishLoop(year: Int) {
        while (scope.isActive) {
            if (pending.isNotEmpty()) {
                val batch = ArrayList<LogEntry>()
                while (true) {
                    val raw = pending.poll() ?: break
                    AdbParsing.parseThreadtime(raw, year)?.let { line ->
                        batch += AdbParsing.toLogEntry(line, packages[line.pid])
                    } ?: noteUnparsed(raw)
                }
                if (batch.isNotEmpty()) {
                    val merged = _entries.value + batch
                    _entries.value = if (merged.size > maxEntries) merged.takeLast(maxEntries) else merged
                }
            }
            if (process?.isAlive == false && pending.isEmpty()) {
                _state.value = LogcatStreamState.ENDED
                if (_entries.value.isEmpty() && _lastError.value == null) {
                    _lastError.value = "adb logcat ended. Is the device still connected and authorised?"
                }
                return
            }
            delay(200)
        }
    }

    /** Remembers adb's own error text ("device offline", "no devices") for the status line. */
    private fun noteUnparsed(raw: String) {
        val text = raw.trim()
        if (text.startsWith("---------") || text.isEmpty()) return
        if (text.startsWith("error:", ignoreCase = true) || text.startsWith("adb:")) _lastError.value = text
    }

    private suspend fun packageLoop(adb: String, serial: String) {
        while (scope.isActive) {
            val ps = ProcessRunner.run(listOf(adb, "-s", serial, "shell", "ps", "-A", "-o", "PID,NAME"), timeoutMs = 10_000)
            if (ps.ok) {
                packages.clear()
                packages.putAll(AdbParsing.parseProcesses(ps.lines))
            }
            delay(5_000)
        }
    }
}
