package com.localai.workspace.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.mutableStateOf
import com.localai.workspace.data.ToolCallEntity
import com.localai.workspace.R
import com.localai.workspace.ui.LocalAiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger
import java.time.ZoneId
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class, qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidActionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun localized(id: Int, vararg args: Any): String = compose.activity.getString(id, *args)

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
        compose.onNodeWithTag("android-action-confirm-sheet-${call.id}").assertIsDisplayed()
        compose.onNodeWithText(localized(R.string.android_action_review_generic_notice)).assertIsDisplayed()
        compose.onNodeWithTag("android-action-value-phoneNumber").assertIsDisplayed()
        compose.onNodeWithTag("android-action-value-body").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, opened.get())
            assertEquals(0, declined.get())
        }
        compose.onNodeWithTag("android-action-confirm-button").performScrollTo()
            .assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, opened.get()) }
    }

    @Test fun calendarConfirmationExplainsThatTheEventIsNotSavedUntilTheUserCompletesIt() {
        val opened = AtomicInteger()
        val declined = AtomicInteger()
        val call = pendingCall(
            """{"action":"calendar_event","title":"H3b synthetic review","startsAtEpochMillis":1792605600000,"endsAtEpochMillis":1792609200000}""",
        )
        compose.setContent {
            LocalAiTheme {
                ToolExecutionCard(call, onOpenAndroidAction = { opened.incrementAndGet() }, onDeclineAndroidAction = { declined.incrementAndGet() })
            }
        }

        compose.onNodeWithTag("android-action-review-button-${call.id}").performClick()
        compose.onNodeWithTag("calendar-event-confirmation-title")
            .assertTextEquals(localized(R.string.android_action_create_event)).assertIsDisplayed()
        compose.onNodeWithTag("android-action-value-title").assertTextEquals("H3b synthetic review")
        compose.onNodeWithText(localized(R.string.android_action_start_time)).assertIsDisplayed()
        compose.onNodeWithText(localized(R.string.android_action_end_time)).assertIsDisplayed()
        compose.onNodeWithTag("android-action-calendar-start")
            .assertTextEquals(formatAndroidActionDateTime(1792605600000L)).assertIsDisplayed()
        compose.onNodeWithTag("android-action-calendar-end")
            .assertTextEquals(formatAndroidActionDateTime(1792609200000L)).assertIsDisplayed()
        compose.onNodeWithText(localized(R.string.android_action_review_calendar_notice)).assertIsDisplayed()
        compose.onNodeWithText(localized(R.string.android_action_open_calendar)).assertIsDisplayed()
        compose.onNodeWithText(localized(R.string.common_cancel)).assertIsDisplayed()
        compose.onNodeWithText("Calendario:").assertDoesNotExist()
        compose.onNodeWithText("Inicio (epoch ms)").assertDoesNotExist()
        compose.onNodeWithText("Fin (epoch ms)").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, opened.get()); assertEquals(0, declined.get()) }
        compose.onNodeWithTag("android-action-decline-button").performClick()
        compose.runOnIdle { assertEquals(0, opened.get()); assertEquals(1, declined.get()) }
        compose.waitForIdle()
        compose.onNodeWithTag("android-action-details-button").performClick()
        compose.onNodeWithTag("android-action-details-startsAtEpochMillis")
            .assertTextEquals("Inicio: ${formatAndroidActionDateTime(1792605600000L)}")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("android-action-details-endsAtEpochMillis")
            .assertTextEquals("Término: ${formatAndroidActionDateTime(1792609200000L)}")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Inicio (epoch ms)").assertDoesNotExist()
        compose.onNodeWithText("Fin (epoch ms)").assertDoesNotExist()
    }

    @Test fun calendarDateFormattingUsesSpanishAndTheRequestedTimeZone() {
        val spanish = Locale.forLanguageTag("es-ES")
        assertEquals(
            "21 de octubre de 2026 · 18:00",
            formatAndroidActionDateTime(1792605600000L, spanish, ZoneId.of("UTC")),
        )
        assertEquals(
            "21 de octubre de 2026 · 11:00",
            formatAndroidActionDateTime(1792605600000L, spanish, ZoneId.of("America/Los_Angeles")),
        )
        assertEquals(
            "20 de octubre de 2026 · 17:00",
            formatAndroidActionDateTime(1792540800000L, spanish, ZoneId.of("America/Los_Angeles")),
        )
    }

    @Test fun calendarProposalOpensOnlyAfterExplicitConfirmation() {
        val opened = AtomicInteger()
        val declined = AtomicInteger()
        val call = pendingCall(
            """{"action":"calendar_event","title":"Revisión H3b","startsAtEpochMillis":1792605600000,"endsAtEpochMillis":1792609200000}""",
        )
        compose.setContent {
            LocalAiTheme {
                ToolExecutionCard(call, onOpenAndroidAction = { opened.incrementAndGet() }, onDeclineAndroidAction = { declined.incrementAndGet() })
            }
        }
        compose.onNodeWithTag("android-action-review-button-${call.id}").performClick()
        compose.runOnIdle { assertEquals(0, opened.get()); assertEquals(0, declined.get()) }
        compose.onNodeWithTag("android-action-confirm-button").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, opened.get()); assertEquals(0, declined.get()) }
    }

    @Test fun openErrorKeepsHumanExplanationAndTechnicalCodeVisible() {
        val call = pendingCall("""{"action":"map_search","query":"Biblioteca central"}""")
            .copy(status = "OPEN_FAILED", errorCode = "NO_ACTIVITY_HANDLER")
        compose.setContent { LocalAiTheme { ToolExecutionCard(call) } }

        compose.onNodeWithText(localized(R.string.android_action_status_error)).assertIsDisplayed()
        compose.onNodeWithText(localized(R.string.android_action_error_explanation)).assertIsDisplayed()
        compose.onNodeWithTag("android-action-error-code").assertTextEquals(
            localized(R.string.android_action_error_code, "NO_ACTIVITY_HANDLER"),
        )
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

    @Test fun personalDataPermissionAppearsOnlyAfterTheToolReportsItIsRequired() {
        val requested = AtomicInteger()
        val granted = mutableStateOf(false)
        val denied = mutableStateOf(false)
        val call = ToolCallEntity(
            id = "contacts-permission-test", conversationId = "conversation-test", messageId = "message-test",
            toolId = "contacts.lookup", validatedArgumentsJson = "{\"privateArgumentsOmitted\":true}",
            permissionDecision = "LOCAL_READ_RUNTIME_PERMISSION", status = "PERMISSION_REQUIRED",
            startedAt = 1L, finishedAt = 1L, errorCode = "PERMISSION_REQUIRED",
        )
        compose.setContent {
            LocalAiTheme {
                ToolExecutionCard(
                    call,
                    onRequestPersonalDataPermission = { requested.incrementAndGet() },
                    personalDataPermissionGranted = granted.value,
                    personalDataPermissionDenied = denied.value,
                )
            }
        }
        compose.onNodeWithText("Para buscar números guardados en el dispositivo, Android necesita permiso para acceder a los contactos.").assertIsDisplayed()
        compose.onNodeWithTag("personal-data-request-permission-${call.id}").performClick()
        compose.runOnIdle { assertEquals(1, requested.get()); denied.value = true }
        compose.onNodeWithText("No se concedió el permiso y no se leyeron datos. Si Android ya no muestra el diálogo, habilita el permiso en Ajustes de la app.").assertIsDisplayed()
        compose.onNodeWithTag("personal-data-request-permission-${call.id}").performClick()
        compose.runOnIdle { assertEquals(2, requested.get()); denied.value = false; granted.value = true }
        compose.onNodeWithText("Permiso concedido. Vuelve a preguntar para consultar estos datos.").assertIsDisplayed()
        compose.onNodeWithTag("personal-data-request-permission-${call.id}").assertDoesNotExist()
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
