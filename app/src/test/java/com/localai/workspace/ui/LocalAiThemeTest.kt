package com.localai.workspace.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localai.workspace.ui.components.AppScreenContent
import com.localai.workspace.ui.components.minimumAppTouchTarget
import com.localai.workspace.ui.theme.LocalAiLayout
import com.localai.workspace.ui.theme.LocalAiRadii
import com.localai.workspace.ui.theme.LocalAiSpacing
import com.localai.workspace.ui.theme.LocalAiStatusColors
import com.localai.workspace.ui.theme.LocalAiTypography
import com.localai.workspace.ui.theme.localAiDarkPalette
import com.localai.workspace.ui.theme.localAiLightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class, qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalAiThemeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun explicitLightAndDarkThemesUseProductPalettesAndTypography() {
        var lightBackground: Color? = null
        var darkBackground: Color? = null
        var lightSurfaceContainer: Color? = null
        var darkSurfaceContainer: Color? = null
        var lightStatus: LocalAiStatusColors? = null
        var darkStatus: LocalAiStatusColors? = null

        compose.setContent {
            Column {
                LocalAiTheme(darkTheme = false) {
                    val scheme = MaterialTheme.colorScheme
                    val status = LocalAiStatusColors.current
                    SideEffect {
                        lightBackground = scheme.background
                        lightSurfaceContainer = scheme.surfaceContainer
                        lightStatus = status
                    }
                    Box(Modifier.size(1.dp))
                }
                LocalAiTheme(darkTheme = true) {
                    val scheme = MaterialTheme.colorScheme
                    val status = LocalAiStatusColors.current
                    SideEffect {
                        darkBackground = scheme.background
                        darkSurfaceContainer = scheme.surfaceContainer
                        darkStatus = status
                    }
                    Box(Modifier.size(1.dp))
                }
            }
        }

        compose.waitForIdle()
        assertEquals(Color(0xFFF6F7F4), lightBackground)
        assertEquals(Color(0xFF101613), darkBackground)
        assertEquals(Color(0xFFEFF1EE), lightSurfaceContainer)
        assertEquals(Color(0xFF202824), darkSurfaceContainer)
        assertEquals(Color(0xFF7A5D00), lightStatus?.warning)
        assertEquals(Color(0xFFE6C66B), darkStatus?.warning)
        assertEquals(22.sp, LocalAiTypography.titleLarge.fontSize)
        assertEquals(28.sp, LocalAiTypography.titleLarge.lineHeight)
        assertEquals(18.sp, LocalAiTypography.titleMedium.fontSize)
        assertEquals(24.sp, LocalAiTypography.titleMedium.lineHeight)
        assertEquals(16.sp, LocalAiTypography.bodyMedium.fontSize)
        assertEquals(24.sp, LocalAiTypography.bodyMedium.lineHeight)
        assertEquals(14.sp, LocalAiTypography.bodySmall.fontSize)
        assertEquals(20.sp, LocalAiTypography.bodySmall.lineHeight)
        assertEquals(12.sp, LocalAiTypography.labelSmall.fontSize)
        assertEquals(16.sp, LocalAiTypography.labelSmall.lineHeight)
        listOf(LocalAiTypography.displayLarge, LocalAiTypography.displayMedium,
            LocalAiTypography.displaySmall, LocalAiTypography.headlineLarge,
            LocalAiTypography.headlineMedium, LocalAiTypography.headlineSmall,
            LocalAiTypography.titleLarge, LocalAiTypography.titleMedium, LocalAiTypography.titleSmall,
            LocalAiTypography.bodyLarge, LocalAiTypography.bodyMedium, LocalAiTypography.bodySmall,
            LocalAiTypography.labelLarge, LocalAiTypography.labelMedium, LocalAiTypography.labelSmall
        ).forEach { assertEquals(androidx.compose.ui.text.font.FontFamily.SansSerif, it.fontFamily) }
        assertEquals(listOf(4, 8, 12, 16, 20, 24, 32), listOf(
            LocalAiSpacing.x1, LocalAiSpacing.x2, LocalAiSpacing.x3, LocalAiSpacing.x4,
            LocalAiSpacing.x5, LocalAiSpacing.x6, LocalAiSpacing.x8,
        ).map { it.value.toInt() })
        assertEquals(listOf(8, 12, 20, 28), listOf(
            LocalAiRadii.small, LocalAiRadii.medium, LocalAiRadii.large, LocalAiRadii.extraLarge,
        ).map { it.value.toInt() })
        assertEquals(720, LocalAiLayout.maxReadingWidth.value.toInt())
        assertEquals(16, LocalAiLayout.screenMargin.value.toInt())
        assertEquals(48, LocalAiLayout.minimumTouchTarget.value.toInt())
    }

    @Test
    fun registeredTextAndControlColorPairsMeetWcagAa() {
        val light = localAiLightPalette.colorScheme
        val dark = localAiDarkPalette.colorScheme
        val lightStatus = localAiLightPalette.statusColors
        val darkStatus = localAiDarkPalette.statusColors

        assertAa("light text/background", light.onBackground, light.background)
        assertAa("light text/surface", light.onSurface, light.surface)
        assertAa("light text/surface container", light.onSurface, light.surfaceContainer)
        assertAa("light secondary/container", light.onSurfaceVariant, light.surfaceVariant)
        assertAa("light brand/background", light.primary, light.background)
        assertAa("light onPrimary/primary", light.onPrimary, light.primary)
        assertAa("light onPrimaryContainer/container", light.onPrimaryContainer, light.primaryContainer)
        assertAa("light error/surface", light.error, light.surface)
        assertAa("light onError/error", light.onError, light.error)
        assertAa("light errorContainer pair", light.onErrorContainer, light.errorContainer)
        assertAa("light warning/surface", lightStatus.warning, light.surface)
        assertAa("light warning/container", lightStatus.warning, lightStatus.warningContainer)
        assertAa("light onWarningContainer/container", lightStatus.onWarningContainer, lightStatus.warningContainer)
        assertAa("light success/surface", lightStatus.success, light.surface)
        assertAa("light success/container", lightStatus.success, lightStatus.successContainer)
        assertAa("light onSuccessContainer/container", lightStatus.onSuccessContainer, lightStatus.successContainer)
        assertTrue("light outline must work as a graphical boundary", contrastRatio(light.outline, light.surfaceVariant) >= 3.0)
        assertTrue("light outline is not suitable for normal text", contrastRatio(light.outline, light.surface) < 4.5)

        assertAa("dark text/background", dark.onBackground, dark.background)
        assertAa("dark text/surface", dark.onSurface, dark.surface)
        assertAa("dark text/surface container", dark.onSurface, dark.surfaceContainer)
        assertAa("dark secondary/container", dark.onSurfaceVariant, dark.surfaceVariant)
        assertAa("dark brand/background", dark.primary, dark.background)
        assertAa("dark onPrimary/primary", dark.onPrimary, dark.primary)
        assertAa("dark onPrimaryContainer/container", dark.onPrimaryContainer, dark.primaryContainer)
        assertAa("dark error/surface", dark.error, dark.surface)
        assertAa("dark onError/error", dark.onError, dark.error)
        assertAa("dark errorContainer pair", dark.onErrorContainer, dark.errorContainer)
        assertAa("dark warning/surface", darkStatus.warning, dark.surface)
        assertAa("dark warning/container", darkStatus.warning, darkStatus.warningContainer)
        assertAa("dark onWarningContainer/container", darkStatus.onWarningContainer, darkStatus.warningContainer)
        assertAa("dark success/surface", darkStatus.success, dark.surface)
        assertAa("dark success/container", darkStatus.success, darkStatus.successContainer)
        assertAa("dark onSuccessContainer/container", darkStatus.onSuccessContainer, darkStatus.successContainer)
        assertTrue("dark outline must work as a graphical boundary", contrastRatio(dark.outline, dark.surfaceVariant) >= 3.0)
    }

    @Test
    fun sharedFrameAndTouchTargetRemainUsableAt411DpAndFontScale16() {
        compose.setContent {
            LocalAiTheme(darkTheme = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                    AppScreenContent(modifier = Modifier.testTag("screen-frame")) {
                        Text("Workspace", style = MaterialTheme.typography.titleLarge)
                        Text("Readable content at large text scale", style = MaterialTheme.typography.bodyMedium)
                        Box(Modifier.fillMaxWidth().height(48.dp).testTag("content-width")) {
                            Box(Modifier.minimumAppTouchTarget().testTag("touch-target"))
                        }
                    }
                }
            }
        }

        compose.onNodeWithText("Workspace").assertIsDisplayed()
        compose.onNodeWithText("Readable content at large text scale").assertIsDisplayed()
        compose.onNodeWithTag("screen-frame").assertWidthIsEqualTo(411.dp)
        compose.onNodeWithTag("content-width").assertWidthIsEqualTo(379.dp)
        compose.onNodeWithTag("touch-target").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)

        var bitmap: Bitmap? = null
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap!!))
        }
        val output = File("build/ux-01-captures").apply { mkdirs() }
        File(output, "shared-frame-411dp-fontScale-1.6.png").outputStream().use {
            assertTrue(bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun assertAa(label: String, foreground: Color, background: Color) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$label contrast was $ratio:1; expected at least 4.5:1", ratio >= 4.5)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        fun channel(value: Float): Double {
            val component = value.toDouble()
            return if (component <= 0.04045) component / 12.92 else Math.pow((component + 0.055) / 1.055, 2.4)
        }
        fun luminance(color: Color): Double =
            0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

        val first = luminance(foreground)
        val second = luminance(background)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }
}
