package lk.cba.isohostsim.ui.design

import androidx.compose.material.Colors
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The POS Testing Platform look: one indigo accent on neutral surfaces, a dark navigation sidebar,
 * 8 dp controls and 12 dp cards. Used by the desktop app only; the browser tools keep their own theme.
 */
object Pos {
    // Accent
    val Indigo = Color(0xFF4338CA)
    val IndigoDeep = Color(0xFF3730A3)
    val IndigoSoft = Color(0xFFEEF0FF)
    val IndigoInk = Color(0xFF312E81)
    val Teal = Color(0xFF0F6B5C)
    val TealSoft = Color(0xFFE3F4F1)

    // Light surfaces
    val Canvas = Color(0xFFF4F5F7)
    val Card = Color.White
    val Ink = Color(0xFF14181F)
    val Muted = Color(0xFF566070)
    val Line = Color(0xFFE3E6EA)
    val LineSoft = Color(0xFFEDEFF2)
    val Sunken = Color(0xFFF8F9FB)

    // Status
    val Success = Color(0xFF0B6B43)
    val SuccessSoft = Color(0xFFE6F6EE)
    val SuccessDot = Color(0xFF12A06A)
    val Danger = Color(0xFFB42318)
    val DangerSoft = Color(0xFFFDECEA)
    val Warn = Color(0xFF8A5200)
    val WarnSoft = Color(0xFFFFF3DC)

    // Sidebar (dark in both themes)
    val Sidebar = Color(0xFF12151C)
    val SidebarActive = Color(0xFF232838)
    val SidebarCard = Color(0xFF1A1F2B)
    val SidebarText = Color(0xFFC9CFDA)
    val SidebarMuted = Color(0xFF9AA3B2)

    // Code blocks
    val Code = Color(0xFF0F1420)
    val CodeText = Color(0xFFE6E9F2)
}

val PosLightColors: Colors = lightColors(
    primary = Pos.Indigo,
    primaryVariant = Pos.IndigoDeep,
    // Material draws checkboxes, switches and radio buttons in the secondary colour, so it matches the accent.
    secondary = Pos.Indigo,
    secondaryVariant = Pos.IndigoDeep,
    background = Pos.Canvas,
    surface = Pos.Card,
    error = Pos.Danger,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Pos.Ink,
    onSurface = Pos.Ink,
    onError = Color.White,
)

val PosDarkColors: Colors = darkColors(
    primary = Color(0xFF8B93FF),
    primaryVariant = Color(0xFF6C75F0),
    secondary = Color(0xFF8B93FF),
    background = Color(0xFF0F1219),
    surface = Color(0xFF171B26),
    error = Color(0xFFFF6B5E),
    onPrimary = Color(0xFF0B0E26),
    onSecondary = Color(0xFF04201B),
    onBackground = Color(0xFFE6E9F2),
    onSurface = Color(0xFFE6E9F2),
    onError = Color.Black,
)

/** The same palettes with see-through backgrounds, for the Windows 11 Mica backdrop. */
val PosLightColorsGlass: Colors = PosLightColors.copy(
    background = Pos.Canvas.copy(alpha = 0.80f),
    surface = Pos.Card.copy(alpha = 0.85f),
)
val PosDarkColorsGlass: Colors = PosDarkColors.copy(
    background = PosDarkColors.background.copy(alpha = 0.80f),
    surface = PosDarkColors.surface.copy(alpha = 0.85f),
)

val PosTypography = Typography(
    defaultFontFamily = FontFamily.Default,
    h4 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = 0.sp),
    h5 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = 0.sp),
    h6 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = 0.sp),
    subtitle1 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.sp),
    subtitle2 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp),
    body1 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, letterSpacing = 0.sp),
    body2 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, letterSpacing = 0.sp),
    button = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp),
    caption = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 0.sp),
    overline = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp),
)

val PosShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
)
