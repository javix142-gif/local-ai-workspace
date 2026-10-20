package com.localai.workspace.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.mutableStateOf
import com.localai.workspace.data.ToolCallEntity
import com.localai.workspace.ui.LocalAiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class, qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidActionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun pendingProposalShowsFullDataAndDoesNothingUntilExplicitConfirmation() {
        val opened = AtomicInteger()
        val declined = AtomicInteger()
        val call = pendingCall("""{"action":"sms_draft","phoneNumber":"+12025550100","body":"${"Long message line ".repeat(45)}"}""")
        compose.setContent {
            LocalAiTheme {
                ToolExecutionCard(call, onOpenAndroidAction = { opened.incrementAndGet() }, onDeclineAndroidAction = { declined.incrementAndGet() })
            }
        }

        compose.onNodeWithText("Preparada; aún no se ha abierto ninguna aplicación.").assertIsDisplayed()
        compose.onNodeWithTag("android-action-review-button-${call.id}").performClick()
        compose.onNodeWithText("Revisa todos los datos. Solo al pulsar «Abrir aplicación» se abrirá la pantalla correspondiente; no se enviará ni guardará nada automáticamente.").assertIsDisplayed()
        compose.onNodeWithTag("android-action-value-phoneNumber").assertIsDisplayed()
        compose.onNodeWithTag("android-action-value-body").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, opened.get())
            assertEquals(0, declined.get())
        }
        compose.onNodeWithTag("android-action-confirm-button").performClick()
        compose.runOnIdle { assertEquals(1, opened.get()) }
    }

    @Test fun decliningProposalDoesNotOpenAndOpenedStatusIsExplicitlyUserReported() {
        val opened = AtomicInteger()
        val declined = AtomicInteger()
        val reported = AtomicInteger()
        val pending = pendingCall("""{"action":"map_search","query":"Biblioteca central"}""")
        val renderedCall = mutableStateOf(pending)
        compose.setContent {
            LocalAiTheme {
                ToolExecutionCard(
                    renderedCall.value,
                    { opened.incrementAndGet() },
                    { declined.incrementAndGet() },
                    {
                        reported.incrementAndGet()
                        renderedCall.value = renderedCall.value.copy(status = "CONFIRMED_BY_USER")
                    },
                )
            }
        }
        compose.onNodeWithTag("android-action-review-button-${pending.id}").performClick()
        compose.onNodeWithTag("android-action-decline-button").performClick()
        compose.runOnIdle {
            assertEquals(0, opened.get())
            assertEquals(1, declined.get())
            renderedCall.value = pending.copy(status = "OPENED")
        }

        compose.onNodeWithText("Se abrió una aplicación externa. Local AI Workspace no puede verificar qué ocurrió allí.").assertIsDisplayed()
        compose.onNodeWithTag("android-action-user-reported-complete").performClick()
        compose.runOnIdle { assertEquals(1, reported.get()) }

        compose.onNodeWithText("Marcada como completada según tu confirmación.").assertIsDisplayed()
        compose.onNodeWithText("Detalles").performClick()
        compose.onNodeWithText("Este estado refleja lo que indicaste; la aplicación no verificó la acción externa.").assertIsDisplayed()
    }

    private fun pendingCall(arguments: String) = ToolCallEntity(
        id = "action-test-1",
        conversationId = "conversation-test",
        messageId = "message-test",
        toolId = "android.action.prepare",
        validatedArgumentsJson = arguments,
        permissionDecision = "EXTERNAL_SEND_REQUIRES_CONFIRMATION",
        confirmationDecision = null,
        status = "CONFIRMATION_REQUIRED",
        startedAt = 1L,
        finishedAt = 1L,
        errorCode = "CONFIRMATION_REQUIRED",
        resultJson = "{\"confirmationRequired\":true,\"executed\":false}",
    )
}
