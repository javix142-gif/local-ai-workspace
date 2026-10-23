package com.localai.workspace.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.localai.workspace.HomeDashboardContent
import com.localai.workspace.MainActivity
import com.localai.workspace.data.ModelEntity
import com.localai.workspace.domain.inference.RuntimePreparation
import com.localai.workspace.domain.tools.LocalCalendarEvent
import com.localai.workspace.ui.components.AwsBottomBar
import com.localai.workspace.ui.LocalAiTheme
import com.localai.workspace.ui.HomeCalendarDayState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SYNTHETIC_MODEL_ID = "synthetic-h3b-home-model"

internal fun h3bCaptureDirectory(): File = File(
    System.getenv("AWS_H3B_CAPTURE_DIR") ?: "build/h3b-phase3-review-captures",
)

private fun captureSyntheticHome(compose:AndroidComposeTestRule<*,MainActivity>,name:String,expectedScale:Float) {
    var renderedFontScale=0f
    var openedModelSettings=0
    val syntheticDate=LocalDate.of(2026,10,22)
    val zone=ZoneId.systemDefault()
    fun eventTime(hour:Int,minute:Int)=syntheticDate.atTime(hour,minute).atZone(zone).toInstant().toEpochMilli()
    val syntheticEvents=listOf(
        LocalCalendarEvent("SINTÉTICO · Revisión del proyecto",eventTime(10,0),eventTime(10,30),false,"Sala de prueba"),
        LocalCalendarEvent("SINTÉTICO · Preparar el resumen",eventTime(12,0),eventTime(12,30),false,null),
        LocalCalendarEvent("SINTÉTICO · Revisar documentos",eventTime(15,0),eventTime(15,30),false,null),
    )
    check(syntheticEvents.all { event ->
        Instant.ofEpochMilli(event.startEpochMillis).atZone(zone).toLocalDate()==syntheticDate &&
            Instant.ofEpochMilli(event.endEpochMillis).atZone(zone).toLocalDate()==syntheticDate
    }) { "The synthetic agenda fixture must stay within one local calendar date." }
    compose.activity.setContentView(ComposeView(compose.activity).apply { setContent {
        val configuration=LocalConfiguration.current
        SideEffect { renderedFontScale=configuration.fontScale }
        LocalAiTheme {
            Scaffold(bottomBar={AwsBottomBar("home",onDestinationSelected={})}) { insets ->
                HomeDashboardContent(
                    greeting="Buenos días",
                    todayLabel=DateTimeFormatter.ofPattern("EEEE d 'de' MMMM",Locale.forLanguageTag("es"))
                        .format(syntheticDate),
                    models=listOf(ModelEntity(
                        id=SYNTHETIC_MODEL_ID,
                        displayName="SINTÉTICO · Gemma 4 E2B",
                        localPath="/synthetic/model-not-loaded",
                        fileHash="0".repeat(64),
                        fileSize=2L*1024*1024*1024,
                        format="LITERT_LM",
                        runtimeId="litert-lm",
                        compatibilityStatus="COMPATIBLE",
                        importedAt=1L,
                    )),
                    preparation=RuntimePreparation.State(modelId=SYNTHETIC_MODEL_ID,ready=true),
                    calendarDay=HomeCalendarDayState.Events(records=syntheticEvents,truncated=false),
                    memoryEnabled=true,
                    webEnabled=false,
                    pendingMemoryCount=0,
                    creating=false,
                    chatReady=true,
                    modifier=Modifier.padding(insets),
                    onRequestCalendarPermission={},
                    onStartChat={},
                    onOpenChats={},
                    onOpenModels={openedModelSettings++},
                    onOpenMemory={},
                    onPrepareModel={},
                    onRetryModelPreparation={},
                    onCancelModelPreparation={},
                )
            }
        }
    } })
    compose.waitForIdle()
    assertEquals(expectedScale,renderedFontScale,0f)
    writeHomeCapture(compose,"$name-overview")
    compose.onNodeWithTag("home-greeting").assertExists().assertIsDisplayed()
    listOf("home" to "Inicio","workspace" to "Chats","memory" to "Memoria","settings" to "Más").forEach { (route,label) ->
        compose.onNodeWithTag("top-level-label-$route",useUnmergedTree=true).assertIsDisplayed().assertTextEquals(label)
    }
    compose.onNodeWithText("Memoria activa").assertExists()
    compose.onNodeWithText("Web desactivada").assertExists()
    compose.onNodeWithTag("home-status-model",useUnmergedTree=true).assertExists().assertIsDisplayed()
    compose.onNodeWithTag("home-workspace-model-card").assertDoesNotExist()
    val webStatusBounds=compose.onNodeWithTag("home-status-web",useUnmergedTree=true)
        .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    if(expectedScale>=1.5f) check(webStatusBounds.height<80f) {
        "The Web status pill must remain a single readable line at large text scale: $webStatusBounds"
    }
    compose.onNodeWithText("Modelo listo").assertExists()
    compose.onNodeWithTag("home-pending-memory").assertDoesNotExist()
    compose.onNodeWithTag("home-model-missing").assertDoesNotExist()
    assertEquals(3,compose.onAllNodesWithTag("home-calendar-event").fetchSemanticsNodes().size)
    compose.onNodeWithText("SINTÉTICO · Revisar documentos").performScrollTo().assertIsDisplayed()
    writeHomeCapture(compose,"$name-agenda")
    compose.onNodeWithTag("home-open-models",useUnmergedTree=true).assertHasClickAction().performClick()
    assertEquals("The real model configuration entry point stays available from the compact status.",1,openedModelSettings)
}

private fun writeHomeCapture(compose:AndroidComposeTestRule<*,MainActivity>,name:String) {
    compose.waitForIdle()
    val folder=h3bCaptureDirectory().apply{mkdirs()}
    val diagnostic=buildString {
        appendLine("fontScale=${compose.activity.resources.configuration.fontScale}")
        listOf("home-greeting","home-status-memory","home-status-web","home-status-model","home-open-models","home-calendar-card","home-calendar-event","home-start-chat","home-open-chats")
            .forEach { tag ->
                val bounds=runCatching {
                    compose.onNodeWithTag(tag,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                }.getOrElse { "MISSING: ${it.javaClass.simpleName}" }
                appendLine("$tag=$bounds")
            }
    }
    var image:Bitmap?=null
    compose.runOnUiThread {
        val view=compose.activity.window.decorView
        folder.resolve("$name-layout.txt").writeText(diagnostic+"decor=${view.width}x${view.height}\n")
        image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(image!!))
    }
    folder.resolve("$name.png").outputStream().use { check(image!!.compress(Bitmap.CompressFormat.PNG,100,it)) }
}

/** Home dashboard production composable in the actual shell, with an explicitly synthetic fixture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=MultiProcessUiTestApplication::class,qualifiers="w360dp-h800dp",fontScale=1f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bHomeSyntheticLightScaleOneCaptureTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun capturesSyntheticHomeLightScaleOne() = captureSyntheticHome(compose,"SYNTHETIC-h3b-home-light-360dp-fontScale-1.0",1f)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=MultiProcessUiTestApplication::class,qualifiers="w360dp-h800dp-night",fontScale=1f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bHomeSyntheticDarkScaleOneCaptureTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun capturesSyntheticHomeDarkScaleOne() = captureSyntheticHome(compose,"SYNTHETIC-h3b-home-dark-360dp-fontScale-1.0",1f)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=MultiProcessUiTestApplication::class,qualifiers="w360dp-h800dp",fontScale=2f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bHomeSyntheticLightScaleTwoCaptureTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun capturesSyntheticHomeLightScaleTwo() = captureSyntheticHome(compose,"SYNTHETIC-h3b-home-light-360dp-fontScale-2.0",2f)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=MultiProcessUiTestApplication::class,qualifiers="w360dp-h800dp-night",fontScale=2f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bHomeSyntheticDarkScaleTwoCaptureTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun capturesSyntheticHomeDarkScaleTwo() = captureSyntheticHome(compose,"SYNTHETIC-h3b-home-dark-360dp-fontScale-2.0",2f)
}
