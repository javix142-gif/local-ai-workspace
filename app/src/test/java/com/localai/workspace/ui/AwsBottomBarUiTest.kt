package com.localai.workspace.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.localai.workspace.ui.components.AwsBottomBar
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class, qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AwsBottomBarUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allFourLabelsAndTargetsRemainUsableAtSystemTextScales() {
        val routes = listOf(
            "home" to "Inicio",
            "workspace" to "Chats",
            "memory" to "Memoria",
            "settings" to "Más",
        )
        var setFontScale: ((Float) -> Unit)? = null
        var minimumTouchTargetPx = 0f

        compose.setContent {
            LocalAiTheme {
                val systemDensity = LocalDensity.current
                val fontScale = remember { mutableStateOf(1f) }
                val selectedRoute = remember { mutableStateOf("workspace") }
                SideEffect {
                    setFontScale = { fontScale.value = it }
                    minimumTouchTargetPx = with(systemDensity) { 48.dp.toPx() }
                }
                CompositionLocalProvider(
                    LocalDensity provides Density(systemDensity.density, fontScale.value),
                ) {
                    AwsBottomBar(
                        selectedRoute = selectedRoute.value,
                        onDestinationSelected = { selectedRoute.value = it },
                    )
                }
            }
        }

        for (scale in listOf(1.0f, 1.3f, 1.6f, 2.0f)) {
            compose.runOnIdle { setFontScale?.invoke(scale) }
            compose.waitForIdle()
            routes.forEach { (route, label) ->
                compose.onNodeWithTag("top-level-label-$route", useUnmergedTree = true)
                    .assertIsDisplayed()
                    .assertTextEquals(label)
                val item = compose.onNodeWithTag("top-level-$route", useUnmergedTree = true)
                    .assertHasClickAction()
                    .fetchSemanticsNode().boundsInRoot
                val icon = compose.onNodeWithTag("top-level-icon-$route", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val labelBounds = compose.onNodeWithTag("top-level-label-$route", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                assertTrue("$route target at scale $scale must be at least 48dp", item.height >= minimumTouchTargetPx)
                assertTrue("$route keeps the icon and label inside its touch target", icon.left >= item.left && labelBounds.right <= item.right)
                assertTrue("$route label follows its icon and remains inside the target", labelBounds.top >= icon.bottom && labelBounds.bottom <= item.bottom)
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithTag("top-level-label-$route", useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getTextLayoutResult ->
                        getTextLayoutResult(layouts)
                    }
                assertTrue("$route label layout exists at scale $scale", layouts.isNotEmpty())
                assertTrue("$route label is not visually clipped at scale $scale", layouts.all {
                    !it.didOverflowWidth && it.lineCount == 1 && it.getLineRight(0) <= it.size.width
                })
            }

            routes.forEach { (route, _) ->
                compose.onNodeWithTag("top-level-$route", useUnmergedTree = true).performClick()
                compose.waitForIdle()
                compose.onNodeWithTag("top-level-$route", useUnmergedTree = true).assertIsSelected()
            }
        }
    }
}
