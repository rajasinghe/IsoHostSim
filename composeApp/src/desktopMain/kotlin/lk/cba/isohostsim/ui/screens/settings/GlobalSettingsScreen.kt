package lk.cba.isohostsim.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lk.cba.isohostsim.analytics.StudioAnalytics
import lk.cba.isohostsim.data.model.AnalyticsConsent
import lk.cba.isohostsim.data.model.AppSettings
import lk.cba.isohostsim.ui.screens.components.AppBarWithBack
import lk.cba.isohostsim.ui.screens.components.Panel
import lk.cba.isohostsim.ui.screens.components.SectionHeader
import lk.cba.isohostsim.ui.screens.components.StyledTextField
import lk.cba.isohostsim.BuildInfo //gimhan added
import lk.cba.isohostsim.update.UpdateStatusRow //gimhan added

@Composable
fun GlobalSettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            AppBarWithBack(
                title = "Settings",
                onBackClick = onBack
            )
        },
        backgroundColor = MaterialTheme.colors.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Global Logging section
            Panel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SectionHeader(title = "Global Logging")

                    Text(
                        "These settings apply to all simulators (Host, HSM, POS, APDU). " +
                                "When the in-memory log buffer grows large, it can slow down the UI. " +
                                "Use these options to control logging behaviour globally.",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )

                    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.1f))

                    // Enable / disable global logging
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Global Logging", fontWeight = FontWeight.Medium)
                            Text(
                                "When disabled, logs will not be written to the Logs tab or the log file. " +
                                        "Disabling this can significantly improve performance under heavy load.",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = AppSettings.enableGlobalLogging,
                            onCheckedChange = { AppSettings.updateEnableGlobalLogging(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colors.primary)
                        )
                    }
                }
            }

            // Auto-clear section
            Panel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SectionHeader(title = "Auto-Clear Logs")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Auto-Clear", fontWeight = FontWeight.Medium)
                            Text(
                                "Automatically clear the in-memory log buffer at a regular interval " +
                                        "to prevent performance degradation.",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = AppSettings.autoClearLogsEnabled,
                            onCheckedChange = { AppSettings.updateAutoClearLogsEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colors.primary)
                        )
                    }

                    if (AppSettings.autoClearLogsEnabled) {
                        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.1f))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Clear interval (minutes)",
                                modifier = Modifier.width(180.dp),
                                fontWeight = FontWeight.Medium
                            )
                            StyledTextField(
                                value = AppSettings.autoClearLogsIntervalMinutes.toString(),
                                onValueChange = {
                                    val minutes = it.toIntOrNull() ?: return@StyledTextField
                                    AppSettings.updateAutoClearLogsIntervalMinutes(minutes)
                                },
                                modifier = Modifier.width(100.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Delete log file on auto-clear", fontWeight = FontWeight.Medium)
                                Text(
                                    "Also delete the log file and rotated log files from disk when " +
                                            "auto-clearing, to free storage space. Log views scroll " +
                                            "through these files, so deleting them discards the " +
                                            "history they show.",
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Switch(
                                checked = AppSettings.deleteLogFileOnClear,
                                onCheckedChange = { AppSettings.updateDeleteLogFileOnClear(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colors.primary)
                            )
                        }
                    }

                    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Live log entries in memory", fontWeight = FontWeight.Medium)
                            Text(
                                "How many entries a log view holds in memory. This is only a display " +
                                        "window \u2014 older entries stay on disk and remain scrollable, " +
                                        "so raising it costs memory without adding history.",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        StyledTextField(
                            value = AppSettings.maxLiveLogEntries.toString(),
                            onValueChange = {
                                val entries = it.toIntOrNull() ?: return@StyledTextField
                                AppSettings.updateMaxLiveLogEntries(entries)
                            },
                            modifier = Modifier.width(100.dp)
                        )
                    }
                }
            }

            // Usage Analytics section
            Panel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SectionHeader(title = "Usage Analytics")

                    Text(
                        "Anonymous statistics about which tools and simulators you use, plus your " +
                                "app version, operating system and approximate location. Card data, PINs, " +
                                "keys, cryptograms and message contents are never collected and never leave " +
                                "this machine.",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )

                    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Share anonymous usage analytics", fontWeight = FontWeight.Medium)
                            Text(
                                "Turning this off stops all reporting immediately.",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = AppSettings.analyticsConsent.isGranted,
                            onCheckedChange = { granted ->
                                AppSettings.updateAnalyticsConsent(
                                    if (granted) AnalyticsConsent.GRANTED else AnalyticsConsent.DENIED
                                )
                                if (granted) StudioAnalytics.init() else StudioAnalytics.setEnabled(false)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colors.primary)
                        )
                    }

                    if (AppSettings.analyticsConsent.isGranted) {
                        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.1f))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Anonymous device ID", fontWeight = FontWeight.Medium)
                                Text(
                                    AppSettings.analyticsClientId.ifBlank { "not yet assigned" },
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            OutlinedButton(onClick = { StudioAnalytics.resetIdentity() }) {
                                Text("Reset")
                            }
                        }
                    }
                }
            }

            // Updates section //gimhan added
            Panel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SectionHeader(title = "Updates")

                    Text(
                        "New versions are published to the update server. The app checks it " +
                                "at start-up and offers to install anything newer.",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )

                    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.1f))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Update location", fontWeight = FontWeight.Medium)
                        Text(
                            "An address such as http://20.1.2.3/iso8583studio, or a folder. " +
                                    "Leave blank to use the built-in location" +
                                    (BuildInfo.UPDATE_SOURCE.takeIf { it.isNotBlank() }?.let { " ($it)." } ?: "."),
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                        StyledTextField(
                            value = AppSettings.updateSource,
                            onValueChange = { AppSettings.updateUpdateSource(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    UpdateStatusRow(modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
