package lk.cba.isohostsim.update

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

//gimhan added — in-app updates from an internal share.

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Checked(val result: UpdateService.CheckResult) : UpdateState
    /** [fraction] is null while the size is unknown. */
    data class Downloading(val pkg: UpdatePackage, val fraction: Float?) : UpdateState
    data class Installing(val pkg: UpdatePackage) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** App-wide update state, shared by the startup prompt, Settings and About. */
object UpdateController {
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /** Set when the user picks "Later", so the prompt stays away for the rest of this run. */
    var promptDismissed by mutableStateOf(false)

    private val busy get() = state is UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing

    /** A [userInitiated] check (Settings, About) brings the prompt back even after "Later". */
    fun check(scope: CoroutineScope, userInitiated: Boolean = true) {
        if (busy) return
        if (userInitiated) promptDismissed = false
        state = UpdateState.Checking
        scope.launch {
            val result = withContext(Dispatchers.IO) { UpdateService.check() }
            state = UpdateState.Checked(result)
        }
    }

    /**
     * The installer is waiting for this process to end. Closing the window normally ends it, but a
     * running simulator thread or a slow shutdown step can keep the JVM alive, leaving the old copy
     * on screen while the new one starts. Make sure it ends: a normal exit (shutdown hooks still stop
     * emulators) after a few seconds, and a hard stop if even that hangs.
     */
    private fun forceExitSoon() {
        fun after(ms: Long, name: String, action: () -> Unit) =
            Thread({ Thread.sleep(ms); action() }, name).apply { isDaemon = true }.start()
        after(5_000, "update-exit") { kotlin.system.exitProcess(0) }
        after(30_000, "update-halt") { Runtime.getRuntime().halt(0) }
    }

    /** Downloads, verifies and hands off to the installer, then calls [exitApp]. */
    fun install(scope: CoroutineScope, available: UpdateService.CheckResult.Available, exitApp: () -> Unit) {
        if (busy) return
        val pkg = available.pkg
        state = UpdateState.Downloading(pkg, null)
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    UpdateService.download(pkg) { copied, total ->
                        if (total > 0) state = UpdateState.Downloading(pkg, (copied.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
                state = UpdateState.Installing(pkg)
                withContext(Dispatchers.IO) { UpdateService.installAfterExit(file, available.kind) }
                forceExitSoon()
                exitApp()
            } catch (e: Exception) {
                state = UpdateState.Failed(e.message ?: e::class.simpleName.orEmpty())
            }
        }
    }
}

/**
 * The prompt shown when a startup check finds a newer version, and the progress of installing it.
 * Place it once in the main window.
 */
@Composable
fun UpdatePrompt(exitApp: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = UpdateController.state
    val available = (state as? UpdateState.Checked)?.result as? UpdateService.CheckResult.Available

    when {
        available != null && !UpdateController.promptDismissed -> AlertDialog(
            onDismissRequest = { UpdateController.promptDismissed = true },
            title = { Text("Update available", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Version ${available.pkg.version} is ready to install. You have ${UpdateService.currentVersion}.")
                    if (available.pkg.notes.isNotBlank()) {
                        Text(available.pkg.notes, style = MaterialTheme.typography.body2)
                    }
                    Text(
                        "The app closes, installs the update and opens again. Running simulators stop; " +
                            "your settings and saved configurations are kept.",
                        style = MaterialTheme.typography.caption,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { UpdateController.install(scope, available, exitApp) }) { Text("Update now") }
            },
            dismissButton = {
                TextButton(onClick = { UpdateController.promptDismissed = true }) { Text("Later") }
            },
        )

        state is UpdateState.Downloading || state is UpdateState.Installing -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Updating", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state is UpdateState.Downloading) {
                        Text("Copying version ${state.pkg.version}…")
                        val f = state.fraction
                        if (f != null) LinearProgressIndicator(progress = f, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        Text("Starting the installer. The app will close and reopen.")
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
        )
    }
}

/**
 * "Check for updates" button with the current status, for Settings and About. Finding an update
 * brings up [UpdatePrompt] in the main window, which does the installing.
 */
@Composable
fun UpdateStatusRow(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val state = UpdateController.state
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            statusText(state),
            style = MaterialTheme.typography.caption,
            color = if (state is UpdateState.Failed || (state as? UpdateState.Checked)?.result is UpdateService.CheckResult.Failed)
                MaterialTheme.colors.error else MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        OutlinedButton(
            onClick = { UpdateController.check(scope) },
            enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading && state !is UpdateState.Installing,
        ) { Text("Check for updates") }
    }
}

private fun statusText(state: UpdateState): String = when (state) {
    UpdateState.Idle -> "Version ${UpdateService.currentVersion}"
    UpdateState.Checking -> "Checking for updates…"
    is UpdateState.Downloading -> "Copying version ${state.pkg.version}…"
    is UpdateState.Installing -> "Starting the installer…"
    is UpdateState.Failed -> "Update failed: ${state.message}"
    is UpdateState.Checked -> when (val r = state.result) {
        UpdateService.CheckResult.NotConfigured -> "No update location set."
        UpdateService.CheckResult.UpToDate -> "You have the latest version (${UpdateService.currentVersion})."
        is UpdateService.CheckResult.Available -> "Version ${r.pkg.version} is available."
        is UpdateService.CheckResult.AvailableManualOnly ->
            "Version ${r.version} is available. This copy isn't installed from an installer, so update it manually."
        is UpdateService.CheckResult.Failed -> r.message
    }
}
