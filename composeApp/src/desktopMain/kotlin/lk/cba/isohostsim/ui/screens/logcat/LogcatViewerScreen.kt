package lk.cba.isohostsim.ui.screens.logcat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lk.cba.isohostsim.domain.service.logcat.AdbDevice
import lk.cba.isohostsim.domain.service.logcat.AdbLogcatSession
import lk.cba.isohostsim.domain.service.logcat.AdbTool
import lk.cba.isohostsim.domain.service.logcat.LogcatStreamState
import lk.cba.isohostsim.ui.ErrorRed
import lk.cba.isohostsim.ui.SuccessGreen
import lk.cba.isohostsim.ui.screens.components.AppBarWithBack
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import lk.cba.isohostsim.ui.design.PosButton
import lk.cba.isohostsim.ui.design.PosButtonKind
import lk.cba.isohostsim.ui.design.posMuted

/**
 * The viewer's stream and query state live outside the composable, so streaming carries on while
 * the user switches to another tab and the view comes back as they left it. The adb process is
 * registered with the app's process registry and is killed when the app exits.
 */
object LogcatViewerHost {
    const val BUFFER_SIZE = 20_000
    val session = AdbLogcatSession(maxEntries = BUFFER_SIZE)
    val state = LogcatState()
}

/**
 * Logcat for a connected Android terminal, read over adb (USB or `adb connect` over Wi-Fi). The
 * view, query language and colours are the same as Android Studio's Logcat.
 */
@Composable
fun LogcatViewerScreen(onBack: () -> Unit) {
    val session = LogcatViewerHost.session
    val entries by session.entries.collectAsState()
    val streamState by session.state.collectAsState()
    val current by session.device.collectAsState()
    val error by session.lastError.collectAsState()
    val scope = rememberCoroutineScope()

    var devices by remember { mutableStateOf<List<AdbDevice>>(emptyList()) }
    var selected by remember { mutableStateOf<AdbDevice?>(current) }
    var loading by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    fun refresh() = scope.launch {
        loading = true
        devices = AdbTool.devices()
        if (selected == null || devices.none { it.serial == selected?.serial }) {
            selected = devices.firstOrNull { it.isOnline } ?: devices.firstOrNull()
        }
        loading = false
    }
    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        Column(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 4.dp)) {
            Text("Logcat", style = MaterialTheme.typography.h5)
            Text(
                "Read the terminal's log over USB. Search and filter while it streams.",
                color = posMuted(),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.widthIn(min = 260.dp)) {
                        Text(
                            selected?.let { "${it.label}${if (it.isOnline) "" else " (${it.state})"}" }
                                ?: if (loading) "Looking for devices…" else "No device found",
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        if (devices.isEmpty()) {
                            DropdownMenuItem(onClick = { expanded = false }) { Text("No devices. Connect one by USB or run adb connect.") }
                        }
                        devices.forEach { d ->
                            DropdownMenuItem(onClick = { selected = d; expanded = false }) {
                                Text("${d.label}${if (d.isOnline) "" else " (${d.state})"}")
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { refresh() }) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                }
                val running = streamState == LogcatStreamState.RUNNING
                PosButton(
                    if (running) "Stop" else "Start",
                    kind = if (running) PosButtonKind.Danger else PosButtonKind.Primary,
                    enabled = running || selected?.isOnline == true,
                    onClick = { if (running) session.stop() else selected?.let { session.start(it) } },
                )
                PosButton(
                    "Clear device log",
                    kind = PosButtonKind.Secondary,
                    enabled = current != null,
                    onClick = { scope.launch { session.clearDevice() } },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when (streamState) {
                        LogcatStreamState.RUNNING -> "Streaming ${current?.label.orEmpty()} · ${entries.size} lines"
                        LogcatStreamState.ENDED -> "Stream ended"
                        LogcatStreamState.STOPPED -> if (entries.isEmpty()) "Pick a device and press Start" else "Stopped · ${entries.size} lines"
                    },
                    color = if (streamState == LogcatStreamState.RUNNING) SuccessGreen else MaterialTheme.colors.onSurface,
                    style = MaterialTheme.typography.body2,
                )
            }
            error?.let {
                Text(
                    it,
                    color = ErrorRed,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
            LogcatTab(
                state = LogcatViewerHost.state,
                entries = entries,
                packageName = "",
                bufferSize = LogcatViewerHost.BUFFER_SIZE,
                onClear = session::clearView,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 32.dp, end = 32.dp, bottom = 24.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}
