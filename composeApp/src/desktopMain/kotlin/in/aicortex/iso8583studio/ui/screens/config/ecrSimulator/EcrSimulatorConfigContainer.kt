package `in`.aicortex.iso8583studio.ui.screens.config.ecrSimulator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fazecast.jSerialComm.SerialPort
import `in`.aicortex.iso8583studio.domain.service.ecr.EcrCommand
import `in`.aicortex.iso8583studio.domain.service.ecr.EcrLibraryClient
import `in`.aicortex.iso8583studio.domain.service.ecr.EcrResult
import `in`.aicortex.iso8583studio.domain.service.ecr.EcrTarget
import `in`.aicortex.iso8583studio.ui.ErrorRed
import `in`.aicortex.iso8583studio.ui.SuccessGreen
import `in`.aicortex.iso8583studio.ui.navigation.stateConfigs.ecr.EcrSimulatorConfig
import `in`.aicortex.iso8583studio.ui.navigation.stateConfigs.ecr.EcrTransport
import `in`.aicortex.iso8583studio.ui.screens.components.FixedOutlinedTextField
import kotlinx.coroutines.launch

/** Transport, terminal address, USB port, timeout and encryption, plus a Check Status test. */
@Composable
fun EcrConnectionTab(config: EcrSimulatorConfig, onConfigChange: (EcrSimulatorConfig) -> Unit) {
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<EcrResult?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Section("Profile") {
            FixedOutlinedTextField(
                value = config.name,
                onValueChange = { onConfigChange(config.copy(name = it)) },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            FixedOutlinedTextField(
                value = config.description,
                onValueChange = { onConfigChange(config.copy(description = it)) },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        Section("Terminal connection") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EcrTransport.entries.forEach { transport ->
                    RadioButton(
                        selected = config.transport == transport,
                        onClick = { onConfigChange(config.copy(transport = transport)) },
                    )
                    Text(transport.label, modifier = Modifier.padding(end = 24.dp))
                }
            }
            when (config.transport) {
                EcrTransport.TCP -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FixedOutlinedTextField(
                        value = config.terminalIp,
                        onValueChange = { onConfigChange(config.copy(terminalIp = it.trim())) },
                        label = { Text("Terminal IP") },
                        modifier = Modifier.weight(2f),
                        singleLine = true,
                    )
                    FixedOutlinedTextField(
                        value = config.port.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { onConfigChange(config.copy(port = it)) } },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                EcrTransport.USB -> UsbPortPicker(
                    selected = config.usbPortOverride,
                    onSelected = { onConfigChange(config.copy(usbPortOverride = it)) },
                )
            }
            Text(
                when (config.transport) {
                    EcrTransport.TCP -> "Set the terminal to ECR mode TCP_IP and JSON. The HNB terminal app listens on port 5555."
                    EcrTransport.USB -> "Set the terminal to ECR mode USB and JSON. On Linux your user must be in the 'dialout' group."
                },
                style = MaterialTheme.typography.caption,
            )
        }

        Section("Timing and security") {
            FixedOutlinedTextField(
                value = config.timeoutSec.toString(),
                onValueChange = { v -> v.toIntOrNull()?.let { onConfigChange(config.copy(timeoutSec = it)) } },
                label = { Text("Result timeout (seconds)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = config.encrypted, onCheckedChange = { onConfigChange(config.copy(encrypted = it)) })
                Text("Encrypted replies (needs a key sent with Transfer Key first)")
            }
        }

        Section("Test") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = !testing,
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            val target = EcrTarget(config.transport, config.terminalIp, config.port, config.usbPortOverride)
                            testResult = EcrLibraryClient().execute(target, EcrCommand.CHECK_STATUS)
                            testing = false
                        }
                    },
                ) { Text("Test connection (Check Status)") }
                if (testing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            testResult?.let { result ->
                Text(
                    "${if (result.succeeded) "OK" else "Failed"}: ${result.message} (${result.durationMs} ms)",
                    color = if (result.succeeded) SuccessGreen else ErrorRed,
                    style = MaterialTheme.typography.body2,
                )
                Text(result.response, style = MaterialTheme.typography.caption)
            }
            if (config.transport == EcrTransport.USB) {
                Text(
                    "Known library issue: the library's USB Check Status always fails with NAK 17. Use Sale or Request Last to test USB.",
                    style = MaterialTheme.typography.caption,
                    color = ErrorRed,
                )
            }
        }
    }
}

/** Currency, detail, print, next ECR reference and the terminal's test password. */
@Composable
fun EcrDefaultsTab(config: EcrSimulatorConfig, onConfigChange: (EcrSimulatorConfig) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Section("Sale defaults") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FixedOutlinedTextField(
                    value = config.currency,
                    onValueChange = { onConfigChange(config.copy(currency = it.trim().uppercase())) },
                    label = { Text("Currency") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                YesNo("Detail", config.detail) { onConfigChange(config.copy(detail = it)) }
                YesNo("Print", config.print) { onConfigChange(config.copy(print = it)) }
            }
            FixedOutlinedTextField(
                value = config.nextEcrRef.toString(),
                onValueChange = { v -> v.toLongOrNull()?.let { onConfigChange(config.copy(nextEcrRef = it)) } },
                label = { Text("Next ECR reference") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Text(
                "Goes up by one after every approved sale. The terminal rejects a reused reference with E2.",
                style = MaterialTheme.typography.caption,
            )
        }
        Section("Terminal password") {
            FixedOutlinedTextField(
                value = config.testPassword,
                onValueChange = { onConfigChange(config.copy(testPassword = it)) },
                label = { Text("Test password for Void") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                "Use a QA terminal's test password only. It is saved in plain text with this profile.",
                style = MaterialTheme.typography.caption,
            )
        }
    }
}

@Composable
private fun UsbPortPicker(selected: String, onSelected: (String) -> Unit) {
    var ports by remember { mutableStateOf(listPorts()) }
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            FixedOutlinedTextField(
                value = selected.ifBlank { "Auto-detect the PAX port" },
                onValueChange = {},
                readOnly = true,
                label = { Text("USB port") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { expanded = !expanded }) { Icon(Icons.Default.ArrowDropDown, "Choose port") }
                },
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(onClick = { onSelected(""); expanded = false }) { Text("Auto-detect the PAX port") }
                ports.forEach { (name, description) ->
                    DropdownMenuItem(onClick = { onSelected(name); expanded = false }) {
                        Text("$name  —  $description")
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { ports = listPorts() }) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            Text("Refresh", modifier = Modifier.padding(start = 4.dp))
        }
    }
}

private fun listPorts(): List<Pair<String, String>> = runCatching {
    SerialPort.getCommPorts().map { it.systemPortName to it.portDescription.orEmpty() }
}.getOrDefault(emptyList())

@Composable
private fun YesNo(label: String, value: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = value == "Y", onCheckedChange = { onChange(if (it) "Y" else "N") })
        Text(label)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth(), elevation = 1.dp, backgroundColor = MaterialTheme.colors.surface) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
        }
    }
}
