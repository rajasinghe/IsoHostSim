package lk.cba.isohostsim.ui.screens.generic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lk.cba.isohostsim.data.BitAttribute
import lk.cba.isohostsim.data.BitTemplate
import lk.cba.isohostsim.data.ResultDialogInterface
import lk.cba.isohostsim.data.model.GatewayConfig
import lk.cba.isohostsim.data.model.MessageLengthType
import lk.cba.isohostsim.ui.navigation.NavigationController
import lk.cba.isohostsim.ui.screens.components.AppBarWithBack
import lk.cba.isohostsim.ui.screens.hostSimulator.UnsolicitedMessageTab
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import lk.cba.isohostsim.ui.design.PosCard
import lk.cba.isohostsim.ui.design.SegmentedChoice
import lk.cba.isohostsim.ui.design.posMuted

/** Where the parser takes its field formats from. */
private data class ParserFormat(val label: String, val config: GatewayConfig)

/**
 * ISO8583 Parser: paste a packet as hex and see every field decoded, with the bitmap, EMV tags
 * (field 55), POS entry mode, condition code and Track 2 broken down.
 *
 * The fields are read with a chosen format: the standard ISO 8583 template, or the bit template of a
 * saved Host Simulator, so a packet is parsed exactly as that simulator reads it. The length header
 * and TPDU switches say what precedes the message in the pasted hex.
 */
@Composable
fun MessageParserScreen(
    navigationController: NavigationController,
    onError: ResultDialogInterface,
    onBack: () -> Unit,
    hostConfigs: List<GatewayConfig> = emptyList(),
) {
    val formats = remember(hostConfigs) {
        listOf(
            ParserFormat(
                "Standard ISO 8583",
                GatewayConfig(id = "iso8583-parser-standard", name = "Standard").apply {
                    bitTemplateSource = BitTemplate.getTemplate_Standard()
                },
            )
        ) + hostConfigs.map { ParserFormat("Host Simulator: ${it.name}", it) }
    }
    var format by remember(formats) { mutableStateOf(formats.first()) }
    var lengthHeader by remember(format) { mutableStateOf(format.config.messageLengthTypeSource.takeIf { it != MessageLengthType.STRING_4 } ?: MessageLengthType.HEX_HL) }
    var hasTpdu by remember(format) { mutableStateOf(!format.config.doNotUseHeaderSource) }
    var expanded by remember { mutableStateOf(false) }

    // The parser reads with a copy, so the switches never change a saved simulator.
    val effective = remember(format, lengthHeader, hasTpdu) {
        format.config.copy(messageLengthTypeSource = lengthHeader, doNotUseHeaderSource = !hasTpdu).apply {
            bitTemplateSource = format.config.bitTemplateSource
        }
    }

    // Parser view state, as the Host Simulator kept it.
    val selectedField = remember { mutableStateOf<BitAttribute?>(null) }
    val selectedFieldIndex = remember { mutableStateOf<Int?>(null) }
    // Opened from the Bitmap button once a packet is parsed, as in the Host Simulator.
    val showBitmapAnalysis = remember { mutableStateOf(false) }
    val showMessageParser = remember { mutableStateOf(true) }
    val isFirst = remember { mutableStateOf(true) }
    val animationTrigger = remember { mutableStateOf(0) }
    val rawMessage = remember { mutableStateOf("") }
    val parseError = remember { mutableStateOf<String?>(null) }
    val currentFields = remember { mutableStateOf<Array<BitAttribute>?>(null) }
    val currentBitmap = remember { mutableStateOf<ByteArray?>(null) }
    val searchQuery = remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        Column(
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text("ISO8583 parser", style = MaterialTheme.typography.h5)
                Text(
                    "Paste a packet as hex and see every field decoded, with the bitmap, chip data, entry mode and Track 2 explained.",
                    color = posMuted(),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            PosCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Format", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Box(Modifier.padding(top = 6.dp)) {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(format.label, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                formats.forEach { f ->
                                    DropdownMenuItem(onClick = { format = f; expanded = false }) { Text(f.label) }
                                }
                            }
                        }
                        Text(
                            "A packet is read exactly as that simulator reads it. These switches never change a saved simulator.",
                            color = posMuted(),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Column(Modifier.weight(2f)) {
                        Text("Length header in the pasted hex", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        SegmentedChoice(
                            options = listOf(MessageLengthType.HEX_HL, MessageLengthType.HEX_LH, MessageLengthType.BCD, MessageLengthType.NONE),
                            selected = lengthHeader,
                            label = { if (it == MessageLengthType.NONE) "None" else it.name },
                            onSelect = { lengthHeader = it },
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            when (lengthHeader) {
                                MessageLengthType.HEX_HL -> "Two bytes, high byte first. A 207-byte packet starts with 00 CF."
                                MessageLengthType.HEX_LH -> "Two bytes, low byte first. A 207-byte packet starts with CF 00."
                                MessageLengthType.BCD -> "Two bytes of decimal digits. A 207-byte packet starts with 02 07."
                                else -> "No length in front. The hex starts at the header or the message type."
                            },
                            color = posMuted(),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Checkbox(checked = hasTpdu, onCheckedChange = { hasTpdu = it })
                            Text("The packet starts with a header (TPDU, 5 bytes)", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        // Re-created when the format changes, so the pasted packet is parsed again with it.
        key(effective) {
            UnsolicitedMessageTab(
                config = effective,
                selectedField = selectedField,
                selectedFieldIndex = selectedFieldIndex,
                showBitmapAnalysis = showBitmapAnalysis,
                showMessageParser = showMessageParser,
                isFirst = isFirst,
                animationTrigger = animationTrigger,
                rawMessage = rawMessage,
                parseError = parseError,
                currentFields = currentFields,
                currentBitmap = currentBitmap,
                searchQuery = searchQuery,
                modifier = Modifier.weight(1f).padding(horizontal = 32.dp),
            )
        }
    }
}
