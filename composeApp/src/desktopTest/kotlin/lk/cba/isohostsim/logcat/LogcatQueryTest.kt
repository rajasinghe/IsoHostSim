package lk.cba.isohostsim.logcat

import lk.cba.isohostsim.logging.LogEntry
import lk.cba.isohostsim.logging.LogStamp
import lk.cba.isohostsim.logging.LogType
import lk.cba.isohostsim.ui.screens.logcat.LogcatFilter
import lk.cba.isohostsim.ui.screens.logcat.LogcatLevel
import lk.cba.isohostsim.ui.screens.logcat.LogcatParse
import lk.cba.isohostsim.ui.screens.logcat.LogcatQuery
import lk.cba.isohostsim.ui.screens.logcat.LogcatRecord
import lk.cba.isohostsim.ui.screens.logcat.toLogcatRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LogcatQueryTest {
    private val now = 1_800_000_000_000L

    private fun rec(
        tag: String,
        type: LogType,
        message: String,
        ageMs: Long = 0,
        tid: Long = 11,
        details: String? = null,
    ): LogcatRecord = LogEntry(
        timestamp = "10:00:00.000",
        type = type,
        message = message,
        details = details,
        metadata = mapOf(LogStamp.TAG to tag, LogStamp.TS to now - ageMs, LogStamp.PID to 4242L, LogStamp.TID to tid, LogStamp.THREAD to "conn-$tid"),
    ).toLogcatRecord("host-sim")

    private fun filter(q: String): LogcatFilter {
        val r = LogcatQuery.parse(q)
        assertIs<LogcatParse.Ok>(r, "query should parse: $q -> $r")
        return r.filter
    }

    private fun LogcatFilter.ok(r: LogcatRecord) = matches(r, now)

    private val sale = rec("GatewayClient", LogType.TRANSACTION, "Authorization request received")
    private val parse = rec("Iso8583Data", LogType.MESSAGE, "Message Type = 200")
    private val bad = rec("GatewayClient", LogType.ERROR, "NOT ACCEPT WRONG PARSED DATA")
    private val bits = rec("Iso8583Data", LogType.BITMAP, "Bitmap: 7024058020C00204")
    private val warn = rec("HostSimulator", LogType.WARNING, "Connection timeout")

    @Test fun `blank query matches everything`() {
        val f = filter("   ")
        assertTrue(f.isEmpty)
        assertTrue(f.ok(sale) && f.ok(bad))
    }

    @Test fun `tag is a case insensitive substring`() {
        val f = filter("tag:gateway")
        assertTrue(f.ok(sale) && f.ok(bad))
        assertFalse(f.ok(parse))
    }

    @Test fun `tag equals is exact`() {
        val f = filter("tag=:Iso8583Data")
        assertTrue(f.ok(parse))
        assertFalse(f.ok(rec("Iso8583DataX", LogType.INFO, "x")))
    }

    @Test fun `tag regex`() {
        val f = filter("tag~:\"^(Gateway|Host)\"")
        assertTrue(f.ok(sale) && f.ok(warn))
        assertFalse(f.ok(parse))
    }

    @Test fun `unquoted regex may contain a bar`() {
        val f = filter("tag~:Gateway|Host")
        assertTrue(f.ok(sale) && f.ok(warn))
        assertFalse(f.ok(parse))
    }

    @Test fun `negation hides a tag`() {
        val f = filter("-tag:Iso8583Data")
        assertTrue(f.ok(sale) && f.ok(warn))
        assertFalse(f.ok(parse) || f.ok(bits))
    }

    @Test fun `level is a minimum`() {
        val f = filter("level:WARN")
        assertTrue(f.ok(warn) && f.ok(bad))
        assertFalse(f.ok(sale) || f.ok(bits))
        assertTrue(filter("level:w").ok(warn))
        assertEquals(LogcatLevel.ERROR, bad.level)
        assertEquals(LogcatLevel.VERBOSE, bits.level)
    }

    @Test fun `terms are anded and bars are ors`() {
        val and = filter("tag:Gateway level:ERROR")
        assertTrue(and.ok(bad))
        assertFalse(and.ok(sale))

        val or = filter("tag:HostSimulator | level:ERROR")
        assertTrue(or.ok(warn) && or.ok(bad))
        assertFalse(or.ok(sale))
        assertTrue(filter("tag:HostSimulator OR level:ERROR").ok(bad))
    }

    @Test fun `and binds tighter than or and parentheses regroup`() {
        // a OR b AND c  ==  a OR (b AND c)
        val f = filter("tag:HostSimulator OR tag:Gateway level:ERROR")
        assertTrue(f.ok(warn))
        assertTrue(f.ok(bad))
        assertFalse(f.ok(sale))

        val g = filter("(tag:HostSimulator OR tag:Gateway) level:ERROR")
        assertFalse(g.ok(warn))
        assertTrue(g.ok(bad))
    }

    @Test fun `negated group`() {
        val f = filter("-(tag:Iso8583Data OR tag:HostSimulator)")
        assertTrue(f.ok(sale) && f.ok(bad))
        assertFalse(f.ok(parse) || f.ok(warn))
    }

    @Test fun `bare word matches message or tag`() {
        val f = filter("timeout")
        assertTrue(f.ok(warn))
        assertFalse(f.ok(sale))
        assertTrue(filter("hostsim").ok(warn)) // via the tag
    }

    @Test fun `quoted phrase keeps its spaces`() {
        val f = filter("message:\"wrong parsed\"")
        assertTrue(f.ok(bad))
        assertFalse(f.ok(sale))
    }

    @Test fun `age keeps only recent lines`() {
        val old = rec("A", LogType.INFO, "old", ageMs = 10 * 60_000)
        val fresh = rec("A", LogType.INFO, "fresh", ageMs = 30_000)
        val f = filter("age:5m")
        assertTrue(f.ok(fresh))
        assertFalse(f.ok(old))
        assertTrue(filter("age:1h").ok(old))
    }

    @Test fun `is crash and stacktrace`() {
        val crash = rec("A", LogType.ERROR, "FATAL EXCEPTION: main")
        val trace = rec("A", LogType.ERROR, "boom", details = "java.lang.IllegalStateException\n\tat lk.cba.Foo.bar(Foo.kt:12)")
        assertTrue(filter("is:crash").ok(crash))
        assertFalse(filter("is:crash").ok(sale))
        assertTrue(filter("is:stacktrace").ok(trace))
        assertFalse(filter("is:stacktrace").ok(sale))
    }

    @Test fun `type thread and package keys`() {
        assertTrue(filter("type:TRANSACTION").ok(sale))
        assertFalse(filter("type:TRANSACTION").ok(bad))
        assertTrue(filter("thread:11").ok(sale))
        assertTrue(filter("thread:conn-11").ok(sale))
        assertFalse(filter("thread:12").ok(sale))
        assertTrue(filter("package:mine").ok(sale))
        assertTrue(filter("package:host").ok(sale))
        assertFalse(filter("package:other").ok(sale))
    }

    @Test fun `unknown key is plain text`() {
        val url = rec("A", LogType.INFO, "GET http://host/path")
        assertTrue(filter("http://host").ok(url))
    }

    @Test fun `empty value while typing matches everything`() {
        assertTrue(filter("tag:").ok(sale))
        assertTrue(filter("level:").ok(sale))
    }

    @Test fun `bad input reports an error instead of throwing`() {
        assertIs<LogcatParse.Error>(LogcatQuery.parse("level:LOUD"))
        assertIs<LogcatParse.Error>(LogcatQuery.parse("age:soon"))
        assertIs<LogcatParse.Error>(LogcatQuery.parse("tag~:[unclosed"))
        assertIs<LogcatParse.Error>(LogcatQuery.parse("message:\"no end"))
        assertIs<LogcatParse.Error>(LogcatQuery.parse("(tag:a"))
        assertIs<LogcatParse.Error>(LogcatQuery.parse("tag:a)"))
        assertIs<LogcatParse.Error>(LogcatQuery.parse("is:unknown"))
    }

    @Test fun `highlights come from positive message terms only`() {
        val f = filter("timeout -message:ignored tag:Host")
        assertEquals(1, f.highlights.size)
        assertTrue(f.highlights[0].containsMatchIn("Connection TIMEOUT"))
    }

    @Test fun `details become continuation lines`() {
        val r = rec("A", LogType.CONNECTION, "Client connected", details = "IP: 10.42.0.246")
        assertEquals(listOf("Client connected", "IP: 10.42.0.246"), r.lines)
    }

    @Test fun `simulator log types fold onto android levels`() {
        assertEquals(LogcatLevel.INFO, sale.level)
        assertEquals(LogcatLevel.INFO, parse.level)
        assertEquals(LogcatLevel.WARN, warn.level)
        assertEquals(LogcatLevel.ERROR, rec("A", LogType.SECURITY, "x").level)
        assertEquals(LogcatLevel.DEBUG, rec("A", LogType.SOCKET, "x").level)
    }

    @Test fun `suggestions offer keys then values`() {
        val keys = LogcatQuery.suggest("ta", 2, emptyList(), emptyList(), "host-sim")
        assertTrue(keys.items.any { it.insert == "tag:" })
        assertEquals(0..<2, keys.replace)

        val negated = LogcatQuery.suggest("-ta", 3, emptyList(), emptyList(), "x")
        assertTrue(negated.items.any { it.insert == "-tag:" })
        assertEquals(0..<3, negated.replace)

        val tags = LogcatQuery.suggest("level:I tag:Gat", 15, listOf("GatewayClient", "Iso8583Data"), emptyList(), "x")
        assertEquals(listOf("GatewayClient"), tags.items.map { it.insert })
        assertEquals("level:I tag:".length..<15, tags.replace)

        val levels = LogcatQuery.suggest("level:w", 7, emptyList(), emptyList(), "x")
        assertEquals(listOf("WARN"), levels.items.map { it.insert })
    }

    @Test fun `stamp derives the tag from the calling class`() {
        val e = LogEntry(timestamp = "10:00:00.000", type = LogType.INFO, message = "hi")
        LogStamp.stamp(e)
        assertEquals("LogcatQueryTest", e.metadata[LogStamp.TAG])
        assertTrue((e.metadata[LogStamp.PID] as Long) > 0)
        assertEquals(Thread.currentThread().threadId(), e.metadata[LogStamp.TID])

        val explicit = LogEntry(timestamp = "10:00:00.000", type = LogType.INFO, message = "hi", source = "APDU/USB")
        LogStamp.stamp(explicit)
        assertEquals("APDU/USB", explicit.metadata[LogStamp.TAG])
    }
}
