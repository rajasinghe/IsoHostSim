package lk.cba.isohostsim.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lk.cba.isohostsim.data.model.StudioTool
import lk.cba.isohostsim.edition.EnabledTools
import lk.cba.isohostsim.ui.design.Pos
import lk.cba.isohostsim.ui.navigation.stateConfigs.SimulatorType
import lk.cba.isohostsim.ui.session.SimulatorSessionManager
import java.net.Inet4Address
import java.net.NetworkInterface

private data class NavItem(val label: String, val icon: ImageVector, val tool: StudioTool)

private val NAV_ITEMS = listOf(
    NavItem("Host simulator", Icons.Default.Dns, StudioTool.HOST_SIMULATOR),
    NavItem("Remote host", Icons.Default.Cloud, StudioTool.REMOTE_HOST),
    NavItem("ECR simulator", Icons.Default.PointOfSale, StudioTool.ECR_SIMULATOR),
    NavItem("ISO8583 parser", Icons.Default.Search, StudioTool.MESSAGE_PARSER),
    NavItem("Logcat", Icons.Default.Terminal, StudioTool.LOGCAT_VIEWER),
)

/** One address of this computer that a terminal could connect to. */
private data class LocalAddress(val iface: String, val ip: String)

private fun localAddresses(): List<LocalAddress> = try {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { runCatching { it.isUp && !it.isLoopback && !it.isVirtual }.getOrDefault(false) }
        .flatMap { nic ->
            nic.inetAddresses.toList().filterIsInstance<Inet4Address>()
                .filter { !it.isLinkLocalAddress }
                .map { LocalAddress(nic.name, it.hostAddress ?: "") }
        }
        .filter { it.ip.isNotBlank() }
        .take(3)
} catch (_: Exception) {
    emptyList()
}

/**
 * The navigation sidebar: brand, the tools this build ships, this computer's addresses and a menu
 * with configuration import and export, settings, about and exit.
 */
@Composable
fun AppSidebar(
    onHome: () -> Unit,
    onOpenTool: (StudioTool) -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeId = SimulatorSessionManager.activeSessionId.value
    val active = SimulatorSessionManager.sessions.find { it.id == activeId }
    val activeTool: StudioTool? = when {
        active == null -> null
        active.studioTool != null -> active.studioTool
        active.simulatorType == SimulatorType.HOST -> StudioTool.HOST_SIMULATOR
        active.simulatorType == SimulatorType.ECR -> StudioTool.ECR_SIMULATOR
        else -> null
    }
    val addresses = remember { localAddresses() }

    Column(
        modifier = modifier
            .width(232.dp)
            .fillMaxHeight()
            .background(Pos.Sidebar)
            .padding(horizontal = 12.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Brand()
        Text(
            "Tools",
            color = Pos.SidebarMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        )
        NavRow("Home", Icons.Default.Home, selected = active == null, onClick = onHome)
        NAV_ITEMS.filter { EnabledTools.isEnabled(it.tool) }.forEach { item ->
            NavRow(item.label, item.icon, selected = activeTool == item.tool, onClick = { onOpenTool(item.tool) })
        }

        Spacer(Modifier.weight(1f))

        if (addresses.isNotEmpty()) NetworkCard(addresses)
        MoreMenu(onExportConfig, onImportConfig, onSettings, onAbout, onExit)
    }
}

@Composable
private fun Brand() {
    Row(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Pos.Indigo),
            contentAlignment = Alignment.Center,
        ) {
            Text("POS", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Column {
            Text(
                "POS Testing Platform",
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text("Terminal test tools", color = Pos.SidebarMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun NavRow(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        selected -> Pos.SidebarActive
        hovered -> Pos.SidebarCard
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .hoverable(interaction)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Color.White else Pos.SidebarText, modifier = Modifier.size(18.dp))
        Text(
            label,
            color = if (selected) Color.White else Pos.SidebarText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun NetworkCard(addresses: List<LocalAddress>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Pos.SidebarCard)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("This computer", color = Pos.SidebarMuted, fontSize = 12.sp)
        addresses.forEach { a ->
            Column {
                Text(a.ip, color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                Text(a.iface, color = Pos.SidebarMuted, fontSize = 11.sp)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun MoreMenu(
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onExit: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        NavRow("More", Icons.Default.MoreHoriz, selected = false, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(onClick = { open = false; onExportConfig() }) { Text("Export configuration") }
            DropdownMenuItem(onClick = { open = false; onImportConfig() }) { Text("Import configuration") }
            DropdownMenuItem(onClick = { open = false; onSettings() }) { Text("Settings") }
            DropdownMenuItem(onClick = { open = false; onAbout() }) { Text("About") }
            DropdownMenuItem(onClick = { open = false; onExit() }) { Text("Exit") }
        }
    }
}
