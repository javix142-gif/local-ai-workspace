package com.localai.workspace.ui

import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatHeaderLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val statusBarInsetPx = AtomicInteger(-1)
    private val composeDensityDpi = AtomicInteger(-1)

    @OptIn(ExperimentalMaterial3Api::class)
    private fun render(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            SideEffect {
                statusBarInsetPx.set(WindowInsets.statusBars.getTop(density))
                composeDensityDpi.set((density.density * 160f).roundToInt())
            }
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    Scaffold(modifier = Modifier.testTag("chat-layout-root"), topBar = {
                        TopAppBar(
                            modifier = Modifier.testTag("chat-header-appbar"),
                            expandedHeight = chatTopBarExpandedHeight(LocalDensity.current.fontScale),
                            navigationIcon = { Text("‹") },
                            title = {
                                ChatHeaderTitle(
                                    agentTitle = { Text("General Agent", maxLines = 1) },
                                    conversationTitle = "A deliberately long conversation title that must end with an ellipsis on narrow screens",
                                    modelStatus = "Gemma 4 E2B · Local · Ready",
                                    onModelClick = {}, enabled = true,
                                )
                            },
                            actions = { Text("⋮") },
                        )
                    }) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding).testTag("chat-body"))
                    }
                }
            }
        }
    }

    @Test fun standardFontScaleKeepsAgentTitleAndModelInsideHeader() {
        render(1f)
        captureLayoutDiagnostics("standard", 1f)
        compose.onNodeWithTag("chat-header-agent").assertIsDisplayed()
        compose.onNodeWithTag("chat-header-conversation").assertIsDisplayed()
        compose.onNodeWithTag("chat-header-model").assertIsDisplayed()
        assertModelLinePrecedesChatBody()
    }

    @Test fun largeFontScaleExpandsHeaderAndKeepsAllThreeLinesVisible() {
        assertTrue(chatTopBarExpandedHeight(1.6f) > chatTopBarExpandedHeight(1f))
        render(1.6f)
        captureLayoutDiagnostics("large-font", 1.6f)
        compose.onNodeWithTag("chat-header-agent").assertIsDisplayed()
        compose.onNodeWithTag("chat-header-conversation").assertIsDisplayed()
        compose.onNodeWithTag("chat-header-model").assertIsDisplayed()
        assertModelLinePrecedesChatBody()
    }

    private fun assertModelLinePrecedesChatBody() {
        val model = compose.onNodeWithTag("chat-header-model").fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithTag("chat-body").fetchSemanticsNode().boundsInRoot
        assertTrue("Model status should not be clipped under the header", model.bottom <= body.top)
    }

    private fun captureLayoutDiagnostics(label: String, requestedFontScale: Float) {
        compose.waitForIdle()
        val tags = listOf(
            "chat-layout-root", "chat-header-appbar", "chat-header-title",
            "chat-header-agent", "chat-header-conversation", "chat-header-model", "chat-body",
        )
        val bounds = tags.joinToString("\n") { tag ->
            val value = runCatching { compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot }
                .getOrNull()
            "$tag.boundsInRootPx=${value ?: "UNAVAILABLE"}"
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val metrics = context.resources.displayMetrics
        val configuration = context.resources.configuration
        val diagnostic = buildString {
            appendLine("case=$label")
            appendLine("requestedComposeFontScale=$requestedFontScale")
            appendLine("resourceFontScale=${configuration.fontScale}")
            appendLine("composeDensityDpi=${composeDensityDpi.get()}")
            appendLine("statusBarInsetPx=${statusBarInsetPx.get()}")
            appendLine("screenPx=${metrics.widthPixels}x${metrics.heightPixels}")
            appendLine("displayDensityDpi=${metrics.densityDpi}")
            appendLine("screenDp=${configuration.screenWidthDp}x${configuration.screenHeightDp}")
            appendLine("orientation=${configuration.orientation}")
            appendLine("sdk=${Build.VERSION.SDK_INT}")
            appendLine("model=${Build.MODEL}")
            appendLine("abis=${Build.SUPPORTED_ABIS.joinToString(",")}")
            appendLine("requestedTopBarHeight=${chatTopBarExpandedHeight(requestedFontScale)}")
            append(bounds)
        }
        Log.i("ChatHeaderLayout", diagnostic)
        compose.onRoot().printToLog("ChatHeaderLayout")

        val screenshotPath = "/sdcard/Download/local-ai-chat-header-$label.png"
        val screenshotCommand = "mkdir -p /sdcard/Download && screencap -p $screenshotPath; printf 'exit=%s\\n' \$?"
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(screenshotCommand)
        val screenshotResult = FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }.trim()
        descriptor.close()
        Log.i("ChatHeaderLayout", "screenshot=$screenshotPath $screenshotResult")
    }
}
