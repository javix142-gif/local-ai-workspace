package com.localai.workspace.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class LocalAiPalette(
    val colorScheme: ColorScheme,
    val statusColors: LocalAiStatusColors,
)

@Immutable
data class LocalAiStatusColors(
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
)

private val LightBackground = Color(0xFFF6F7F4)
private val LightSurface = Color(0xFFFFFFFF)
private val LightContainer = Color(0xFFEFF1EE)
private val LightText = Color(0xFF1D2522)
private val LightSecondary = Color(0xFF54605B)
private val LightOutline = Color(0xFF77817C)
private val LightBrand = Color(0xFF3E746D)
private val LightError = Color(0xFFBA1A1A)

private val DarkBackground = Color(0xFF101613)
private val DarkSurface = Color(0xFF171E1B)
private val DarkContainer = Color(0xFF202824)
private val DarkText = Color(0xFFE1E4DF)
private val DarkSecondary = Color(0xFFBDC8C1)
private val DarkOutline = Color(0xFF89948E)
private val DarkBrand = Color(0xFFA5D8CE)
private val DarkError = Color(0xFFFFB4AB)

val localAiLightPalette = LocalAiPalette(
    colorScheme = lightColorScheme(
        primary = LightBrand,
        onPrimary = LightSurface,
        primaryContainer = Color(0xFFDDEBE7),
        onPrimaryContainer = Color(0xFF173E38),
        secondary = LightSecondary,
        onSecondary = LightSurface,
        secondaryContainer = LightContainer,
        onSecondaryContainer = LightText,
        tertiary = LightBrand,
        onTertiary = LightSurface,
        tertiaryContainer = Color(0xFFDDEBE7),
        onTertiaryContainer = Color(0xFF173E38),
        background = LightBackground,
        onBackground = LightText,
        surface = LightSurface,
        onSurface = LightText,
        surfaceVariant = LightContainer,
        onSurfaceVariant = LightSecondary,
        surfaceBright = LightSurface,
        surfaceDim = LightBackground,
        surfaceContainerLowest = LightSurface,
        surfaceContainerLow = LightBackground,
        surfaceContainer = LightContainer,
        surfaceContainerHigh = LightContainer,
        surfaceContainerHighest = LightContainer,
        outline = LightOutline,
        outlineVariant = Color(0xFFC2C8C3),
        error = LightError,
        onError = LightSurface,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        inverseSurface = Color(0xFF2E3431),
        inverseOnSurface = Color(0xFFF0F1EC),
        inversePrimary = Color(0xFFA5D8CE),
        surfaceTint = LightBrand,
    ),
    statusColors = LocalAiStatusColors(
        warning = Color(0xFF7A5D00),
        warningContainer = Color(0xFFF4E7BE),
        onWarningContainer = Color(0xFF4A3600),
        success = Color(0xFF256D46),
        successContainer = Color(0xFFDCEFE1),
        onSuccessContainer = Color(0xFF173C27),
    ),
)

val localAiDarkPalette = LocalAiPalette(
    colorScheme = darkColorScheme(
        primary = DarkBrand,
        onPrimary = Color(0xFF183833),
        primaryContainer = Color(0xFF294B44),
        onPrimaryContainer = Color(0xFFD5EEE8),
        secondary = DarkSecondary,
        onSecondary = Color(0xFF26322D),
        secondaryContainer = DarkContainer,
        onSecondaryContainer = DarkText,
        tertiary = DarkBrand,
        onTertiary = Color(0xFF183833),
        tertiaryContainer = Color(0xFF294B44),
        onTertiaryContainer = Color(0xFFD5EEE8),
        background = DarkBackground,
        onBackground = DarkText,
        surface = DarkSurface,
        onSurface = DarkText,
        surfaceVariant = DarkContainer,
        onSurfaceVariant = DarkSecondary,
        surfaceBright = DarkSurface,
        surfaceDim = DarkBackground,
        surfaceContainerLowest = DarkBackground,
        surfaceContainerLow = DarkSurface,
        surfaceContainer = DarkContainer,
        surfaceContainerHigh = DarkContainer,
        surfaceContainerHighest = DarkContainer,
        outline = DarkOutline,
        outlineVariant = Color(0xFF424C46),
        error = DarkError,
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        inverseSurface = Color(0xFFE1E4DF),
        inverseOnSurface = Color(0xFF2E3431),
        inversePrimary = Color(0xFF3E746D),
        surfaceTint = DarkBrand,
    ),
    statusColors = LocalAiStatusColors(
        warning = Color(0xFFE6C66B),
        warningContainer = Color(0xFF3C3218),
        onWarningContainer = Color(0xFFFFF0BE),
        success = Color(0xFF8BD2A4),
        successContainer = Color(0xFF203B2B),
        onSuccessContainer = Color(0xFFD5F4DB),
    ),
)

val LocalAiTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

object LocalAiSpacing {
    val x1: Dp = 4.dp
    val x2: Dp = 8.dp
    val x3: Dp = 12.dp
    val x4: Dp = 16.dp
    val x5: Dp = 20.dp
    val x6: Dp = 24.dp
    val x8: Dp = 32.dp
}

object LocalAiLayout {
    val screenMargin: Dp = LocalAiSpacing.x4
    val maxReadingWidth: Dp = 720.dp
    val minimumTouchTarget: Dp = 48.dp
}

object LocalAiRadii {
    val small: Dp = 8.dp
    val medium: Dp = 12.dp
    val large: Dp = 20.dp
    val extraLarge: Dp = 28.dp

    val materialShapes = Shapes(
        extraSmall = RoundedCornerShape(small),
        small = RoundedCornerShape(medium),
        medium = RoundedCornerShape(large),
        large = RoundedCornerShape(extraLarge),
        extraLarge = RoundedCornerShape(extraLarge),
    )
}
