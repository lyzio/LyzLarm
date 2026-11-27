package se.lyzio.anatole.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import se.lyzio.anatole.R

/** App colors (Project Facts + UI mockup). */
object Lz {
    val Base = Color(0xFF1B2F33)
    /** Bedtime screen and logo background. */
    val Night = Color(0xFF0F1C1F)
    val NightCard = Color(0xFF182A2E)
    /** "Gap" marker. */
    val Warning = Color(0xFFF2C94C)
    val Surface = Color(0xFF243D42)
    val SurfaceDim = Color(0xFF20363A)
    val Divider = Color(0xFF2C474D)
    val Outline = Color(0xFF33525A)
    val Text = Color(0xFFEEF3F3)
    val TextSoft = Color(0xFFD3DEE0)
    val TextMuted = Color(0xFFA9BCC0)
    val TextDim = Color(0xFF9DB1B5)
    val OnAccent = Color(0xFF0F1A1C)
    val Accent = Color(0xFFED4C67)
    val AccentText = Color(0xFFF07A8E)
    val AccentSoft = Color(0xFFF59BAA)
    val Sound = Color(0xFF5758BB)
    val Highlight = Color(0xFF006266)
    val HighlightText = Color(0xFFD6EDEC)
    val HighlightSoft = Color(0xFFE6F4F3)
    val Ok = Color(0xFF4FB3A9)
    val Ringing = Color(0xFF6F1E51)
    val RingingCard = Color(0xFF833471)
    val RingingBorder = Color(0xFF9A4A87)
    val RingingButtonBorder = Color(0xFFB0699E)
    val RingingText = Color(0xFFFFF5FA)
    val RingingTextSoft = Color(0xFFF1D9E7)
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
)

private val typography = Typography().let { t ->
    Typography(
        displayLarge = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 60.sp),
        headlineLarge = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 30.sp),
        headlineMedium = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
        titleLarge = t.titleLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = t.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        bodyLarge = t.bodyLarge.copy(fontFamily = Inter, fontSize = 16.sp),
        bodyMedium = t.bodyMedium.copy(fontFamily = Inter, fontSize = 15.sp),
        bodySmall = t.bodySmall.copy(fontFamily = Inter, fontSize = 13.sp),
        labelLarge = t.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        labelMedium = t.labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
        labelSmall = t.labelSmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
    )
}

private val colors = darkColorScheme(
    primary = Lz.Accent,
    onPrimary = Lz.OnAccent,
    secondary = Lz.Highlight,
    onSecondary = Color.White,
    tertiary = Lz.Sound,
    background = Lz.Base,
    onBackground = Lz.Text,
    surface = Lz.Surface,
    onSurface = Lz.Text,
    surfaceVariant = Lz.Divider,
    onSurfaceVariant = Lz.TextMuted,
    outline = Lz.Outline,
    surfaceContainerHigh = Lz.Surface,
    surfaceContainerHighest = Lz.Surface,
    surfaceContainer = Lz.Surface,
)

@Composable
fun AnatoleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
