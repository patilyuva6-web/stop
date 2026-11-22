package com.studyshield.shared.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyshield.shared.R

val DeepSlate = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate600 = Color(0xFF475569)
val Slate400 = Color(0xFF94A3B8)
val CloudCanvas = Color(0xFFF8FAFC)
val SurfaceWhite = Color(0xFFFFFFFF)
val SurfaceMuted = Color(0xFFF1F5F9)

val IndigoPrimary = Color(0xFF4338CA)
val IndigoSoft = Color(0xFFE0E7FF)
val IndigoLight = Color(0xFF818CF8)

val EmeraldFocus = Color(0xFF059669)
val EmeraldVibrant = Color(0xFF10B981)
val EmeraldSoft = Color(0xFFD1FAE5)

val AmberBreak = Color(0xFFD97706)
val AmberSoft = Color(0xFFFEF3C7)

val CrimsonAlert = Color(0xFFDC2626)
val CrimsonSoft = Color(0xFFFEE2E2)

val DarkBackground = Color(0xFF090E1A)
val DarkSurface = Color(0xFF111827)
val DarkSurfaceVariant = Color(0xFF1E293B)
val DarkOutline = Color(0xFF334155)

val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

val FocusGuardTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp
    )
)

private val LightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = IndigoSoft,
    onPrimaryContainer = DeepSlate,
    secondary = EmeraldFocus,
    onSecondary = Color.White,
    secondaryContainer = EmeraldSoft,
    onSecondaryContainer = DeepSlate,
    tertiary = AmberBreak,
    onTertiary = Color.White,
    tertiaryContainer = AmberSoft,
    onTertiaryContainer = DeepSlate,
    error = CrimsonAlert,
    onError = Color.White,
    errorContainer = CrimsonSoft,
    onErrorContainer = DeepSlate,
    background = CloudCanvas,
    onBackground = DeepSlate,
    surface = SurfaceWhite,
    onSurface = DeepSlate,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = Slate600,
    outline = Color(0xFFCBD5E1)
)

private val DarkColorScheme = darkColorScheme(
    primary = IndigoLight,
    onPrimary = DeepSlate,
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = IndigoSoft,
    secondary = EmeraldVibrant,
    onSecondary = DeepSlate,
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = EmeraldSoft,
    tertiary = Color(0xFFFBBF24),
    onTertiary = DeepSlate,
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = AmberSoft,
    error = Color(0xFFF87171),
    onError = DeepSlate,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = CrimsonSoft,
    background = DarkBackground,
    onBackground = CloudCanvas,
    surface = DarkSurface,
    onSurface = CloudCanvas,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Slate400,
    outline = DarkOutline
)

val FocusGuardShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FocusGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FocusGuardTypography,
        shapes = FocusGuardShapes,
        content = content
    )
}
