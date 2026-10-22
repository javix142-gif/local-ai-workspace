package com.localai.workspace.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
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

    @Test fun largeTextShowsOnlyActiveLabelAndKeepsEveryDestinationAccessible() {
        val routes = listOf(
            "home" to "Inicio",
            "workspace" to "Chats",
            "memory" to "Memoria",
            "settings" to "Más",
        )
        var setFontScale: ((Float) -> Unit)? = null
        var setSelectedRoute: ((String) -> Unit)? = null
        var minimumTouchTargetPx = 0f

        compose.setContent {
            LocalAiTheme {
                val systemDensity = LocalDensity.current
                val fontScale = remember { mutableStateOf(1f) }
                val selectedRoute = remember { mutableStateOf("workspace") }
                SideEffect {
                    setFontScale = { fontScale.value = it }
                    setSelectedRoute = { selectedRoute.value = it }
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

        routes.forEach { (route, label) ->
            compose.onNodeWithTag("top-level-label-$route", useUnmergedTree = true)
                .assertTextEquals(label)
            val target = compose.onNodeWithTag("top-level-$route")
                .assertHasClickAction().fetchSemanticsNode().boundsInRoot
            assertTrue("$route target must be at least 48dp", target.height >= minimumTouchTargetPx)
        }

        for (scale in listOf(1.6f, 2f)) {
            compose.runOnIdle { setFontScale?.invoke(scale) }
            for ((activeRoute, activeLabel) in routes) {
                compose.runOnIdle { setSelectedRoute?.invoke(activeRoute) }
                compose.waitForIdle()
                compose.onNodeWithTag("top-level-label-$activeRoute", useUnmergedTree = true)
                    .assertIsDisplayed().assertTextEquals(activeLabel)
                val item = compose.onNodeWithTag("top-level-$activeRoute", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val icon = compose.onNodeWithTag("top-level-icon-$activeRoute", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val labelBounds = compose.onNodeWithTag("top-level-label-$activeRoute", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                assertTrue("Active $activeLabel label is inside its icon destination", labelBounds.left >= item.left && labelBounds.right <= item.right)
                assertTrue("Active $activeLabel label sits below its icon", labelBounds.top >= icon.bottom)
                assertTrue("Active $activeLabel label remains within the navigation bar", labelBounds.bottom <= item.bottom)
                routes.filter { it.first != activeRoute }.forEach { (route, label) ->
                    compose.onNodeWithTag("top-level-label-$route", useUnmergedTree = true).assertDoesNotExist()
                    compose.onNodeWithContentDescription(label, useUnmergedTree = true).assertExists()
                    compose.onNodeWithTag("top-level-icon-$route", useUnmergedTree = true).assertExists()
                }
                routes.forEach { (route, _) ->
                    val target = compose.onNodeWithTag("top-level-$route")
                        .assertHasClickAction().fetchSemanticsNode().boundsInRoot
                    assertTrue("$route target at fontScale $scale must be at least 48dp", target.height >= minimumTouchTargetPx)
                }
            }
        }
    }
}
