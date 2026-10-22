package com.localai.workspace.ui

import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
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
            val statusBarTopPx = WindowInsets.statusBars.getTop(density)
            SideEffect {
                statusBarInsetPx.set(statusBarTopPx)
                composeDensityDpi.set((density.density * 160f).roundToInt())
            }
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    Scaffold(modifier = Modifier.testTag("chat-layout-root"), topBar = {
                        ChatTopBar(
                            modifier = Modifier.testTag("chat-header-appbar"),
                            navigationIcon = { Text("‹") },
                            title = {
                                ChatHeaderTitle(
                                    agentTitle = { Text("General Agent", maxLines = 1) },
                                    conversationTitle = LONG_CONVERSATION_TITLE,
                                    modelStatus = LONG_MODEL_STATUS,
                                    onModelClick = {}, enabled = true,
                                    modelState = "Preparing the selected local model",
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
        assertHeaderContentAndBounds(1f)
    }

    @Test fun largeFontScaleKeepsHeaderPillsReadableAtOnePointSix() {
        render(1.6f)
        captureLayoutDiagnostics("font-scale-1.6", 1.6f, capturePng = false)
        assertHeaderContentAndBounds(1.6f)
    }

    @Test fun maximumFontScaleKeepsHeaderPillsReadableAtTwo() {
        render(2f)
        captureLayoutDiagnostics("large-font", 2f)
        assertHeaderContentAndBounds(2f)
    }

    private fun assertHeaderContentAndBounds(fontScale: Float) {
        compose.onNodeWithTag("chat-header-agent", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("chat-header-conversation", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("chat-header-model", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("chat-header-model-state", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("chat-header-conversation", useUnmergedTree = true).assertTextContains(LONG_CONVERSATION_TITLE)
        compose.onNodeWithText(LONG_MODEL_STATUS, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(LONG_MODEL_STATUS, useUnmergedTree = true).assertIsDisplayed()
        assertModelLinePrecedesChatBody(fontScale)
    }

    private fun assertModelLinePrecedesChatBody(fontScale: Float) {
        val appBar = compose.onNodeWithTag("chat-header-appbar", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("chat-header-title", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val conversation = compose.onNodeWithTag("chat-header-conversation", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val model = compose.onNodeWithTag("chat-header-model", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val agent = compose.onNodeWithTag("chat-header-agent", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val modelState = compose.onNodeWithTag("chat-header-model-state", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithTag("chat-body", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val density = composeDensityDpi.get() / 160f
        assertTrue("The long conversation title should wrap at fontScale=$fontScale", conversation.height > 20f * density * fontScale)
        assertTrue("The long model status should wrap at fontScale=$fontScale", model.height > 16f * density * fontScale)
        assertTrue("ChatTopBar should grow beyond the standard minimum to fit its content", appBar.height > 64f * density)
        if (statusBarInsetPx.get() > 0) {
            assertTrue("Header text should begin below the status-bar inset", title.top >= statusBarInsetPx.get())
        }
        assertTrue("Conversation title should remain inside the app bar", conversation.bottom <= appBar.bottom)
        assertTrue("Model status should remain inside the app bar", model.bottom <= appBar.bottom)
        assertTrue("Agent pill should remain within the app bar bounds", agent.left >= appBar.left && agent.right <= appBar.right && agent.bottom <= appBar.bottom)
        assertTrue("Model-state pill should remain within the app bar bounds", modelState.left >= appBar.left && modelState.right <= appBar.right && modelState.bottom <= appBar.bottom)
        assertTrue("Model status should not be clipped under the header", model.bottom <= body.top)
    }

    private fun captureLayoutDiagnostics(
        label: String,
        requestedFontScale: Float,
        capturePng: Boolean = true,
    ) {
        compose.waitForIdle()
        val tags = listOf(
            "chat-layout-root", "chat-header-appbar", "chat-header-title",
            "chat-header-agent", "chat-header-conversation", "chat-header-model", "chat-body",
        )
        val bounds = tags.joinToString("\n") { tag ->
            val value = runCatching {
                compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            }
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
            appendLine("headerSizing=content-driven ChatTopBar; no fixed expanded height")
            append(bounds)
        }
        Log.i("ChatHeaderLayout", diagnostic)
        compose.onRoot(useUnmergedTree = true).printToLog("ChatHeaderLayout")
        if (!capturePng) return

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        // Capture the Compose root after waitForIdle instead of the device display surface.
        // UiAutomation returned an almost-white standard-font frame even though Compose had
        // completed layout and the semantics assertions passed.
        val root = compose.onRoot(useUnmergedTree = true)
        val bitmap = root.captureToImage().asAndroidBitmap()
        val rootBounds = root.fetchSemanticsNode().boundsInRoot
        val captureFile = File(targetContext.cacheDir, "chat-header-$label.png")
        try {
            FileOutputStream(captureFile).use { output ->
                assertTrue("PNG compression failed for $label", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            val png = captureFile.readBytes()
            val sha256 = MessageDigest.getInstance("SHA-256").digest(png)
                .joinToString("") { "%02x".format(it) }
            val encoded = Base64.encodeToString(png, Base64.NO_WRAP)
            val chunks = encoded.chunked(PNG_LOG_CHUNK_CHARACTERS)
            Log.i(
                PNG_LOG_TAG,
                "M00PNG|BEGIN|$label|${bitmap.width}|${bitmap.height}|${png.size}|$sha256|${chunks.size}",
            )
            chunks.forEachIndexed { index, chunk ->
                Log.i(PNG_LOG_TAG, "M00PNG|CHUNK|$label|$index|$chunk")
            }
            Log.i(PNG_LOG_TAG, "M00PNG|END|$label|$sha256")
            Log.i(
                "ChatHeaderLayout",
                "captureSource=ComposeTestRule.onRoot.captureToImage case=$label bitmap=${bitmap.width}x${bitmap.height} " +
                    "rootBoundsInRootPx=$rootBounds " +
                    "cacheBytes=${captureFile.length()} cacheFileRetained=true transport=logcat_base64_chunks_v1 " +
                    "chunks=${chunks.size} sha256=$sha256",
            )
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val LONG_CONVERSATION_TITLE = "A deliberately long conversation title that wraps over several lines and remains readable on a compact screen"
        const val LONG_MODEL_STATUS = "Gemma 4 E2B long selected model identifier · Local · Preparing model resources"
        const val PNG_LOG_TAG = "M00HeaderPng"
        const val PNG_LOG_CHUNK_CHARACTERS = 512
    }
}
