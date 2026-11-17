package lk.cba.isohostsim.logging

/**
 * Gives a [LogEntry] the identity Android's logcat shows on every line: a tag, a process id, a
 * thread id and a wall-clock time.
 *
 * It runs on the thread that is logging, inside `writeLog`, because the thread and the call stack
 * are only knowable there. The tag is the class that logged, which is what `Log.d(TAG, ...)` is in
 * practice. An explicit [LogEntry.source] always wins over the derived tag.
 *
 * The values go into [LogEntry.metadata], so they also survive the round trip through the on-disk
 * history (which flattens metadata to strings).
 */
object LogStamp {
    const val TAG = "tag"
    const val TS = "ts"
    const val PID = "pid"
    const val TID = "tid"
    const val THREAD = "thread"
    /** Exact logcat level letter (V D I W E A), set for lines read from a device. */
    const val LEVEL = "level"
    /** App the line came from, set for lines read from a device. */
    const val PACKAGE = "package"

    private val pid: Long = ProcessHandle.current().pid()
    private val walker: StackWalker = StackWalker.getInstance()

    /** Frames that are logging plumbing rather than the code that wanted to log. */
    private fun isPlumbing(className: String, method: String): Boolean =
        className.startsWith("lk.cba.isohostsim.logging.") ||
            (method == "writeLog" && className.endsWith("HostSimulator")) ||
            className.startsWith("java.") || className.startsWith("jdk.") ||
            className.startsWith("kotlin.") || className.startsWith("kotlinx.")

    fun stamp(entry: LogEntry) {
        if (entry.metadata.containsKey(TS)) return
        val thread = Thread.currentThread()
        val tag = entry.source?.takeIf { it.isNotBlank() } ?: callerTag()
        entry.metadata = entry.metadata + mapOf(
            TAG to tag,
            TS to System.currentTimeMillis(),
            PID to pid,
            TID to thread.threadId(),
            THREAD to thread.name,
        )
    }

    private fun callerTag(): String = try {
        walker.walk { frames ->
            frames.filter { !isPlumbing(it.className, it.methodName) }.findFirst()
                .map { tagOf(it.className) }
                .orElse("Simulator")
        }
    } catch (_: Exception) {
        "Simulator"
    }

    /** `GatewayClient$processGateway$1` -> `GatewayClient`, `LogTabKt` -> `LogTab`. */
    internal fun tagOf(className: String): String =
        className.substringAfterLast('.').substringBefore('$').removeSuffix("Kt").ifEmpty { className }
}
