package lk.cba.isohostsim.web.chrome

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import lk.cba.isocore.ValidationResult
import lk.cba.isohostsim.ui.chrome.DesktopChrome
import lk.cba.isohostsim.ui.chrome.ToolChrome
import lk.cba.isohostsim.ui.chrome.ToolLog
import lk.cba.isohostsim.ui.chrome.ToolScreenSpec
import lk.cba.isohostsim.ui.chrome.ToolTab

/** The browser chrome: the site's glass panels. Pieces not yet replaced fall back to the desktop look. */
object GlassChrome : ToolChrome by DesktopChrome {
    override val isDesktop: Boolean get() = false

    @Composable
    override fun Screen(spec: ToolScreenSpec, content: @Composable () -> Unit) = GlassScreen(spec, content)

    @Composable
    override fun Tabs(tabs: List<ToolTab>, selected: Int, onSelected: (Int) -> Unit) = GlassTabs(tabs, selected, onSelected)

    @Composable
    override fun LogPanel(log: ToolLog, modifier: Modifier) = GlassLogPanel(log, modifier)

    @Composable
    override fun Card(title: String, subtitle: String, icon: ImageVector, onInfoClick: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) =
        GlassCard(title, subtitle, icon, onInfoClick, content)

    @Composable
    override fun TextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier, maxLines: Int, validation: ValidationResult?, placeholder: String) =
        GlassTextField(value, onValueChange, label, modifier, maxLines, validation, placeholder)

    @Composable
    override fun Dropdown(label: String, value: String, options: List<String>, onSelectionChanged: (Int) -> Unit, modifier: Modifier) =
        GlassDropdown(label, value, options, onSelectionChanged, modifier)

    @Composable
    override fun Button(text: String, onClick: () -> Unit, modifier: Modifier, isLoading: Boolean, enabled: Boolean, icon: ImageVector?) =
        GlassButton(text, onClick, modifier, isLoading, enabled, icon)

    @Composable
    override fun InfoDialog(title: String, onDismissRequest: () -> Unit, content: @Composable () -> Unit) =
        GlassInfoDialog(title, onDismissRequest, content)
}
