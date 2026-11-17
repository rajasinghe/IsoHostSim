package lk.cba.isohostsim.ui.screens.logcat

import lk.cba.isohostsim.logging.LogEntry
import lk.cba.isohostsim.logging.LogStamp
import lk.cba.isohostsim.logging.LogType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Android's six log priorities, in the order Android Studio's Logcat ranks them. The simulator's own
 * [LogType] has 22 kinds; [toLogcatLevel] folds them onto these so `level:` behaves exactly as it
 * does in Android Studio (a minimum, not an exact match).
 */
enum class LogcatLevel(val letter: Char, val label: String) {
    VERBOSE('V', "Verbose"),
    DEBUG('D', "Debug"),
    INFO('I', "Info"),
    WARN('W', "Warn"),
    ERROR('E', "Error"),
    ASSERT('A', "Assert");

    companion object {
        /** Accepts a letter (`w`), a name (`warn`, `warning`) in any case. */
        fun parse(text: String): LogcatLevel? {
            val t = text.trim().lowercase()
            if (t.isEmpty()) return null
            return entries.firstOrNull {
                it.letter.lowercaseChar().toString() == t || it.label.lowercase() == t ||
                    (it == WARN && t == "warning") || (it == ASSERT && t == "fatal")
            }
        }
    }
}

fun LogType.toLogcatLevel(): LogcatLevel = when (this) {
    LogType.VERBOSE, LogType.TRACE, LogType.FIELD, LogType.BITMAP -> LogcatLevel.VERBOSE
    LogType.DEBUG, LogType.PROTOCOL, LogType.SOCKET, LogType.METRICS -> LogcatLevel.DEBUG
    LogType.WARNING -> LogcatLevel.WARN
    LogType.ERROR, LogType.SECURITY -> LogcatLevel.ERROR
    else -> LogcatLevel.INFO
}

private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
private val compactFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")
private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
private val zone: ZoneId = ZoneId.systemDefault()
private val stackFrame = Regex("""\bat [\w.$<>]+\(""")

/**
 * One row of the logcat view: a [LogEntry] plus the fields logcat shows and filters on. Built once
 * per entry, so a keystroke in the query box re-runs a predicate rather than re-deriving anything.
 */
class LogcatRecord(
    val entry: LogEntry,
    val epochMs: Long,
    val pid: Long,
    val tid: Long,
    val threadName: String?,
    val tag: String,
    val level: LogcatLevel,
    val message: String,
    val packageName: String,
) {
    val type: LogType get() = entry.type

    val dateTime: String by lazy { dateTimeFormat.format(Instant.ofEpochMilli(epochMs).atZone(zone)) }
    val compactTime: String by lazy { compactFormat.format(Instant.ofEpochMilli(epochMs).atZone(zone)) }
    val pidTid: String get() = "$pid-$tid"

    /** Message split into display lines; continuation lines align under the first. */
    val lines: List<String> by lazy {
        message.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n').split('\n')
    }

    /** What `line:` matches and what Copy puts on the clipboard. */
    val fullLine: String by lazy { "$dateTime $pidTid $tag $packageName ${level.letter} $message" }

    val isStackTrace: Boolean by lazy {
        lines.any { it.trimStart().startsWith("at ") } || stackFrame.containsMatchIn(message)
    }

    val isCrash: Boolean by lazy {
        level >= LogcatLevel.ERROR &&
            (message.contains("FATAL", ignoreCase = true) || message.contains("Exception") ||
                message.contains("crash", ignoreCase = true))
    }
}

private fun Any?.asLong(): Long? = when (this) {
    is Number -> toLong()
    is String -> toLongOrNull()
    else -> null
}

/**
 * Entries written after [LogStamp] was introduced carry the real tag, process id, thread id and
 * epoch time. Older entries, and ones read back from the history file by a build without it, fall
 * back to what the entry itself knows, so the view never shows a blank column.
 */
fun LogEntry.toLogcatRecord(packageName: String): LogcatRecord {
    val meta = metadata
    val epoch = meta[LogStamp.TS].asLong() ?: epochFromClock(timestamp)
    val text = if (details.isNullOrBlank()) message else "$message\n$details"
    return LogcatRecord(
        entry = this,
        epochMs = epoch,
        pid = meta[LogStamp.PID].asLong() ?: 0L,
        tid = meta[LogStamp.TID].asLong() ?: 0L,
        threadName = meta[LogStamp.THREAD]?.toString(),
        tag = (meta[LogStamp.TAG]?.toString() ?: source)?.takeIf { it.isNotBlank() } ?: type.displayName,
        level = meta[LogStamp.LEVEL]?.toString()?.let(LogcatLevel::parse) ?: type.toLogcatLevel(),
        message = text,
        packageName = meta[LogStamp.PACKAGE]?.toString() ?: packageName,
    )
}

/** `HH:mm:ss.SSS` carries no date; assume today, which is right for anything in the live buffer. */
private fun epochFromClock(clock: String): Long = try {
    LocalDate.now().atTime(LocalTime.parse(clock, clockFormat)).atZone(zone).toInstant().toEpochMilli()
} catch (_: Exception) {
    System.currentTimeMillis()
}
