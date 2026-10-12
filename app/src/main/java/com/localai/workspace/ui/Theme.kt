package com.localai.workspace.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.localai.workspace.ui.theme.LocalAiPalette
import com.localai.workspace.ui.theme.LocalAiRadii
import com.localai.workspace.ui.theme.LocalAiTypography
import com.localai.workspace.ui.theme.localAiDarkPalette
import com.localai.workspace.ui.theme.localAiLightPalette

val LocalAiStatusColors = staticCompositionLocalOf { localAiLightPalette.statusColors }

@Composable
fun LocalAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette: LocalAiPalette = if (darkTheme) localAiDarkPalette else localAiLightPalette
    CompositionLocalProvider(LocalAiStatusColors provides palette.statusColors) {
        MaterialTheme(
            colorScheme = palette.colorScheme,
            typography = LocalAiTypography,
            shapes = LocalAiRadii.materialShapes,
            content = content,
        )
    }
}
