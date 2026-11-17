package lk.cba.isohostsim.ui.screens.parser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lk.cba.isohostsim.data.BitSpecific
import lk.cba.isohostsim.data.model.GatewayConfig
import lk.cba.isohostsim.data.model.MessageLengthType
import lk.cba.isohostsim.domain.service.parser.ComparedField
import lk.cba.isohostsim.domain.service.parser.DecodeException
import lk.cba.isohostsim.domain.service.parser.DecodedField
import lk.cba.isohostsim.domain.service.parser.DecodedPacket
import lk.cba.isohostsim.domain.service.parser.FieldDiff
import lk.cba.isohostsim.domain.service.parser.Framing
import lk.cba.isohostsim.domain.service.parser.IsoPacketDecoder
import lk.cba.isohostsim.domain.service.parser.PacketComparer
import lk.cba.isohostsim.ui.design.NoteBox
import lk.cba.isohostsim.ui.design.PageHeader
import lk.cba.isohostsim.ui.design.PosCard
import lk.cba.isohostsim.ui.design.SegmentedChoice
import lk.cba.isohostsim.ui.design.StatusChip
import lk.cba.isohostsim.ui.design.Tone
import lk.cba.isohostsim.ui.design.posLine
import lk.cba.isohostsim.ui.design.posMuted
import lk.cba.isohostsim.ui.design.posSunken

/** Where the field formats come from: the standard template or a saved Host Simulator's. */
private data class ParserFormat(val label: String, val template: Array<BitSpecific>)

private enum class Mode(val label: String) { SINGLE("One packet"), COMPARE("Request vs response") }

/** Auto-detect, or one of the fixed length headers. */
private sealed class FramingChoice(val label: String) {
    data object Auto : FramingChoice("Auto-detect")
    data class Fixed(val header: MessageLengthType) : FramingChoice(if (header == MessageLengthType.NONE) "None" else header.name)
}

private val FRAMING_CHOICES: List<FramingChoice> =
    listOf(FramingChoice.Auto) + Framing.LENGTH_HEADERS.map { FramingChoice.Fixed(it) }

/** The outcome of decoding one input box. */
private sealed class Parsed {
    data object Empty : Parsed()
    data class Failed(val message: String) : Parsed()
    data class Ok(val packet: DecodedPacket, val detected: Boolean) : Parsed()
}

private fun parse(text: String, choice: FramingChoice, tpdu: Boolean, template: Array<BitSpecific>): Parsed {
    if (text.isBlank()) return Parsed.Empty
    return try {
        val bytes = IsoPacketDecoder.parseHex(text)
        when (choice) {
            FramingChoice.Auto -> IsoPacketDecoder.detect(bytes, template).firstOrNull()
                ?.let { Parsed.Ok(it.packet, detected = true) }
                ?: Parsed.Failed(
                    // Show why the most common framing fails, which is usually the useful message.
                    runCatching { IsoPacketDecoder.decode(bytes, Framing(MessageLengthType.HEX_HL, true), template) }
                        .exceptionOrNull()?.message?.let { "No framing fits this packet. With a HEX_HL length and a TPDU: $it" }
                        ?: "No framing fits this packet."
                )
            is FramingChoice.Fixed -> Parsed.Ok(IsoPacketDecoder.decode(bytes, Framing(choice.header, tpdu), template), detected = false)
        }
    } catch (e: DecodeException) {
        Parsed.Failed(e.message ?: "The packet could not be read.")
    }
}

/**
 * ISO8583 Parser: paste a packet as hex and read it as one table, one row per field. The framing
 * (length header and TPDU) is detected from the bytes unless you pick it. Compare mode reads a
 * request and its response side by side and marks the fields that differ or are missing.
 */
@Composable
fun IsoParserScreen(hostConfigs: List<GatewayConfig> = emptyList()) {
    val formats = remember(hostConfigs) {
        listOf(ParserFormat("Standard ISO 8583", IsoPacketDecoder.standardTemplate())) +
            hostConfigs.map { ParserFormat("Host Simulator: ${it.name}", it.getBitTemplate(true) ?: it.bitTemplateSource) }
    }
    var format by remember(formats) { mutableStateOf(formats.first()) }
    var mode by remember { mutableStateOf(Mode.SINGLE) }
    var framing by remember { mutableStateOf<FramingChoice>(FramingChoice.Auto) }
    var tpdu by remember { mutableStateOf(true) }
    var requestText by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("") }
    var onlyDifferences by remember { mutableStateOf(false) }

    val request = remember(requestText, framing, tpdu, format) { parse(requestText, framing, tpdu, format.template) }
    val response = remember(responseText, framing, tpdu, format, mode) {
        if (mode == Mode.COMPARE) parse(responseText, framing, tpdu, format.template) else Parsed.Empty
    }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colors.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageHeader(
                title = "ISO8583 Parser",
                subtitle = "Paste a packet as hex. Spaces, new lines and 0x are fine.",
                actions = {
                    SegmentedChoice(Mode.entries.toList(), mode, { it.label }, { mode = it })
                },
            )
        }

        item {
            PosCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Labeled("Field formats") { FormatPicker(formats, format) { format = it } }
                    Labeled("Length header") {
                        SegmentedChoice(FRAMING_CHOICES, framing, { it.label }, { framing = it })
                    }
                    if (framing != FramingChoice.Auto) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 18.dp)) {
                            Checkbox(checked = tpdu, onCheckedChange = { tpdu = it })
                            Text("TPDU (5 bytes)", fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        item {
            if (mode == Mode.SINGLE) {
                HexInput("Packet", requestText, { requestText = it }, Modifier.fillMaxWidth())
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HexInput("Request", requestText, { requestText = it }, Modifier.weight(1f))
                    HexInput("Response", responseText, { responseText = it }, Modifier.weight(1f))
                }
            }
        }

        if (mode == Mode.SINGLE) {
            when (val r = request) {
                Parsed.Empty -> item { NoteBox("Nothing to show yet. Paste a packet above; it's decoded as you type.") }
                is Parsed.Failed -> item { NoteBox(r.message, Tone.Danger) }
                is Parsed.Ok -> {
                    item { PacketSummary(null, r) }
                    item { FieldTable(r.packet) }
                    r.packet.error?.let { e -> item { NoteBox("Stopped here: $e", Tone.Danger) } }
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.weight(1f)) { SideStatus("Request", request) }
                    Box(Modifier.weight(1f)) { SideStatus("Response", response) }
                }
            }
            val req = request as? Parsed.Ok
            val res = response as? Parsed.Ok
            if (req != null && res != null) {
                item {
                    CompareTable(
                        PacketComparer.compare(req.packet, res.packet),
                        req.packet,
                        res.packet,
                        onlyDifferences,
                    ) { onlyDifferences = it }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Controls

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
private fun FormatPicker(formats: List<ParserFormat>, selected: ParserFormat, onSelect: (ParserFormat) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.widthIn(min = 240.dp).heightIn(min = 44.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(selected.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            formats.forEach { f -> DropdownMenuItem(onClick = { onSelect(f); open = false }) { Text(f.label) } }
        }
    }
}

@Composable
private fun HexInput(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text("00 CF 60 00 96 00 00 02 00 …", fontFamily = FontFamily.Monospace) },
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
        modifier = modifier.heightIn(min = 110.dp, max = 180.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// One packet

/** MTI, framing and the header bytes, on one line, then any warnings. */
@Composable
private fun PacketSummary(title: String?, parsed: Parsed.Ok) {
    val p = parsed.packet
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, fontWeight = FontWeight.SemiBold)
            StatusChip("MTI ${p.mti}", Tone.Accent)
            StatusChip(p.framing.label + if (parsed.detected) " (detected)" else "", Tone.Neutral)
            StatusChip("${p.fields.size} fields", Tone.Neutral)
        }
        SelectionContainer {
            Text(
                listOfNotNull(
                    p.declaredLength?.let { "Length $it" },
                    p.tpdu?.let { "TPDU ${it.chunked(2).joinToString(" ")}" },
                    "Bitmap ${p.bitmapHex}",
                ).joinToString("    "),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = posMuted(),
            )
        }
        p.warnings.forEach { NoteBox(it, Tone.Warning) }
        if (title != null) p.error?.let { NoteBox("Stopped after ${p.fields.size} fields: $it", Tone.Danger) }
    }
}

@Composable
private fun SideStatus(label: String, parsed: Parsed) {
    when (parsed) {
        Parsed.Empty -> NoteBox("Paste the $label.")
        is Parsed.Failed -> NoteBox("$label: ${parsed.message}", Tone.Danger)
        is Parsed.Ok -> PacketSummary(label, parsed)
    }
}

private val COL_FIELD = 56.dp
private val COL_NAME = 220.dp
private val COL_FORMAT = 110.dp
private val COL_LEN = 56.dp

@Composable
private fun FieldTable(packet: DecodedPacket) {
    var expanded by remember(packet) { mutableStateOf<Int?>(null) }
    PosCard(Modifier.fillMaxWidth(), padding = 0.dp) {
        HeaderRow {
            HeaderCell("Field", COL_FIELD)
            HeaderCell("Name", COL_NAME)
            HeaderCell("Format", COL_FORMAT)
            HeaderCell("Length", COL_LEN)
            Text("Value", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = posMuted(), modifier = Modifier.weight(1f))
        }
        packet.fields.forEach { f ->
            Divider()
            Column(Modifier.fillMaxWidth().clickable { expanded = if (expanded == f.number) null else f.number }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.Top) {
                    Mono(f.number.toString(), COL_FIELD, bold = true)
                    Text(f.name, fontSize = 13.sp, modifier = Modifier.width(COL_NAME).padding(end = 8.dp))
                    Mono(f.format, COL_FORMAT, muted = true)
                    Mono(f.length.toString(), COL_LEN, muted = true)
                    SelectionContainer(Modifier.weight(1f)) {
                        Text(f.value, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                }
                if (expanded == f.number) FieldDetail(f)
            }
        }
    }
    Text(
        "Click a row for its raw bytes${if (packet[55] != null) " and, for field 55, its EMV tags" else ""}.",
        color = posMuted(),
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** Raw bytes and offset, and the EMV tags for field 55. */
@Composable
private fun FieldDetail(f: DecodedField) {
    Column(
        Modifier.fillMaxWidth().background(posSunken()).padding(start = 16.dp + COL_FIELD, end = 16.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SelectionContainer {
            Text("Raw  ${f.rawHex}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Text("Starts at byte ${f.offset}", fontSize = 12.sp, color = posMuted())
        if (f.number == 55) {
            val tags = IsoPacketDecoder.tlv(f.rawHex)
            if (tags.isEmpty()) Text("No EMV tags could be read.", fontSize = 12.sp, color = posMuted())
            SelectionContainer {
                Column {
                    tags.forEach { t ->
                        Row {
                            Text(t.tag, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(56.dp))
                            Text(t.name, fontSize = 12.sp, color = posMuted(), modifier = Modifier.width(200.dp))
                            Text(t.valueHex, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Request vs response

@Composable
private fun CompareTable(
    rows: List<ComparedField>,
    request: DecodedPacket,
    response: DecodedPacket,
    onlyDifferences: Boolean,
    onOnlyDifferences: (Boolean) -> Unit,
) {
    val counts = rows.groupingBy { it.diff }.eachCount()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusChip("MTI ${request.mti} → ${response.mti}", Tone.Accent)
        FieldDiff.entries.forEach { d -> counts[d]?.let { StatusChip("${d.label}: $it", toneOf(d)) } }
        Box(Modifier.weight(1f))
        Checkbox(checked = onlyDifferences, onCheckedChange = onOnlyDifferences)
        Text("Hide fields that are the same", fontSize = 14.sp)
    }
    PosCard(Modifier.fillMaxWidth().padding(top = 10.dp), padding = 0.dp) {
        HeaderRow {
            HeaderCell("Field", COL_FIELD)
            HeaderCell("Name", COL_NAME)
            Text("Request", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = posMuted(), modifier = Modifier.weight(1f))
            Text("Response", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = posMuted(), modifier = Modifier.weight(1f))
            HeaderCell("", 120.dp)
        }
        rows.filter { !onlyDifferences || it.diff != FieldDiff.SAME }.forEach { row ->
            Divider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.Top) {
                Mono(row.number.toString(), COL_FIELD, bold = true)
                Text(row.name, fontSize = 13.sp, modifier = Modifier.width(COL_NAME).padding(end = 8.dp))
                SelectionContainer(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(row.request?.value ?: "—", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = if (row.request == null) posMuted() else MaterialTheme.colors.onSurface)
                }
                SelectionContainer(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(row.response?.value ?: "—", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = if (row.response == null) posMuted() else MaterialTheme.colors.onSurface)
                }
                Box(Modifier.width(120.dp)) {
                    if (row.diff != FieldDiff.SAME) StatusChip(row.diff.label, toneOf(row.diff)) else Text("Same", fontSize = 12.sp, color = posMuted())
                }
            }
        }
    }
}

private fun toneOf(d: FieldDiff) = when (d) {
    FieldDiff.SAME -> Tone.Neutral
    FieldDiff.CHANGED -> Tone.Warning
    FieldDiff.REQUEST_ONLY -> Tone.Danger
    FieldDiff.RESPONSE_ONLY -> Tone.Success
}

// ---------------------------------------------------------------------------------------------
// Table pieces

@Composable
private fun HeaderRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(posSunken()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = posMuted(), modifier = Modifier.width(width))
}

@Composable
private fun Mono(text: String, width: androidx.compose.ui.unit.Dp, bold: Boolean = false, muted: Boolean = false) {
    Text(
        text,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = if (muted) posMuted() else MaterialTheme.colors.onSurface,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(posLine()))
}
