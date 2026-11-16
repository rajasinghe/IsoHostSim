package `in`.aicortex.iso8583studio.ui.screens.logcat

import `in`.aicortex.iso8583studio.logging.LogType
import java.util.regex.PatternSyntaxException

/**
 * Android Studio's Logcat query language.
 *
 *     tag:GatewayClient        substring, case-insensitive
 *     tag=:GatewayClient       exact match
 *     tag~:"Gateway|Hsm"       regular expression (quote anything with spaces, | or &)
 *     -tag:Bitmap              negation, works on any term and on a (group)
 *     level:WARN               that level and above (V D I W E A)
 *     age:5m                   only the last 5 seconds / minutes / hours / days (s m h d)
 *     is:crash  is:stacktrace
 *     message:"bad field"      also: type: thread: process: package: line:
 *     timeout                  a bare word matches the message or the tag
 *
 * Terms are ANDed by juxtaposition (or `&`), `|` or `OR` ORs them, AND binds tighter than OR, and
 * parentheses group. Unknown `word:` prefixes are read as plain text, so pasting a URL still works.
 */
class LogcatFilter(
    private val predicate: (LogcatRecord, Long) -> Boolean,
    /** Patterns to paint on matching message text; negated terms contribute none. */
    val highlights: List<Regex>,
    val isEmpty: Boolean,
) {
    fun matches(record: LogcatRecord, now: Long = System.currentTimeMillis()): Boolean = predicate(record, now)

    companion object {
        val ALL = LogcatFilter({ _, _ -> true }, emptyList(), true)
    }
}

sealed interface LogcatParse {
    data class Ok(val filter: LogcatFilter) : LogcatParse
    data class Error(val message: String, val position: Int) : LogcatParse
}

private class QueryError(message: String, val position: Int) : Exception(message)

private enum class Op { CONTAINS, EXACT, REGEX }

private sealed interface Tok {
    data object LParen : Tok
    data object RParen : Tok
    data object Or : Tok
    data object And : Tok
    data object Not : Tok
    data class Term(val key: String?, val op: Op, val value: String, val position: Int) : Tok
}

private typealias Pred = (LogcatRecord, Long) -> Boolean

/** A lambda written inside an `if` or `when` branch would be read as a block, so name the type. */
private fun pred(f: Pred): Pred = f
private fun textMatcher(f: (String) -> Boolean): (String) -> Boolean = f

object LogcatQuery {
    val KEYS = listOf("tag", "message", "level", "age", "is", "type", "thread", "process", "package", "line")

    fun parse(text: String): LogcatParse {
        if (text.isBlank()) return LogcatParse.Ok(LogcatFilter.ALL)
        return try {
            val parser = Parser(tokenize(text))
            val pred = parser.parseExpression()
            parser.expectEnd()
            LogcatParse.Ok(LogcatFilter(pred, parser.highlights, isEmpty = false))
        } catch (e: QueryError) {
            LogcatParse.Error(e.message ?: "Invalid query", e.position)
        }
    }

    // ---- tokenizer ---------------------------------------------------------------------------

    private fun tokenize(s: String): List<Tok> {
        val out = ArrayList<Tok>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c == '(' -> { out += Tok.LParen; i++ }
                c == ')' -> { out += Tok.RParen; i++ }
                c == '|' -> { out += Tok.Or; i++ }
                c == '&' -> { out += Tok.And; i++ }
                c == '-' && i + 1 < s.length && !s[i + 1].isWhitespace() && s[i + 1] != ')' -> {
                    out += Tok.Not; i++
                }
                else -> i = readTerm(s, i, out)
            }
        }
        return out
    }

    private fun readTerm(s: String, start: Int, out: MutableList<Tok>): Int {
        var i = start
        var key: String? = null
        var op = Op.CONTAINS

        // `key:`, `key=:` or `key~:`, but only for keys we know; anything else is free text.
        var j = i
        while (j < s.length && s[j].isLetter()) j++
        if (j > i && j < s.length) {
            var k = j
            var o = Op.CONTAINS
            if (s[k] == '~') { o = Op.REGEX; k++ } else if (s[k] == '=') { o = Op.EXACT; k++ }
            if (k < s.length && s[k] == ':' && s.substring(i, j).lowercase() in KEYS) {
                key = s.substring(i, j).lowercase()
                op = o
                i = k + 1
            }
        }

        val sb = StringBuilder()
        if (i < s.length && s[i] == '"') {
            val quoteAt = i
            i++
            while (i < s.length && s[i] != '"') {
                if (s[i] == '\\' && i + 1 < s.length && (s[i + 1] == '"' || s[i + 1] == '\\')) {
                    sb.append(s[i + 1]); i += 2
                } else {
                    sb.append(s[i]); i++
                }
            }
            if (i >= s.length) throw QueryError("Missing closing quote", quoteAt)
            i++
        } else {
            // A regex value may contain | & ( ) so only whitespace ends it; everything else stops
            // at the characters that mean something to the query itself.
            while (i < s.length && !s[i].isWhitespace()) {
                val ch = s[i]
                if (op != Op.REGEX && (ch == ')' || ch == '|' || ch == '&')) break
                sb.append(ch); i++
            }
        }

        val value = sb.toString()
        if (key == null && value == "OR") out += Tok.Or
        else if (key == null && value == "AND") out += Tok.And
        else out += Tok.Term(key, op, value, start)
        return i
    }

    // ---- parser ------------------------------------------------------------------------------

    private class Parser(private val tokens: List<Tok>) {
        private var pos = 0
        val highlights = ArrayList<Regex>()

        private fun peek(): Tok? = tokens.getOrNull(pos)

        fun expectEnd() {
            if (peek() != null) throw QueryError("Unexpected ')'", 0)
        }

        fun parseExpression(): Pred = parseOr(negated = false)

        private fun parseOr(negated: Boolean): Pred {
            val parts = arrayListOf(parseAnd(negated))
            while (peek() == Tok.Or) {
                pos++
                parts += parseAnd(negated)
            }
            return if (parts.size == 1) parts[0] else pred { r, now -> parts.any { it(r, now) } }
        }

        private fun parseAnd(negated: Boolean): Pred {
            val parts = ArrayList<Pred>()
            while (true) {
                val t = peek() ?: break
                if (t == Tok.Or || t == Tok.RParen) break
                if (t == Tok.And) { pos++; continue }
                parts += parseUnary(negated)
            }
            return when (parts.size) {
                0 -> pred { _, _ -> true }
                1 -> parts[0]
                else -> pred { r, now -> parts.all { it(r, now) } }
            }
        }

        private fun parseUnary(negated: Boolean): Pred {
            when (val t = peek()) {
                Tok.Not -> {
                    pos++
                    val inner = parseUnary(!negated)
                    return pred { r, now -> !inner(r, now) }
                }
                Tok.LParen -> {
                    pos++
                    val inner = parseOr(negated)
                    if (peek() != Tok.RParen) throw QueryError("Missing closing ')'", 0)
                    pos++
                    return inner
                }
                is Tok.Term -> {
                    pos++
                    return compile(t, negated)
                }
                else -> throw QueryError("Unexpected token", 0)
            }
        }

        private fun compile(t: Tok.Term, negated: Boolean): Pred {
            val v = t.value
            if (v.isEmpty()) return pred { _, _ -> true } // `tag:` while still typing matches everything
            val key = t.key
            val at = t.position
            return when (key) {
                null -> {
                    val m = matcher(t.op, v, at)
                    if (!negated) addHighlight(t.op, v)
                    pred { r, _ -> m(r.message) || m(r.tag) }
                }
                "tag" -> { val m = matcher(t.op, v, at); pred { r, _ -> m(r.tag) } }
                "message" -> {
                    val m = matcher(t.op, v, at)
                    if (!negated) addHighlight(t.op, v)
                    pred { r, _ -> m(r.message) }
                }
                "package" -> {
                    if (v.equals("mine", ignoreCase = true)) pred { _, _ -> true }
                    else { val m = matcher(t.op, v, at); pred { r, _ -> m(r.packageName) } }
                }
                "type" -> {
                    val m = matcher(t.op, v, at)
                    pred { r, _ -> m(r.type.name) || m(r.type.displayName) }
                }
                "thread" -> {
                    val m = matcher(t.op, v, at)
                    pred { r, _ -> m(r.tid.toString()) || m(r.threadName ?: "") }
                }
                "process" -> { val m = matcher(t.op, v, at); pred { r, _ -> m(r.pid.toString()) } }
                "line" -> { val m = matcher(t.op, v, at); pred { r, _ -> m(r.fullLine) } }
                "level" -> {
                    val lv = LogcatLevel.parse(v)
                        ?: throw QueryError("Unknown level '$v' (use V, D, I, W, E or A)", at)
                    if (t.op == Op.EXACT) pred { r, _ -> r.level == lv } else pred { r, _ -> r.level >= lv }
                }
                "age" -> {
                    val ms = parseAge(v) ?: throw QueryError("Age must look like 30s, 5m, 2h or 1d", at)
                    pred { r, now -> now - r.epochMs <= ms }
                }
                "is" -> when (v.lowercase()) {
                    "crash" -> pred { r, _ -> r.isCrash }
                    "stacktrace" -> pred { r, _ -> r.isStackTrace }
                    else -> throw QueryError("is: takes crash or stacktrace", at)
                }
                else -> throw QueryError("Unknown key '$key'", at)
            }
        }

        private fun addHighlight(op: Op, v: String) {
            runCatching {
                highlights += if (op == Op.REGEX) Regex(v, RegexOption.IGNORE_CASE)
                else Regex(Regex.escape(v), RegexOption.IGNORE_CASE)
            }
        }

        private fun matcher(op: Op, v: String, position: Int): (String) -> Boolean = when (op) {
            Op.CONTAINS -> textMatcher { s -> s.contains(v, ignoreCase = true) }
            Op.EXACT -> textMatcher { s -> s.equals(v, ignoreCase = true) }
            Op.REGEX -> {
                val re = try {
                    Regex(v, RegexOption.IGNORE_CASE)
                } catch (e: PatternSyntaxException) {
                    throw QueryError("Invalid regular expression: ${e.description}", position)
                }
                textMatcher { s -> re.containsMatchIn(s) }
            }
        }

        private fun parseAge(v: String): Long? {
            val m = Regex("""(\d+)\s*([smhd]?)""", RegexOption.IGNORE_CASE).matchEntire(v.trim()) ?: return null
            val n = m.groupValues[1].toLongOrNull() ?: return null
            val unit = when (m.groupValues[2].lowercase()) {
                "", "s" -> 1_000L
                "m" -> 60_000L
                "h" -> 3_600_000L
                else -> 86_400_000L
            }
            return n * unit
        }
    }

    // ---- suggestions -------------------------------------------------------------------------

    data class Suggestion(val label: String, val insert: String, val detail: String? = null)

    /** What to offer at [caret], and the span of [text] a chosen suggestion replaces. */
    class Suggestions(val replace: IntRange, val items: List<Suggestion>)

    private val keyHints = listOf(
        Suggestion("tag:", "tag:", "Class that logged"),
        Suggestion("tag~:", "tag~:", "Tag matches a regex"),
        Suggestion("level:", "level:", "That level and above"),
        Suggestion("message:", "message:", "Text in the message"),
        Suggestion("age:", "age:", "Last 30s, 5m, 1h"),
        Suggestion("is:", "is:", "crash or stacktrace"),
        Suggestion("type:", "type:", "Simulator log type"),
        Suggestion("thread:", "thread:", "Thread id or name"),
        Suggestion("package:", "package:", "Simulator name"),
        Suggestion("line:", "line:", "Whole formatted line"),
    )

    fun suggest(
        text: String,
        caret: Int,
        tags: Collection<String>,
        threads: Collection<String>,
        packageName: String,
    ): Suggestions {
        val c = caret.coerceIn(0, text.length)
        var start = c
        while (start > 0 && !text[start - 1].isWhitespace() && text[start - 1] != '(' &&
            text[start - 1] != '|' && text[start - 1] != '&'
        ) start--
        val token = text.substring(start, c)
        val body = token.removePrefix("-")
        val bodyStart = start + (token.length - body.length)

        val colon = body.indexOf(':')
        if (colon < 0) {
            if (body.isEmpty() && !token.startsWith("-")) return Suggestions(c..<c, emptyList())
            // A leading '-' negates, so offer the key with its dash and replace the dash too.
            val prefix = if (token.startsWith("-")) "-" else ""
            val items = keyHints
                .filter { it.label.startsWith(body, ignoreCase = true) && it.label != body }
                .map { Suggestion(prefix + it.label, prefix + it.insert, it.detail) }
            return Suggestions(start..<c, items)
        }

        val rawKey = body.substring(0, colon).trimEnd('~', '=').lowercase()
        val partial = body.substring(colon + 1).removePrefix("\"")
        val valueStart = bodyStart + colon + 1 + (body.length - colon - 1 - partial.length)

        fun quote(v: String) = if (v.any { it.isWhitespace() || it == '|' || it == '&' || it == ')' || it == '(' }) "\"$v\"" else v
        fun pick(values: Collection<String>, detail: String? = null) = values
            .filter { it.isNotEmpty() && it.contains(partial, ignoreCase = true) && !it.equals(partial, ignoreCase = true) }
            .take(12)
            .map { Suggestion(it, quote(it), detail) }

        val items = when (rawKey) {
            "tag" -> pick(tags.sortedBy { it.lowercase() })
            "level" -> pick(LogcatLevel.entries.map { it.label.uppercase() })
            "is" -> pick(listOf("crash", "stacktrace"))
            "age" -> pick(listOf("30s", "1m", "5m", "15m", "1h"))
            "type" -> pick(LogType.entries.map { it.name })
            "thread" -> pick(threads.sorted())
            "package" -> pick(listOf("mine", packageName))
            else -> emptyList()
        }
        return Suggestions(valueStart..<c, items)
    }
}
