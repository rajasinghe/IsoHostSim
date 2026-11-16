package `in`.aicortex.iso8583studio.ui.screens.logcat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import `in`.aicortex.iso8583studio.logging.LogEntry
import java.io.File
import java.util.IdentityHashMap

enum class LogcatFormat(val label: String) {
    STANDARD("Standard view"),
    COMPACT("Compact view"),
    CUSTOM("Custom view"),
}

/**
 * Everything the logcat view remembers between visits to the tab: the query, the view options and the
 * selection. It lives in the screen, not in the tab body, because the tab body leaves composition
 * every time the user switches tabs and a half-typed filter should survive that.
 */
class LogcatState {
    var query by mutableStateOf(TextFieldValue(""))
    var format by mutableStateOf(LogcatFormat.STANDARD)
    var showTimestamp by mutableStateOf(true)
    var showPidTid by mutableStateOf(true)
    var showTag by mutableStateOf(true)
    var showPackage by mutableStateOf(true)
    var showLevel by mutableStateOf(true)
    var softWrap by mutableStateOf(false)
    var autoScroll by mutableStateOf(true)

    var paused by mutableStateOf(false)
        private set
    var frozen: List<LogEntry>? by mutableStateOf(null)
        private set

    var anchor by mutableIntStateOf(-1)
    var focus by mutableIntStateOf(-1)

    /** Last query that parsed, so a half-typed `level:W` keeps showing the previous result. */
    var lastGood: LogcatFilter = LogcatFilter.ALL

    var history by mutableStateOf(LogcatHistory.load())
        private set

    fun togglePause(current: List<LogEntry>) {
        if (paused) { paused = false; frozen = null } else { frozen = current.toList(); paused = true }
    }

    fun clearSelection() { anchor = -1; focus = -1 }

    fun setQuery(text: String) {
        query = TextFieldValue(text, TextRange(text.length))
    }

    /** Context-menu actions append a term unless it is already there. */
    fun addTerm(term: String) {
        val current = query.text
        if (current.split(' ').any { it == term }) return
        setQuery(if (current.isBlank()) term else "${current.trimEnd()} $term")
        commitQuery()
    }

    fun commitQuery() {
        val text = query.text.trim()
        if (text.isNotEmpty()) history = LogcatHistory.add(text)
    }

    fun clearHistory() {
        LogcatHistory.clear()
        history = emptyList()
    }
}

/** Recent queries, newest first, kept across runs in the app's own folder. */
object LogcatHistory {
    private const val MAX = 25
    /** Overridable so tests never write into the real home folder. */
    private val file: File
        get() = System.getProperty("iso8583studio.logcat.history")?.let(::File)
            ?: File(System.getProperty("user.home"), ".iso8583studio/logcat_history.txt")

    fun load(): List<String> = try {
        if (file.exists()) file.readLines().filter { it.isNotBlank() }.take(MAX) else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    fun add(query: String): List<String> {
        val next = (listOf(query) + load().filter { it != query }).take(MAX)
        save(next)
        return next
    }

    fun clear() = save(emptyList())

    private fun save(items: List<String>) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(items.joinToString("\n"))
        } catch (_: Exception) {
            // History is a convenience; failing to persist it must never disturb the view.
        }
    }
}

/**
 * Turns entries into [LogcatRecord]s once each. The live buffer is a window that slides, so records
 * are looked up by entry identity and the map is rebuilt from what is still present on every pass,
 * which also lets entries that scrolled out of the window be collected.
 */
class LogcatRecordCache {
    private var known = IdentityHashMap<LogEntry, LogcatRecord>()

    fun convert(entries: List<LogEntry>, packageName: String): List<LogcatRecord> {
        val next = IdentityHashMap<LogEntry, LogcatRecord>(entries.size * 2 + 1)
        val out = ArrayList<LogcatRecord>(entries.size)
        for (e in entries) {
            val r = known[e]?.takeIf { it.packageName == packageName } ?: e.toLogcatRecord(packageName)
            next[e] = r
            out += r
        }
        known = next
        return out
    }
}
