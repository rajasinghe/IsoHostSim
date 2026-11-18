package lk.cba.isohostsim.ui.screens.logcat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.gestures.detectTapGestures
import lk.cba.isohostsim.logging.LogEntry
import kotlinx.coroutines.delay
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

private val Mono = FontFamily.Monospace
private val FontSize = 12.sp
private val LineHeight = 17.sp

private val LevelWidth = 26.dp
private const val CellPadding = 10 // dp: 8 of gap after a cell's text plus a little slack

/** Column widths in dp, derived from the real glyph width so nothing is clipped on any font. */
private class Columns(
    val timestamp: androidx.compose.ui.unit.Dp,
    val pidTid: androidx.compose.ui.unit.Dp,
    val tag: androidx.compose.ui.unit.Dp,
    val packageName: androidx.compose.ui.unit.Dp,
    val compactTime: androidx.compose.ui.unit.Dp,
)

/** What the rows show, resolved once per recomposition from the chosen format. */
private class RowOptions(
    val compact: Boolean,
    val timestamp: Boolean,
    val pidTid: Boolean,
    val tag: Boolean,
    val packageName: Boolean,
    val level: Boolean,
    val wrap: Boolean,
)

/**
 * A log viewer that looks and behaves like Android Studio's Logcat: one coloured line per entry
 * (timestamp, pid-tid, tag, package, level badge, message), a query box with `tag:`, `level:`,
 * `message:`, `age:` and friends, suggestions as you type, and a history of recent queries.
 *
 * It reads the same live buffer as the classic Logs tab, so it needs no extra plumbing. The buffer
 * is capped (as logcat's own is), so a long run keeps the most recent lines; the Logs tab keeps the
 * complete history on disk.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun LogcatTab(
    state: LogcatState,
    entries: List<LogEntry>,
    packageName: String,
    bufferSize: Int,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = if (state.darkTheme) LogcatPalette.Dark else LogcatPalette.Light
    val cache = remember { LogcatRecordCache() }

    val source: List<LogEntry> = state.frozen ?: entries
    val size = source.size
    val lastId = source.lastOrNull()?.id
    val records = remember(state.frozen, size, lastId, packageName) { cache.convert(source.toList(), packageName) }

    val parsed = remember(state.query.text) { LogcatQuery.parse(state.query.text) }
    val filter = when (parsed) {
        is LogcatParse.Ok -> parsed.filter
        is LogcatParse.Error -> state.lastGood
    }
    SideEffect { if (parsed is LogcatParse.Ok) state.lastGood = parsed.filter }
    val error = (parsed as? LogcatParse.Error)?.message

    // `age:` results go stale as the clock moves even when no line arrives, so tick while it is used.
    val usesAge = "age:" in state.query.text
    val tick by produceState(0, usesAge) { while (usesAge) { delay(1000); value++ } }
    val visible = remember(records, filter, tick) {
        val now = System.currentTimeMillis()
        records.filter { filter.matches(it, now) }
    }

    val tags = remember(records) { records.asReversed().map { it.tag }.distinct() }
    val threads = remember(records) { records.flatMap { listOf(it.tid.toString(), it.threadName.orEmpty()) }.distinct() }

    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current

    val options = RowOptions(
        compact = state.format == LogcatFormat.COMPACT,
        timestamp = state.format != LogcatFormat.CUSTOM || state.showTimestamp,
        // Process and thread ids are noise for most users, so only the Custom view can switch them on.
        pidTid = state.format == LogcatFormat.CUSTOM && state.showPidTid,
        tag = state.format != LogcatFormat.CUSTOM || state.showTag,
        packageName = state.format != LogcatFormat.CUSTOM || state.showPackage,
        level = state.format != LogcatFormat.CUSTOM || state.showLevel,
        wrap = state.softWrap,
    )

    fun selectedText(): String {
        if (state.anchor < 0 || visible.isEmpty()) return ""
        val lo = min(state.anchor, state.focus).coerceIn(0, visible.lastIndex)
        val hi = max(state.anchor, state.focus).coerceIn(0, visible.lastIndex)
        return visible.subList(lo, hi + 1).joinToString("\n") { it.fullLine }
    }

    Column(modifier.fillMaxSize().background(palette.background)) {
        Header(palette, state, visible.size, records.size, bufferSize)
        Divider(color = palette.border, thickness = 1.dp)
        Row(Modifier.weight(1f)) {
            SideToolbar(
                palette = palette,
                state = state,
                onClear = { state.clearSelection(); onClear() },
                onScrollToEnd = { state.autoScroll = true },
                onTogglePause = { state.togglePause(entries) },
                onExport = { exportLines(visible.map { it.fullLine }) },
            )
            Box(Modifier.width(1.dp).fillMaxHeight().background(palette.border))
            Column(Modifier.weight(1f)) {
                FilterBar(palette, state, tags, threads, packageName) { focusRequester.requestFocus() }
                if (error != null) {
                    Text(
                        error,
                        color = palette.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 10.dp, bottom = 4.dp),
                    )
                }
                Divider(color = palette.border, thickness = 1.dp)
                LogList(
                    palette = palette,
                    state = state,
                    options = options,
                    visible = visible,
                    highlights = filter.highlights,
                    packageName = packageName,
                    listState = listState,
                    focusRequester = focusRequester,
                    emptyText = if (records.isEmpty()) "Waiting for log output. Start the gateway and send a transaction."
                    else "No log lines match the current filter.",
                    onCopy = { text -> if (text.isNotEmpty()) clipboard.setText(AnnotatedString(text)) },
                    selectedText = ::selectedText,
                    onClear = { state.clearSelection(); onClear() },
                )
            }
        }
    }
}

// ---- header ----------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Header(
    palette: LogcatPalette,
    state: LogcatState,
    shown: Int,
    total: Int,
    bufferSize: Int,
) {
    Row(
        Modifier.fillMaxWidth().height(34.dp).background(palette.toolbar).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Terminal, null, tint = palette.text, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.fillMaxHeight().drawBehind {
                drawRect(palette.accent, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx()))
            }.padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Logcat", color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.weight(1f))
        if (state.paused) {
            Text("Paused", color = Color(0xFFE6B422), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(12.dp))
        }
        TooltipArea(
            tooltip = { Tip("Lines shown of lines buffered. The buffer keeps the most recent $bufferSize lines.", palette) },
            delayMillis = 500,
        ) {
            Text(
                if (shown == total) "$total lines" else "$shown / $total lines",
                color = palette.muted,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        FormatMenu(palette, state)
    }
}

@Composable
private fun FormatMenu(palette: LogcatPalette, state: LogcatState) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(24.dp).clip(RoundedCornerShape(4.dp)).clickable { open = true }.padding(start = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(state.format.label, color = palette.text, fontSize = 12.sp)
            Icon(Icons.Default.ArrowDropDown, null, tint = palette.text, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LogcatFormat.entries.forEach { f ->
                DropdownMenuItem(onClick = { state.format = f; open = false }) {
                    MenuCheck(f == state.format)
                    Text(f.label, fontSize = 13.sp)
                }
            }
            if (state.format == LogcatFormat.CUSTOM) {
                Divider()
                ColumnToggle("Timestamp", state.showTimestamp) { state.showTimestamp = it }
                ColumnToggle("Process and thread id", state.showPidTid) { state.showPidTid = it }
                ColumnToggle("Tag", state.showTag) { state.showTag = it }
                ColumnToggle("Package", state.showPackage) { state.showPackage = it }
                ColumnToggle("Level", state.showLevel) { state.showLevel = it }
            }
        }
    }
}

@Composable
private fun ColumnToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(onClick = { onChange(!checked) }) {
        MenuCheck(checked)
        Text(label, fontSize = 13.sp)
    }
}

@Composable
private fun MenuCheck(visible: Boolean) {
    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        if (visible) Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
    }
}

// ---- left toolbar ----------------------------------------------------------------------------

@Composable
private fun SideToolbar(
    palette: LogcatPalette,
    state: LogcatState,
    onClear: () -> Unit,
    onScrollToEnd: () -> Unit,
    onTogglePause: () -> Unit,
    onExport: () -> Unit,
) {
    Column(
        Modifier.width(34.dp).fillMaxHeight().background(palette.toolbar).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ToolButton(Icons.Default.DeleteSweep, "Clear Logcat", palette, onClick = onClear)
        ToolButton(Icons.Default.VerticalAlignBottom, "Scroll to the End", palette, active = state.autoScroll, onClick = onScrollToEnd)
        ToolButton(Icons.Default.WrapText, "Soft-Wrap", palette, active = state.softWrap) { state.softWrap = !state.softWrap }
        ToolButton(
            if (state.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
            if (state.paused) "Resume Logcat" else "Pause Logcat",
            palette,
            active = state.paused,
            onClick = onTogglePause,
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(18.dp).height(1.dp).background(palette.border))
        Spacer(Modifier.height(4.dp))
        ToolButton(Icons.Default.Save, "Export Logcat…", palette, onClick = onExport)
        ToolButton(
            if (state.darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
            if (state.darkTheme) "Switch to light theme" else "Switch to dark theme",
            palette,
        ) { state.darkTheme = !state.darkTheme }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolButton(
    icon: ImageVector,
    tip: String,
    palette: LogcatPalette,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    TooltipArea(
        tooltip = { Tip(tip, palette) },
        delayMillis = 400,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(12.dp, 14.dp)),
    ) {
        Box(
            Modifier.padding(vertical = 1.dp).size(26.dp).clip(RoundedCornerShape(4.dp))
                .background(if (active) palette.selection else Color.Transparent)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, tip, tint = if (active) palette.accent else palette.text, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun Tip(text: String, palette: LogcatPalette) {
    Surface(
        color = palette.field,
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(1.dp, palette.border),
        elevation = 4.dp,
    ) {
        Text(text, color = palette.text, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

// ---- query box -------------------------------------------------------------------------------

@Composable
private fun FilterBar(
    palette: LogcatPalette,
    state: LogcatState,
    tags: Collection<String>,
    threads: Collection<String>,
    packageName: String,
    onFocusList: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var fieldHeight by remember { mutableIntStateOf(0) }

    val text = state.query.text
    val caret = state.query.selection.start
    val suggestions = remember(text, caret, tags, threads, packageName) {
        LogcatQuery.suggest(text, caret, tags, threads, packageName)
    }
    var selected by remember(suggestions) { mutableIntStateOf(0) }
    val popupOpen = focused && showSuggestions && suggestions.items.isNotEmpty()

    fun accept(item: LogcatQuery.Suggestion) {
        val r = suggestions.replace
        val isKey = item.insert.endsWith(":")
        val insert = if (isKey) item.insert else item.insert + " "
        state.query = TextFieldValue(text.replaceRange(r.first, r.last + 1, insert), TextRange(r.first + insert.length))
        showSuggestions = isKey
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(4.dp)).background(palette.field)
                .border(1.dp, if (focused) palette.accent else palette.border, RoundedCornerShape(4.dp))
                .onSizeChanged { fieldHeight = it.height },
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Box(
                        Modifier.padding(horizontal = 4.dp).size(22.dp).clip(RoundedCornerShape(3.dp)).clickable { historyOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.History, "Query history", tint = palette.muted, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = historyOpen, onDismissRequest = { historyOpen = false }) {
                        if (state.history.isEmpty()) {
                            DropdownMenuItem(onClick = { historyOpen = false }, enabled = false) {
                                Text("No recent queries", fontSize = 13.sp)
                            }
                        } else {
                            state.history.forEach { q ->
                                DropdownMenuItem(onClick = { state.setQuery(q); historyOpen = false; onFocusList() }) {
                                    Text(q, fontSize = 13.sp, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Divider()
                            DropdownMenuItem(onClick = { state.clearHistory(); historyOpen = false }) {
                                Text("Clear history", fontSize = 13.sp)
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = state.query,
                        onValueChange = { state.query = it; showSuggestions = true },
                        singleLine = true,
                        textStyle = TextStyle(color = palette.text, fontSize = 13.sp, fontFamily = Mono, letterSpacing = 0.sp),
                        cursorBrush = SolidColor(palette.text),
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged {
                                focused = it.isFocused
                                if (!it.isFocused) { showSuggestions = false; state.commitQuery() }
                            }
                            .onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                val n = suggestions.items.size
                                when {
                                    e.isCtrlPressed && e.key == Key.Spacebar -> { showSuggestions = true; true }
                                    popupOpen && e.key == Key.DirectionDown -> { selected = (selected + 1) % n; true }
                                    popupOpen && e.key == Key.DirectionUp -> { selected = (selected - 1 + n) % n; true }
                                    popupOpen && (e.key == Key.Enter || e.key == Key.Tab) -> {
                                        accept(suggestions.items[selected.coerceIn(0, n - 1)]); true
                                    }
                                    popupOpen && e.key == Key.Escape -> { showSuggestions = false; true }
                                    e.key == Key.Enter -> { state.commitQuery(); showSuggestions = false; onFocusList(); true }
                                    else -> false
                                }
                            },
                    )
                    if (text.isEmpty()) {
                        Text(
                            "Search for messages. Use 'key:value' to filter by tag, level, etc.",
                            color = palette.muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (text.isNotEmpty()) {
                    Box(
                        Modifier.padding(horizontal = 4.dp).size(22.dp).clip(RoundedCornerShape(3.dp)).clickable { state.setQuery("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Close, "Clear query", tint = palette.muted, modifier = Modifier.size(14.dp))
                    }
                }
            }
            if (popupOpen) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, fieldHeight + 2),
                    properties = PopupProperties(focusable = false),
                ) {
                    Column(
                        Modifier.widthIn(min = 280.dp, max = 460.dp)
                            .border(1.dp, palette.border)
                            .background(palette.toolbar)
                            .padding(vertical = 2.dp),
                    ) {
                        suggestions.items.forEachIndexed { i, s ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (i == selected) palette.selection else Color.Transparent)
                                    .pointerInput(s) { detectTapGestures { accept(s) } }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(s.label, color = palette.text, fontSize = 13.sp, fontFamily = Mono)
                                Spacer(Modifier.weight(1f))
                                Spacer(Modifier.width(16.dp))
                                s.detail?.let { Text(it, color = palette.muted, fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- the list --------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun LogList(
    palette: LogcatPalette,
    state: LogcatState,
    options: RowOptions,
    visible: List<LogcatRecord>,
    highlights: List<Regex>,
    packageName: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    focusRequester: FocusRequester,
    emptyText: String,
    onCopy: (String) -> Unit,
    selectedText: () -> String,
    onClear: () -> Unit,
) {
    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val charPx = remember(density, measurer) {
        // Ten glyphs, then divide: one glyph's width is rounded to whole pixels and drifts.
        measurer.measure("0000000000", TextStyle(fontFamily = Mono, fontSize = FontSize, letterSpacing = 0.sp)).size.width / 10f
    }

    // Follow the tail while pinned to the bottom, like logcat's "scroll to end".
    LaunchedEffect(visible.lastOrNull(), state.autoScroll) {
        if (state.autoScroll && visible.isNotEmpty()) listState.scrollToItem(visible.lastIndex)
    }
    // A scroll the user made away from the bottom stops the follow; returning to it resumes.
    val atBottom by remember(visible) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            visible.isEmpty() || last >= visible.lastIndex
        }
    }
    var userScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) userScrolled = true
            else if (userScrolled) state.autoScroll = atBottom
        }
    }

    Box(
        Modifier.fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    // Whole lines selected: copy those. Otherwise leave Ctrl+C to the text selection in the list.
                    e.isCtrlPressed && e.key == Key.C -> if (state.anchor >= 0) { onCopy(selectedText()); true } else false
                    e.isCtrlPressed && e.key == Key.A -> {
                        if (visible.isNotEmpty()) { state.anchor = 0; state.focus = visible.lastIndex }
                        true
                    }
                    e.key == Key.Escape -> { state.clearSelection(); true }
                    e.key == Key.MoveEnd -> { state.autoScroll = true; true }
                    e.key == Key.DirectionDown || e.key == Key.DirectionUp -> {
                        if (visible.isNotEmpty()) {
                            val from = if (state.focus < 0) (if (e.key == Key.DirectionDown) -1 else visible.size) else state.focus
                            val to = (from + if (e.key == Key.DirectionDown) 1 else -1).coerceIn(0, visible.lastIndex)
                            state.anchor = to; state.focus = to
                            state.autoScroll = false
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        if (visible.isEmpty()) {
            Text(emptyText, color = palette.muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
            return@Box
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport = maxWidth
            val charWidth = with(density) { charPx.toDp() }
            val pad = CellPadding.dp
            val columns = remember(visible, charPx, packageName) {
                Columns(
                    timestamp = charWidth * 23 + pad,
                    pidTid = charWidth * (visible.maxOfOrNull { it.pidTid.length } ?: 7).coerceIn(5, 18) + pad,
                    tag = charWidth * (visible.maxOfOrNull { it.tag.length } ?: 8).coerceIn(6, 23) + pad,
                    // Sized from the names actually on screen: a record can carry its own package, and real
                    // Android package names are often longer than the viewer's default one.
                    packageName = charWidth * (visible.maxOfOrNull { it.packageName.length } ?: packageName.length).coerceIn(4, 48) + pad,
                    compactTime = charWidth * 18 + pad,
                )
            }
            val prefixWidth =
                if (options.compact) columns.compactTime + charWidth * 24
                else (if (options.timestamp) columns.timestamp else 0.dp) +
                    (if (options.pidTid) columns.pidTid else 0.dp) +
                    (if (options.tag) columns.tag else 0.dp) +
                    (if (options.packageName) columns.packageName else 0.dp) +
                    (if (options.level) LevelWidth else 0.dp)
            val longest = remember(visible) { visible.maxOfOrNull { r -> r.lines.maxOf { it.length + 3 * it.count { c -> c == '\t' } } } ?: 0 }
            val contentWidth = if (options.wrap) viewport else max(viewport.value, (prefixWidth + charWidth * longest + 32.dp).value).dp

            Box(Modifier.fillMaxSize().then(if (options.wrap) Modifier else Modifier.horizontalScroll(hScroll))) {
                // Lets the user drag across the log text and copy just that part with Ctrl+C.
                SelectionContainer(Modifier.width(contentWidth).fillMaxHeight()) {
                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        itemsIndexed(visible) { index, rec ->
                            val lo = min(state.anchor, state.focus)
                            val hi = max(state.anchor, state.focus)
                            val isSelected = state.anchor >= 0 && index in lo..hi
                            LogcatRow(
                                rec = rec,
                                selected = isSelected,
                                palette = palette,
                                options = options,
                                columns = columns,
                                highlights = highlights,
                                onClick = { shift ->
                                    if (shift && state.anchor >= 0) state.focus = index
                                    else { state.anchor = index; state.focus = index }
                                    focusRequester.requestFocus()
                                },
                                // Dragging selects text instead of lines, and must not be scrolled away by the tail.
                                onDragStart = { state.clearSelection(); state.autoScroll = false },
                                menu = {
                                    val hereOrSelection = if (isSelected) selectedText() else rec.fullLine
                                    listOf(
                                        ContextMenuItem("Copy") { onCopy(hereOrSelection) },
                                        ContextMenuItem("Copy message") { onCopy(rec.message) },
                                        ContextMenuItem("Show only tag '${rec.tag}'") { state.addTerm("tag:${quoteIfNeeded(rec.tag)}") },
                                        ContextMenuItem("Hide tag '${rec.tag}'") { state.addTerm("-tag:${quoteIfNeeded(rec.tag)}") },
                                        ContextMenuItem("Show ${rec.level.label} and above") { state.addTerm("level:${rec.level.label.uppercase()}") },
                                        ContextMenuItem("Show only thread ${rec.tid}") { state.addTerm("thread:${rec.tid}") },
                                        ContextMenuItem("Select all") { state.anchor = 0; state.focus = visible.lastIndex },
                                        ContextMenuItem("Clear Logcat") { onClear() },
                                    )
                                },
                            )
                        }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            if (!options.wrap) {
                HorizontalScrollbar(rememberScrollbarAdapter(hScroll), Modifier.align(Alignment.BottomStart).fillMaxWidth())
            }
        }
    }
}

private fun quoteIfNeeded(v: String): String =
    if (v.any { it.isWhitespace() || it == '|' || it == '&' || it == '(' || it == ')' }) "\"$v\"" else v

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogcatRow(
    rec: LogcatRecord,
    selected: Boolean,
    palette: LogcatPalette,
    options: RowOptions,
    columns: Columns,
    highlights: List<Regex>,
    onClick: (shift: Boolean) -> Unit,
    onDragStart: () -> Unit,
    menu: () -> List<ContextMenuItem>,
) {
    val messageColor = palette.messageColor(rec.level)
    val message = remember(rec, highlights, palette) { highlighted(rec.message.replace("\t", "    "), highlights, palette.highlight) }
    val weight = if (rec.level >= LogcatLevel.ASSERT) FontWeight.Bold else FontWeight.Normal

    ContextMenuArea(items = menu) {
        Row(
            Modifier.fillMaxWidth()
                .background(if (selected) palette.selection else Color.Transparent)
                .pointerHoverIcon(PointerIcon.Text)
                // A click selects the line; a drag is left to the text selection around the list. Compose 1.8
                // has no shift-key query, so read it off the AWT event behind the release.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        var down: Offset? = null
                        var dragged = false
                        while (true) {
                            val e = awaitPointerEvent()
                            val pos = e.changes.firstOrNull()?.position ?: continue
                            when (e.type) {
                                PointerEventType.Press -> if (e.buttons.isPrimaryPressed) { down = pos; dragged = false }
                                PointerEventType.Move -> {
                                    val start = down
                                    if (start != null && !dragged && (pos - start).getDistance() > viewConfiguration.touchSlop) {
                                        dragged = true
                                        onDragStart()
                                    }
                                }
                                PointerEventType.Release -> {
                                    if (down != null && !dragged) {
                                        onClick((e.nativeEvent as? java.awt.event.MouseEvent)?.isShiftDown == true)
                                    }
                                    down = null
                                }
                            }
                        }
                    }
                }
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            if (options.compact) {
                Cell(rec.compactTime, palette.muted, columns.compactTime)
                Text(
                    "${rec.level.letter}/${rec.tag}:",
                    color = palette.tagColor(rec.tag),
                    fontFamily = Mono, fontSize = FontSize, lineHeight = LineHeight, letterSpacing = 0.sp,
                    maxLines = 1, softWrap = false,
                )
                Spacer(Modifier.width(8.dp))
            } else {
                if (options.timestamp) Cell(rec.dateTime, palette.muted, columns.timestamp)
                if (options.pidTid) Cell(rec.pidTid, palette.muted, columns.pidTid)
                if (options.tag) Cell(rec.tag, palette.tagColor(rec.tag), columns.tag)
                if (options.packageName) Cell(rec.packageName, palette.text, columns.packageName)
                if (options.level) {
                    Box(Modifier.width(LevelWidth), contentAlignment = Alignment.CenterStart) {
                        Box(
                            Modifier.size(width = 16.dp, height = 15.dp).clip(RoundedCornerShape(3.dp)).background(palette.badgeColor(rec.level)),
                            contentAlignment = Alignment.Center,
                        ) {
                            DisableSelection {
                                Text(
                                    rec.level.letter.toString(),
                                    color = palette.badgeTextColor(rec.level),
                                    fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
            Text(
                message,
                color = messageColor,
                fontFamily = Mono, fontSize = FontSize, lineHeight = LineHeight, letterSpacing = 0.sp, fontWeight = weight,
                softWrap = options.wrap,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Cell(text: String, color: Color, width: androidx.compose.ui.unit.Dp) {
    Text(
        text,
        color = color,
        fontFamily = Mono, fontSize = FontSize, lineHeight = LineHeight, letterSpacing = 0.sp,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).padding(end = 8.dp),
    )
}

private fun highlighted(text: String, patterns: List<Regex>, background: Color): AnnotatedString {
    if (patterns.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        for (re in patterns) {
            var n = 0
            for (m in re.findAll(text)) {
                if (m.range.isEmpty()) continue
                addStyle(SpanStyle(background = background), m.range.first, m.range.last + 1)
                if (++n >= 50) break
            }
        }
    }
}

private fun exportLines(lines: List<String>) {
    if (lines.isEmpty()) return
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    val dialog = FileDialog(null as Frame?, "Export Logcat", FileDialog.SAVE)
    dialog.file = "logcat-$stamp.txt"
    dialog.isVisible = true
    val name = dialog.file ?: return
    try {
        File(dialog.directory, name).writeText(lines.joinToString("\n") + "\n")
    } catch (_: Exception) {
        // The dialog already told the user where it was saving; nothing useful to add on failure.
    }
}
