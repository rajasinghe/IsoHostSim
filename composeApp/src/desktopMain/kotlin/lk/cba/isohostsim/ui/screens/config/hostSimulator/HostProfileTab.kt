package lk.cba.isohostsim.ui.screens.config.hostSimulator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lk.cba.isohostsim.data.StudioPaths
import lk.cba.isohostsim.data.model.GatewayConfig
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.HostProfileLoader
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.HostProfileStarter
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.ProfileResult
import lk.cba.isohostsim.ui.ErrorRed
import lk.cba.isohostsim.ui.SuccessGreen
import lk.cba.isohostsim.ui.navigation.stateConfigs.UnifiedSimulatorState
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import lk.cba.isohostsim.ui.design.NoteBox
import lk.cba.isohostsim.ui.design.PosButton
import lk.cba.isohostsim.ui.design.PosButtonKind
import lk.cba.isohostsim.ui.design.PosCard
import lk.cba.isohostsim.ui.design.SectionTitle
import lk.cba.isohostsim.ui.design.StatusChip
import lk.cba.isohostsim.ui.design.Tone
import lk.cba.isohostsim.ui.design.posLine
import lk.cba.isohostsim.ui.design.posMuted
import lk.cba.isohostsim.ui.design.posSunken

/**
 * The only configuration view for a Host Simulator: its JSON profile. Everything the old Gateway,
 * Transmission and Log tabs set is in the file; this shows what the file sets up and lets QA open,
 * reload or (for an older gateway) export it.
 */
@Composable
fun HostProfileTab(config: GatewayConfig, appState: UnifiedSimulatorState) {
    var message by remember(config.id) { mutableStateOf<Pair<Boolean, String>?>(null) }
    val path = config.profilePath

    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PosCard(Modifier.fillMaxWidth(), padding = 20.dp) {
            if (path == null) {
                SectionTitle(
                    "This gateway isn't linked to a JSON profile.",
                    "Export it to a profile file to edit its settings. The file then becomes its configuration.",
                )
                Spacer(Modifier.height(12.dp))
                PosButton("Export to JSON profile", onClick = {
                    val file = File(StudioPaths.hostsDir, "${safeFileName(config.name)}.json")
                    HostProfileLoader.write(HostProfileLoader.fromGatewayConfig(config), file)
                    HostProfileLoader.writeSchema(StudioPaths.hostsDir)
                    config.profilePath = file.absolutePath
                    appState.updateConfig(config.copy(modifiedDate = System.currentTimeMillis()))
                    appState.save()
                    message = true to "Exported to ${file.absolutePath}"
                })
            } else {
                SectionTitle(
                    "Profile file",
                    "This simulator is its profile. Edit the file and the running gateway picks up the change.",
                )
                Row(
                    Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SelectionContainer {
                        Text(
                            path,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(posSunken())
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    StatusChip("Linked", Tone.Success, showDot = true)
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PosButton("Open in editor", onClick = { openInEditor(File(path)) })
                    PosButton("Reload from file", kind = PosButtonKind.Secondary, onClick = {
                        message = reloadInto(config, File(path), appState)
                    })
                }
                Text(
                    "Edit the file and save it. A running gateway reloads it by itself; this button updates the saved gateway.",
                    color = posMuted(),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        message?.let { (ok, text) -> NoteBox(text, if (ok) Tone.Success else Tone.Danger) }

        PosCard(Modifier.fillMaxWidth(), padding = 20.dp) {
            SectionTitle("What it runs")
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Summary("Type", "${config.gatewayType} · ${config.transmissionType}")
                Summary("Listens on", "${config.serverConnectionType} ${config.serverAddress.ifBlank { "0.0.0.0" }}:${config.serverPort}")
                Summary("Framing", "${config.messageLengthTypeSource} length · TPDU " + when {
                    config.doNotUseHeaderSource -> "none"
                    config.swapResponseTpdu -> "swapped from the request"
                    else -> config.fixedResponseHeaderSource?.joinToString("") { "%02X".format(it) }?.ifEmpty { null } ?: "60 00 00 00 00"
                })
                Summary("Unmatched", config.unmatchedResponseCode?.let { "declined with $it" } ?: "connection closed")
                Summary("Log file", config.logFileName)
            }
            Text("Transactions", style = MaterialTheme.typography.subtitle1, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
            config.simulatedTransactionsToSource.forEach { t ->
                val fields = t.fields.orEmpty().indices.filter { t.fields!![it].isSet }.map { it + 1 }
                Box(Modifier.fillMaxWidth().height(1.dp).background(posLine()))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(t.description, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(140.dp))
                    Text(
                        "${t.mti.padStart(4, '0')} · ${t.proCode}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.width(150.dp),
                    )
                    Text("fields ${fields.joinToString(" ")}", color = posMuted(), fontSize = 13.sp)
                }
            }
            if (config.simulatedTransactionsToSource.isEmpty()) Text("None", color = posMuted(), fontSize = 13.sp)
        }
    }
}

@Composable
private fun Summary(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, color = posMuted(), fontSize = 13.sp, modifier = Modifier.width(130.dp))
        Text(value, fontSize = 14.sp)
    }
}

private fun reloadInto(config: GatewayConfig, file: File, appState: UnifiedSimulatorState): Pair<Boolean, String> =
    when (val result = HostProfileLoader.read(file)) {
        is ProfileResult.Ok -> {
            HostProfileLoader.applyTo(config, result.profile, file.absolutePath)
            appState.updateConfig(config.copy(modifiedDate = System.currentTimeMillis()))
            appState.save()
            true to "Reloaded from ${file.name}"
        }
        is ProfileResult.Invalid -> false to "Not reloaded:\n" + result.errors.joinToString("\n") { "• $it" }
    }

/** Picks a JSON profile and adds it as a gateway, or updates the gateway already linked to it. */
fun addFromJsonProfile(appState: UnifiedSimulatorState) {
    StudioPaths.hostsDir.mkdirs()
    HostProfileLoader.writeSchema(StudioPaths.hostsDir)
    val chooser = JFileChooser(StudioPaths.hostsDir).apply {
        dialogTitle = "Add a Host Simulator from a JSON profile"
        fileFilter = FileNameExtensionFilter("Host profiles (*.json)", "json")
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
    loadFile(chooser.selectedFile, appState)
}

/** Writes a starter profile, opens it in the user's editor and adds it as a gateway. */
fun createStarterProfile(appState: UnifiedSimulatorState) {
    val name = JOptionPane.showInputDialog(null, "Name for the new Host Simulator:", "New profile", JOptionPane.PLAIN_MESSAGE)
        ?.trim()?.takeIf { it.isNotEmpty() } ?: return
    StudioPaths.hostsDir.mkdirs()
    HostProfileLoader.writeSchema(StudioPaths.hostsDir)
    var file = File(StudioPaths.hostsDir, "${safeFileName(name)}.json")
    var n = 2
    while (file.exists()) file = File(StudioPaths.hostsDir, "${safeFileName(name)}-${n++}.json")
    file.writeText(HostProfileStarter.text(name))
    loadFile(file, appState)
    openInEditor(file)
}

private fun loadFile(file: File, appState: UnifiedSimulatorState) {
    when (val result = HostProfileLoader.read(file)) {
        is ProfileResult.Invalid -> JOptionPane.showMessageDialog(
            null,
            "The profile was not loaded:\n\n" + result.errors.joinToString("\n") { "• $it" },
            "Problems in ${file.name}",
            JOptionPane.ERROR_MESSAGE,
        )
        is ProfileResult.Ok -> {
            val existing = appState.hostConfigs.value.firstOrNull { it.profilePath == file.absolutePath }
            if (existing != null) {
                HostProfileLoader.applyTo(existing, result.profile, file.absolutePath)
                appState.updateConfig(existing.copy(modifiedDate = System.currentTimeMillis()))
                appState.selectConfig(existing.id)
            } else {
                val config = HostProfileLoader.toGatewayConfig(result.profile, appState.generateConfigId(), file.absolutePath)
                appState.addConfig(config)
                appState.selectConfig(config.id)
            }
            appState.save()
        }
    }
}

/** Opens [file] in the system's editor for JSON, falling back to xdg-open on Linux. */
fun openInEditor(file: File) {
    val opened = runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(file); true
        } else false
    }.getOrDefault(false)
    if (!opened) runCatching { ProcessBuilder("xdg-open", file.absolutePath).start() }
}

internal fun safeFileName(name: String): String =
    name.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifEmpty { "host-profile" }
