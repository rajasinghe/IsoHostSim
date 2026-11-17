package lk.cba.isohostsim.ui.screens.hostSimulator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import lk.cba.isohostsim.data.StudioPaths
import lk.cba.isohostsim.data.model.GatewayConfig
import lk.cba.isohostsim.domain.service.hostSimulatorService.ResponseControl
import lk.cba.isohostsim.domain.service.hostSimulatorService.ResponseMode
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.HostProfileLoader
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.ProfileResult
import lk.cba.isohostsim.ui.screens.components.FixedOutlinedTextField
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import lk.cba.isohostsim.ui.design.PosButton
import lk.cba.isohostsim.ui.design.PosButtonKind
import lk.cba.isohostsim.ui.design.PosCard
import lk.cba.isohostsim.ui.design.SegmentedChoice
import lk.cba.isohostsim.ui.design.StatusChip
import lk.cba.isohostsim.ui.design.Tone
import lk.cba.isohostsim.ui.design.posLine
import lk.cba.isohostsim.ui.design.posMuted
import lk.cba.isohostsim.ui.design.posSunken
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.semantics.Role

/**
 * The QA controls above the Host Simulator's tabs.
 *
 * Response: answer normally, never answer, or answer after a delay. Applies from the next request,
 * while the gateway runs.
 *
 * Profile: the JSON file this gateway runs from. Saving the file reloads it on the fly; a file with
 * problems is not applied, and the problems are listed here.
 */
@Composable
fun HostQaBar(config: GatewayConfig, onSaveClick: () -> Unit) {
    val control = remember(config.id) { ResponseControl.of(config.id) }
    var mode by remember(config.id) { mutableStateOf(control.mode) }
    var delayText by remember(config.id) { mutableStateOf(control.delaySeconds.toString()) }

    var profilePath by remember(config.id) { mutableStateOf(config.profilePath) }
    var status by remember(config.id) { mutableStateOf<String?>(null) }
    var errors by remember(config.id) { mutableStateOf<List<String>>(emptyList()) }
    // Folded to one slim line by default, so the request and response panes keep the height.
    var expanded by remember(config.id) { mutableStateOf(false) }
    val clock = remember { SimpleDateFormat("HH:mm:ss") }

    fun reload(file: File) {
        when (val result = HostProfileLoader.read(file)) {
            is ProfileResult.Ok -> {
                val oldAddress = config.serverAddress to config.serverPort
                HostProfileLoader.applyTo(config, result.profile, file.absolutePath)
                onSaveClick()
                errors = emptyList()
                val restart = oldAddress != (config.serverAddress to config.serverPort)
                status = "Loaded at ${clock.format(Date())}" +
                    if (restart) " · address or port changed: stop and start the gateway" else ""
            }
            is ProfileResult.Invalid -> {
                errors = result.errors
                status = "Not applied: ${result.errors.size} problem(s) in the file. The gateway keeps its last good profile."
            }
        }
    }

    // Reload when the file is saved, so QA can edit it in any editor.
    LaunchedEffect(profilePath) {
        val file = profilePath?.let(::File) ?: return@LaunchedEffect
        var seen = withContext(Dispatchers.IO) { file.lastModified() }
        while (true) {
            delay(1_000)
            val now = withContext(Dispatchers.IO) { file.lastModified() }
            if (now != seen) {
                seen = now
                reload(file)
            }
        }
    }

    val showFull = expanded || errors.isNotEmpty()
    Box(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 4.dp)) {
    if (!showFull) {
        val shape = RoundedCornerShape(10.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colors.surface)
                .border(1.dp, posLine(), shape)
                .clickable(role = Role.Button) { expanded = true }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Response", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            StatusChip(mode.label, if (mode == ResponseMode.NORMAL) Tone.Neutral else Tone.Warning)
            Text("Profile", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                profilePath?.let { File(it).name } ?: "not linked",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = posMuted(),
                modifier = Modifier.weight(1f, fill = false),
            )
            if (profilePath != null) StatusChip("Loaded", Tone.Success, showDot = true)
            Spacer(Modifier.weight(1f))
            Text("Show", color = MaterialTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colors.primary, modifier = Modifier.size(18.dp))
        }
    } else
    PosCard(Modifier.fillMaxWidth(), padding = 16.dp) {
        // Response mode
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Response", fontWeight = FontWeight.SemiBold)
            SegmentedChoice(
                options = ResponseMode.entries.toList(),
                selected = mode,
                label = { it.label },
                onSelect = { mode = it; control.mode = it },
                modifier = Modifier.widthIn(max = 520.dp),
            )
            if (mode == ResponseMode.DELAY) {
                Text("Wait", color = posMuted(), fontSize = 13.sp)
                FixedOutlinedTextField(
                    value = delayText,
                    onValueChange = { v ->
                        delayText = v.filter { it.isDigit() }.take(4)
                        delayText.toIntOrNull()?.let { control.delaySeconds = it }
                    },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text("seconds", color = posMuted(), fontSize = 13.sp)
            }
        }
        Text(
            when (mode) {
                ResponseMode.NORMAL -> "Replies go out straight away."
                ResponseMode.NO_RESPONSE ->
                    "Replies are never sent. The connection stays open, so the terminal times out. Use this to test reversals."
                else -> "Replies go out after ${delayText.ifBlank { "0" }} seconds. You can change the number at any time."
            },
            color = posMuted(),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(posLine()))

        // Profile file
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Profile", fontWeight = FontWeight.SemiBold)
            val path = profilePath
            if (path != null) {
                Text(
                    path,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(6.dp))
                        .background(posSunken())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                if (errors.isEmpty() && status != null) {
                    StatusChip(status!!, Tone.Success, showDot = true)
                } else if (errors.isEmpty()) {
                    StatusChip("Loaded", Tone.Success, showDot = true)
                }
                Spacer(Modifier.weight(1f))
                PosButton("Reload", onClick = { reload(File(path)) }, kind = PosButtonKind.Secondary)
            } else {
                Text("Not linked to a JSON profile", color = posMuted(), fontSize = 13.sp, modifier = Modifier.weight(1f))
                PosButton(
                    "Export to JSON profile",
                    kind = PosButtonKind.Secondary,
                    onClick = {
                        val file = File(StudioPaths.hostsDir, "${safeName(config.name)}.json")
                        HostProfileLoader.write(HostProfileLoader.fromGatewayConfig(config), file)
                        HostProfileLoader.writeSchema(StudioPaths.hostsDir)
                        config.profilePath = file.absolutePath
                        onSaveClick()
                        profilePath = file.absolutePath
                        status = "Exported. Edit the file and save it; changes load automatically."
                    },
                )
            }
            Text(
                "Hide",
                color = MaterialTheme.colors.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(role = Role.Button) { expanded = false }.padding(4.dp),
            )
        }
        if (errors.isNotEmpty()) {
            status?.let { Text(it, color = MaterialTheme.colors.error, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            Column(Modifier.padding(top = 4.dp)) {
                errors.take(8).forEach { Text("• $it", color = MaterialTheme.colors.error, fontSize = 12.sp) }
                if (errors.size > 8) Text("…and ${errors.size - 8} more", color = MaterialTheme.colors.error, fontSize = 12.sp)
            }
        }
    }
    }
}

internal fun safeName(name: String): String =
    name.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifEmpty { "host-profile" }
