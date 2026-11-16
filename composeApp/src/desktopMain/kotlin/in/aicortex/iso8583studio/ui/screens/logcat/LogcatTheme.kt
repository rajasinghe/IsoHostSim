package `in`.aicortex.iso8583studio.ui.screens.logcat

import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

/**
 * Colours of Android Studio's Logcat, in its Darcula and IntelliJ Light variants. Logcat has its own
 * look rather than the host app's: a near-black console, level-coloured text and a different colour
 * for every tag, so that is what is reproduced here instead of the Material theme.
 */
class LogcatPalette(
    val dark: Boolean,
    val background: Color,
    val toolbar: Color,
    val border: Color,
    val field: Color,
    val selection: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val highlight: Color,
    val error: Color,
    private val message: Map<LogcatLevel, Color>,
    private val badge: Map<LogcatLevel, Color>,
    private val badgeText: Map<LogcatLevel, Color>,
    private val tags: List<Color>,
) {
    fun messageColor(level: LogcatLevel): Color = message.getValue(level)
    fun badgeColor(level: LogcatLevel): Color = badge.getValue(level)
    fun badgeTextColor(level: LogcatLevel): Color = badgeText.getValue(level)

    /** The same tag always gets the same colour, so a tag can be followed down the screen. */
    fun tagColor(tag: String): Color = tags[tag.hashCode().absoluteValue % tags.size]

    companion object {
        val Dark = LogcatPalette(
            dark = true,
            background = Color(0xFF2B2B2B),
            toolbar = Color(0xFF3C3F41),
            border = Color(0xFF515151),
            field = Color(0xFF45494A),
            selection = Color(0xFF214283),
            text = Color(0xFFBBBBBB),
            muted = Color(0xFF808080),
            accent = Color(0xFF4A88C7),
            highlight = Color(0xFF5B5B1E),
            error = Color(0xFFFF6B68),
            message = mapOf(
                LogcatLevel.VERBOSE to Color(0xFFBBBBBB),
                LogcatLevel.DEBUG to Color(0xFF6CA8D9),
                LogcatLevel.INFO to Color(0xFF8FC46A),
                LogcatLevel.WARN to Color(0xFFE6B422),
                LogcatLevel.ERROR to Color(0xFFFF6B68),
                LogcatLevel.ASSERT to Color(0xFFFF6B68),
            ),
            badge = mapOf(
                LogcatLevel.VERBOSE to Color(0xFF6B6B6B),
                LogcatLevel.DEBUG to Color(0xFF3592C4),
                LogcatLevel.INFO to Color(0xFF62B543),
                LogcatLevel.WARN to Color(0xFFE6B422),
                LogcatLevel.ERROR to Color(0xFFDB5C5C),
                LogcatLevel.ASSERT to Color(0xFFB71C1C),
            ),
            badgeText = mapOf(
                LogcatLevel.VERBOSE to Color.White,
                LogcatLevel.DEBUG to Color.White,
                LogcatLevel.INFO to Color(0xFF1E1E1E),
                LogcatLevel.WARN to Color(0xFF1E1E1E),
                LogcatLevel.ERROR to Color.White,
                LogcatLevel.ASSERT to Color.White,
            ),
            tags = listOf(
                Color(0xFFE8BF6A), Color(0xFFB589D6), Color(0xFF56B6C2), Color(0xFFE08E5A),
                Color(0xFFE57AA5), Color(0xFFA4C95A), Color(0xFF6AA7E8), Color(0xFFE57373),
                Color(0xFF4DB6AC), Color(0xFF9FA8DA),
            ),
        )

        val Light = LogcatPalette(
            dark = false,
            background = Color(0xFFFFFFFF),
            toolbar = Color(0xFFF2F2F2),
            border = Color(0xFFC9C9C9),
            field = Color(0xFFFFFFFF),
            selection = Color(0xFFA6D2FF),
            text = Color(0xFF000000),
            muted = Color(0xFF808080),
            accent = Color(0xFF3574F0),
            highlight = Color(0xFFFFEB8A),
            error = Color(0xFFC62828),
            message = mapOf(
                LogcatLevel.VERBOSE to Color(0xFF6E6E6E),
                LogcatLevel.DEBUG to Color(0xFF1E6FBE),
                LogcatLevel.INFO to Color(0xFF3B8A33),
                LogcatLevel.WARN to Color(0xFFB07A00),
                LogcatLevel.ERROR to Color(0xFFC62828),
                LogcatLevel.ASSERT to Color(0xFFB71C1C),
            ),
            badge = mapOf(
                LogcatLevel.VERBOSE to Color(0xFF8E8E8E),
                LogcatLevel.DEBUG to Color(0xFF1E6FBE),
                LogcatLevel.INFO to Color(0xFF3B8A33),
                LogcatLevel.WARN to Color(0xFFC79200),
                LogcatLevel.ERROR to Color(0xFFC62828),
                LogcatLevel.ASSERT to Color(0xFF8E0000),
            ),
            badgeText = mapOf(
                LogcatLevel.VERBOSE to Color.White,
                LogcatLevel.DEBUG to Color.White,
                LogcatLevel.INFO to Color.White,
                LogcatLevel.WARN to Color.White,
                LogcatLevel.ERROR to Color.White,
                LogcatLevel.ASSERT to Color.White,
            ),
            tags = listOf(
                Color(0xFF9A6B00), Color(0xFF7B42A8), Color(0xFF007A87), Color(0xFFB45309),
                Color(0xFFB83280), Color(0xFF4D7C0F), Color(0xFF1D5FB4), Color(0xFFC0392B),
                Color(0xFF00796B), Color(0xFF3F51B5),
            ),
        )
    }
}
