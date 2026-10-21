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
    val disabled: Color,
)

private val LightBackground = Color(0xFFF7F8FA)
private val LightSurface = Color(0xFFFFFFFF)
private val LightContainer = Color(0xFFE8F0FC)
private val LightText = Color(0xFF1B1F24)
private val LightSecondary = Color(0xFF5A6270)
private val LightOutline = Color(0xFF7D8794)
private val LightBrand = Color(0xFF2F6FD1)
private val LightError = Color(0xFFBA1A1A)

private val DarkBackground = Color(0xFF121418)
private val DarkSurface = Color(0xFF1C1F25)
private val DarkContainer = Color(0xFF23324A)
private val DarkText = Color(0xFFE6E8EB)
private val DarkSecondary = Color(0xFFA8B0BC)
private val DarkOutline = Color(0xFF9AA1AD)
private val DarkBrand = Color(0xFF7FA8F0)
private val DarkError = Color(0xFFFFB4AB)

val localAiLightPalette = LocalAiPalette(
    colorScheme = lightColorScheme(
        primary = LightBrand,
        onPrimary = LightSurface,
        primaryContainer = LightContainer,
        onPrimaryContainer = LightText,
        secondary = LightSecondary,
        onSecondary = LightSurface,
        secondaryContainer = LightContainer,
        onSecondaryContainer = LightText,
        tertiary = LightBrand,
        onTertiary = LightSurface,
        tertiaryContainer = LightContainer,
        onTertiaryContainer = LightText,
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
        outlineVariant = Color(0xFFD1D8E2),
        error = LightError,
        onError = LightSurface,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        inverseSurface = Color(0xFF30343B),
        inverseOnSurface = Color(0xFFF6F8FC),
        inversePrimary = DarkBrand,
        surfaceTint = LightBrand,
    ),
    statusColors = LocalAiStatusColors(
        warning = Color(0xFF9A6700),
        warningContainer = Color(0xFFFFF7E8),
        onWarningContainer = Color(0xFF3B2A00),
        success = Color(0xFF286F47),
        successContainer = Color(0xFFE0F2E6),
        onSuccessContainer = Color(0xFF153A24),
        disabled = Color(0xFF6B7280),
    ),
)

val localAiDarkPalette = LocalAiPalette(
    colorScheme = darkColorScheme(
        primary = DarkBrand,
        onPrimary = DarkBackground,
        primaryContainer = DarkContainer,
        onPrimaryContainer = DarkText,
        secondary = DarkSecondary,
        onSecondary = DarkBackground,
        secondaryContainer = DarkContainer,
        onSecondaryContainer = DarkText,
        tertiary = DarkBrand,
        onTertiary = DarkBackground,
        tertiaryContainer = DarkContainer,
        onTertiaryContainer = DarkText,
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
        outlineVariant = Color(0xFF414956),
        error = DarkError,
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF5C1516),
        onErrorContainer = Color(0xFFFFDAD6),
        inverseSurface = Color(0xFFE1E4DF),
        inverseOnSurface = Color(0xFF2E3431),
        inversePrimary = LightBrand,
        surfaceTint = DarkBrand,
    ),
    statusColors = LocalAiStatusColors(
        warning = Color(0xFFE3B964),
        warningContainer = Color(0xFF42381F),
        onWarningContainer = Color(0xFFFFF0BE),
        success = Color(0xFF7FCB9C),
        successContainer = Color(0xFF213C2B),
        onSuccessContainer = Color(0xFFD5F4DB),
        disabled = Color(0xFF9AA1AD),
    ),
)

// Resolve a sans-serif family explicitly instead of inheriting a device's
// customized default family. Font sizes still follow the system font scale.
private val platformTypography = Typography()
val LocalAiTypography = Typography(
    displayLarge = platformTypography.displayLarge.copy(fontFamily = FontFamily.SansSerif),
    displayMedium = platformTypography.displayMedium.copy(fontFamily = FontFamily.SansSerif),
    displaySmall = platformTypography.displaySmall.copy(fontFamily = FontFamily.SansSerif),
    headlineLarge = platformTypography.headlineLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal),
    headlineMedium = platformTypography.headlineMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
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
    val card: Dp = 16.dp
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
