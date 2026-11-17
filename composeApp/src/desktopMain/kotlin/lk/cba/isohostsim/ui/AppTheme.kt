package lk.cba.isohostsim.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import lk.cba.isohostsim.ui.design.PosDarkColors
import lk.cba.isohostsim.ui.design.PosDarkColorsGlass
import lk.cba.isohostsim.ui.design.PosLightColors
import lk.cba.isohostsim.ui.design.PosLightColorsGlass
import lk.cba.isohostsim.ui.design.PosShapes
import lk.cba.isohostsim.ui.design.PosTypography
import lk.cba.isohostsim.ui.screens.components.LocalToolLogPanel
import lk.cba.isohostsim.ui.screens.components.ToolLogPanel
import lk.cba.isohostsim.ui.screens.hostSimulator.LogPanelWithAutoScroll as SimulatorLogPanel

/** The calculators' log panel on desktop is the host simulator's full one, as it always was. */
private val DesktopToolLogPanel = ToolLogPanel { label, onClear, entries ->
    SimulatorLogPanel(label = label, onClearClick = onClear, logEntries = entries)
}

/**
 * Detect system dark mode. Uses Compose's isSystemInDarkTheme() first,
 * then falls back to Windows registry check on Windows.
 */
@Composable
private fun detectDarkTheme(): Boolean {
    val composeDark = isSystemInDarkTheme()
    if (composeDark) return true
    // Fallback: check Windows registry (cached)
    return windowsDarkModeCache
}

/** Cached Windows dark mode detection — checked once at startup */
private val windowsDarkModeCache: Boolean by lazy {
    detectWindowsDarkMode() ?: false
}

@Composable
fun AppTheme(darkTheme: Boolean = detectDarkTheme(), content: @Composable () -> Unit) {
    val glassActive by isGlassEffectActive

    val colors = when {
        darkTheme && glassActive -> PosDarkColorsGlass
        darkTheme -> PosDarkColors
        glassActive -> PosLightColorsGlass
        else -> PosLightColors
    }

    // Text outside a Material surface would otherwise default to black, unreadable on the dark page.
    CompositionLocalProvider(
        LocalToolLogPanel provides DesktopToolLogPanel,
        LocalContentColor provides colors.onBackground,
    ) {
        MaterialTheme(
            colors = colors,
            typography = PosTypography,
            shapes = PosShapes,
            content = content
        )
    }
}

