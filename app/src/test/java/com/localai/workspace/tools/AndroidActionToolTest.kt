package com.localai.workspace.tools

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import com.localai.workspace.capabilities.Capability
import com.localai.workspace.capabilities.CapabilityPolicy
import com.localai.workspace.domain.tools.AndroidActionPlanner
import com.localai.workspace.domain.tools.ToolContext
import com.localai.workspace.domain.tools.ToolExecutionResult
import com.localai.workspace.domain.tools.ToolRegistry
import com.localai.workspace.security.ConfirmationPolicy
import com.localai.workspace.security.ToolDescriptor
import com.localai.workspace.security.ToolPermission
import com.localai.workspace.security.ToolRequest
import com.localai.workspace.security.TrustOrigin
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class AndroidActionToolTest {
    @Test fun alarmTimerCalendarAndExternalDestinationsAreTypedAllowListedIntents() {
        val alarm = AndroidActionPlanner.toIntent(AndroidActionPlanner.prepare("""{"action":"alarm","hour":7,"minute":5,"message":"Wake up"}"""))
        assertEquals(AlarmClock.ACTION_SET_ALARM, alarm.action)
        assertEquals(7, alarm.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(5, alarm.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
        assertFalse(alarm.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))

        val timer = AndroidActionPlanner.toIntent(AndroidActionPlanner.prepare("""{"action":"timer","seconds":90}"""))
        assertEquals(AlarmClock.ACTION_SET_TIMER, timer.action)
        assertEquals(90, timer.getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
        assertFalse(timer.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))

        val calendar = AndroidActionPlanner.toIntent(AndroidActionPlanner.prepare("""{"action":"calendar_event","title":"Review","startsAtEpochMillis":1800000000000,"endsAtEpochMillis":1800003600000,"location":"Room 2"}"""))
        assertEquals(Intent.ACTION_INSERT, calendar.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, calendar.data)
        assertEquals("Review", calendar.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals(1_800_000_000_000L, calendar.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
        assertEquals("Room 2", calendar.getStringExtra(CalendarContract.Events.EVENT_LOCATION))

        val dial = AndroidActionPlanner.toIntent(AndroidActionPlanner.prepare("""{"action":"dial","phoneNumber":"+1 (202) 555-0100"}"""))
        assertEquals(Intent.ACTION_DIAL, dial.action)
        assertEquals(Uri.fromParts("tel", "+12025550100", null), dial.data)

        val sms = AndroidActionPlanner.toIntent(AndroidActionPlanner.prepare("""{"action":"sms_draft","phoneNumber":"202-555-0100","body":"Hello"}"""))
        assertEquals(Intent.ACTION_SENDTO, sms.action)
        assertEquals("smsto", sms.data?.scheme)
        assertEquals("Hello", sms.getStringExtra("sms_body"))

        val email = AndroidActionPlanner.toIntent(AndroidActionPlanner.prepare("""{"action":"email_draft","recipient":"person@example.com","subject":"Hello","body":"Draft"}"""))
        assertEquals(Intent.ACTION_SENDTO, email.action)
        assertEquals("mailto", email.data?.scheme)
        assertEquals("Draft", email.getStringExtra(Intent.EXTRA_TEXT))

        val map = AndroidActionPlanner.toIntent(AndroidActionPlanner.prepare("""{"action":"map_search","query":"Café Central"}"""))
        assertEquals(Intent.ACTION_VIEW, map.action)
        assertEquals("geo", map.data?.scheme)
        assertTrue(map.data.toString().contains("Caf%C3%A9"))

        val share = AndroidActionPlanner.toIntent(AndroidActionPlanner.prepare("""{"action":"share_text","text":"Hello"}"""))
        assertEquals(Intent.ACTION_SEND, share.action)
        assertEquals("text/plain", share.type)

        val settings = AndroidActionPlanner.toIntent(AndroidActionPlanner.prepare("""{"action":"settings","setting":"wifi"}"""))
        assertEquals(Settings.ACTION_WIFI_SETTINGS, settings.action)
        val notifications = AndroidActionPlanner.toIntent(AndroidActionPlanner.prepare("""{"action":"settings","setting":"notifications"}"""), "com.localai.workspace")
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notifications.action)
        assertEquals("com.localai.workspace", notifications.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertNotNull(AndroidActionPlanner.validate(JSONObject("""{"action":"settings","setting":"wifi","component":"arbitrary"}""")))
    }

    @Test fun invalidAndOutOfScopeInputsAreRejectedWithoutCreatingAnIntent() {
        val invalid = listOf(
            """{"action":"call","phoneNumber":"123456"}""",
            """{"action":"settings","setting":"android.intent.action.DELETE"}""",
            """{"action":"dial","phoneNumber":"12345","package":"com.evil.app"}""",
            """{"action":"alarm","hour":24,"minute":0}""",
            """{"action":"calendar_event","title":"bad","startsAtEpochMillis":2000,"endsAtEpochMillis":1000}""",
            """{"action":"email_draft","recipient":"not-an-email","body":"x"}""",
            """{"action":"share_text","text":"\u0001hidden"}""",
        )
        invalid.forEach { assertNotNull("Expected rejection for $it", AndroidActionPlanner.validate(JSONObject(it))) }
    }

    @Test fun modelToolCallReturnsConfirmationWithoutExecutingExternalAction() = runBlocking {
        var executionCount = 0
        val id = AndroidActionPlanner.TOOL_ID
        val tool = object : com.localai.workspace.domain.tools.LocalTool {
            override val descriptor = ToolDescriptor(id, setOf(ToolPermission.EXTERNAL_SIDE_EFFECT), ConfirmationPolicy.ALWAYS)
            override fun validate(arguments: JSONObject): String? = AndroidActionPlanner.validate(arguments)
            override suspend fun execute(arguments: JSONObject, context: ToolContext): String {
                executionCount++
                return AndroidActionPlanner.prepare(arguments).toJson()
            }
        }
        val state = ToolRegistry.ToolLoopState()
        val result = ToolRegistry(listOf(tool)).execute(
            ToolRequest(id, """{"action":"dial","phoneNumber":"+12025550100"}""", TrustOrigin.MODEL),
            setOf(id), ToolContext("project", "conversation", File("/tmp")), state,
        )
        assertTrue(result is ToolExecutionResult.NeedsConfirmation)
        assertEquals(0, executionCount)
        assertEquals(1, state.calls)

        val unavailable = ToolRegistry(listOf(tool)).execute(
            ToolRequest(id, """{"action":"dial","phoneNumber":"+12025550100"}""", TrustOrigin.MODEL),
            setOf(id), ToolContext("project", "conversation", File("/tmp"), CapabilityPolicy.localCapabilities), ToolRegistry.ToolLoopState(),
        )
        assertTrue(unavailable is ToolExecutionResult.Rejected)
        assertEquals("CAPABILITY_UNAVAILABLE", (unavailable as ToolExecutionResult.Rejected).reason)
    }

    @Test fun actionCapabilityIsVisibleButStillMarkedForConfirmationAndCanBeTurnedOff() {
        val id = AndroidActionPlanner.TOOL_ID
        val policy = CapabilityPolicy()
        val listed = policy.tools(setOf(id), com.localai.workspace.skills.SkillDefinition.STANDARD_TOOLS, emptyList()).single { it.toolId == id }
        assertTrue(listed.available)
        assertEquals("CONFIRMATION_REQUIRED", listed.reason)
        val off = policy.tools(emptySet(), com.localai.workspace.skills.SkillDefinition.STANDARD_TOOLS, emptyList()).single { it.toolId == id }
        assertFalse(off.available)
        val excludedByAgent = policy.tools(setOf(id), com.localai.workspace.skills.SkillDefinition.STANDARD_TOOLS - id, emptyList()).single { it.toolId == id }
        assertFalse(excludedByAgent.available)
        assertEquals(setOf(Capability.EXTERNAL_SEND), CapabilityPolicy.requirements(id))
    }
}
