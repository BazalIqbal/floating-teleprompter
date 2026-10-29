package com.flowveo.prompter.ui.theme

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
import com.flowveo.prompter.R

/** Colour tokens (dark theme only). */
object PrompterColors {
    val Background = Color(0xFF0B0D12)
    val Surface = Color(0xFF151922)
    val Surface2 = Color(0xFF1F2533)
    val Accent = Color(0xFF2F6BFF)
    val AccentText = Color(0xFF9DBBFF)
    val Text = Color(0xFFF2F4F8)
    val Text2 = Color(0xFFA3ABBA)
    val Muted = Color(0xFF8C95A5)
    val Success = Color(0xFF34C38F)
    val Warning = Color(0xFFF5A524)
    val Hairline = Color(0x14FFFFFF)
}

/** Fonts are bundled (SIL OFL, see assets/licenses) so they work without Google Play services. */
object PrompterFonts {
    val Mono = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
        Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    )
    val Sans = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )
}

private val colorScheme = darkColorScheme(
    primary = PrompterColors.Accent,
    onPrimary = Color.White,
    background = PrompterColors.Background,
    onBackground = PrompterColors.Text,
    surface = PrompterColors.Surface,
    onSurface = PrompterColors.Text,
    surfaceVariant = PrompterColors.Surface2,
    onSurfaceVariant = PrompterColors.Text2,
)

private val typography = Typography(
    headlineMedium = TextStyle(fontFamily = PrompterFonts.Sans, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleMedium = TextStyle(fontFamily = PrompterFonts.Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = PrompterFonts.Sans, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = PrompterFonts.Sans, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = PrompterFonts.Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelSmall = TextStyle(fontFamily = PrompterFonts.Sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.8.sp),
)

@Composable
fun PrompterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
}
