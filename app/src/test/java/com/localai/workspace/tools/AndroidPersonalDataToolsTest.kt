package com.localai.workspace.tools

import android.app.Application
import android.Manifest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.localai.workspace.capabilities.Capability
import com.localai.workspace.capabilities.CapabilityPolicy
import com.localai.workspace.data.AssistantRouting
import com.localai.workspace.domain.tools.*
import com.localai.workspace.security.ToolRequest
import com.localai.workspace.security.TrustOrigin
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class AndroidPersonalDataToolsTest {
    @Test fun calendarArgumentsAreExactAndBoundedToThirtyOneDays() {
        val reader = FakeReader()
        val tool = CalendarReadTool(reader)
        val start = 1_800_000_000_000L
        val valid = JSONObject().put("startEpochMillis", start).put("endEpochMillis", start + CalendarReadTool.MAX_RANGE_MILLIS)
        assertEquals("parsed bounded interval", start to start + CalendarReadTool.MAX_RANGE_MILLIS, CalendarReadTool.calendarRange(valid))
        assertNull(tool.validate(valid))
        assertEquals("CALENDAR_RANGE_INVALID", tool.validate(JSONObject().put("startEpochMillis", start).put("endEpochMillis", start + CalendarReadTool.MAX_RANGE_MILLIS + 1)))
        assertEquals("CALENDAR_ARGUMENTS_UNKNOWN", tool.validate(JSONObject().put("startEpochMillis", start).put("endEpochMillis", start + 1).put("limit", 100)))
        assertEquals("CALENDAR_EPOCH_MILLIS_REQUIRED", tool.validate(JSONObject().put("startEpochMillis", 1.5).put("endEpochMillis", start)))
    }

    @Test fun calendarPermissionIsCheckedAtExecutionAndResultsAreUntrustedAndBounded() = runBlocking {
        val start = 1_800_000_000_000L
        val reader = FakeReader(calendarAllowed = true).apply {
            events = (0 until 45).map { LocalCalendarEvent("Event $it", start + it, start + it + 1, false, null) }
        }
        val output = execute(CalendarReadTool(reader), "calendar.read", JSONObject()
            .put("startEpochMillis", start).put("endEpochMillis", start + CalendarReadTool.MAX_RANGE_MILLIS).toString())
        val json = JSONObject(output)
        assertEquals("ANDROID_CALENDAR", json.getString("source"))
        assertEquals("UNTRUSTED_LOCAL_DATA", json.getString("trust"))
        assertEquals(40, json.getInt("returnedCount"))
        assertEquals(40, json.getJSONArray("events").length())
        assertTrue(json.getBoolean("truncated"))
        assertEquals(1, reader.calendarQueries)
        assertEquals(start, reader.lastRange!!.first)
    }

    @Test fun missingCalendarPermissionDoesNotQueryProvider() = runBlocking {
        val start = 1_800_000_000_000L
        val reader = FakeReader(calendarAllowed = false)
        val result = executeResult(CalendarReadTool(reader), "calendar.read", JSONObject()
            .put("startEpochMillis", start).put("endEpochMillis", start + 60_000).toString())
        assertTrue(result is ToolExecutionResult.Failed)
        assertEquals("PERMISSION_REQUIRED", (result as ToolExecutionResult.Failed).code)
        assertEquals(0, reader.calendarQueries)
        assertEquals(setOf(Capability.LOCAL_READ), CapabilityPolicy.requirements("calendar.read"))
    }

    @Test fun missingContactsPermissionDoesNotQueryProvider() = runBlocking {
        val reader = FakeReader(contactsAllowed = false)
        val result = executeResult(ContactsLookupTool(reader), "contacts.lookup", JSONObject().put("query", "Ana").toString())
        assertTrue(result is ToolExecutionResult.Failed)
        assertEquals("PERMISSION_REQUIRED", (result as ToolExecutionResult.Failed).code)
        assertNull(reader.lastContactQuery)
        assertEquals(setOf(Capability.LOCAL_READ), CapabilityPolicy.requirements("contacts.lookup"))
    }

    @Test fun contactsLookupIsBoundedAndReturnsOnlyLocalUntrustedMatches() = runBlocking {
        val reader = FakeReader(contactsAllowed = true).apply {
            contacts = (0 until 24).map { LocalContactMatch("Contact $it", listOf("+1202555%04d".format(it))) }
        }
        val output = execute(ContactsLookupTool(reader), "contacts.lookup", JSONObject().put("query", "Ana").toString())
        val json = JSONObject(output)
        assertEquals("ANDROID_CONTACTS", json.getString("source"))
        assertEquals("UNTRUSTED_LOCAL_DATA", json.getString("trust"))
        assertEquals(20, json.getInt("returnedCount"))
        assertEquals(20, json.getJSONArray("contacts").length())
        assertTrue(json.getBoolean("truncated"))
        assertEquals("+12025550000", json.getJSONArray("contacts").getJSONObject(0).getJSONArray("phoneNumbers").getString(0))
        assertEquals("Ana", reader.lastContactQuery)
        assertEquals("CONTACT_QUERY_LENGTH_INVALID", ContactsLookupTool(reader).validate(JSONObject().put("query", "  ")))
        assertEquals("CONTACT_QUERY_LENGTH_INVALID", ContactsLookupTool(reader).validate(JSONObject().put("query", "A".padStart(ContactsLookupTool.MAX_QUERY_CHARS + 1))))
    }

    @Test fun personalToolHistoryOmitsSearchTermsNamesAndPhoneNumbers() {
        val storedArguments = PersonalDataToolHistory.persistedArguments("contacts.lookup", "{\"query\":\"Ana Example\"}")
        val storedResult = PersonalDataToolHistory.persistedResult("contacts.lookup", JSONObject()
            .put("status", "SUCCESS")
            .put("result", JSONObject().put("source", "ANDROID_CONTACTS").put("returnedCount", 1).put("truncated", false)
                .put("contacts", JSONArray().put(JSONObject().put("displayName", "Ana Example").put("phoneNumbers", JSONArray().put("+12025550100")))))
        )
        assertFalse(storedArguments.contains("Ana"))
        assertFalse(storedResult.contains("Ana Example"))
        assertFalse(storedResult.contains("+12025550100"))
        assertTrue(storedResult.contains("personalDataOmittedFromToolHistory"))
        assertTrue(storedResult.contains("returnedCount"))
    }

    @Test fun toolSchemasAndDeterministicRoutingExposeOnlyRelevantPersonalReadsAndActionHandoffs() {
        assertEquals(setOf(Capability.LOCAL_READ), CapabilityPolicy.requirements("contacts.lookup"))
        for (id in listOf("calendar.read", "contacts.lookup")) {
            val schema = JSONObject(ChatToolSchemas.schema(id))
            assertTrue(id in ChatToolSchemas.defaults)
            assertEquals(id, schema.getString("name"))
            assertTrue(schema.getString("description").length < 500)
            assertEquals("object", schema.getJSONObject("parameters").getString("type"))
        }
        val selected = setOf("calendar.read", "contacts.lookup", AndroidActionPlanner.TOOL_ID)
        assertEquals(setOf("calendar.read"), AssistantRouting.tools(selected, "¿Qué tengo mañana?", false))
        assertEquals(setOf("contacts.lookup"), AssistantRouting.tools(selected, "Busca el teléfono de Ana", false))
        assertTrue(AssistantRouting.tools(selected, "Busca el correo de Ana", false).isEmpty())
        assertEquals(setOf(AndroidActionPlanner.TOOL_ID), AssistantRouting.tools(selected, "Pon una alarma mañana a las siete", false))
        assertTrue(AssistantRouting.tools(selected, "Hola", false).isEmpty())
    }

    private suspend fun execute(tool: LocalTool, id: String, args: String): String =
        (executeResult(tool, id, args) as ToolExecutionResult.Success).output

    private suspend fun executeResult(tool: LocalTool, id: String, args: String): ToolExecutionResult = ToolRegistry(listOf(tool)).execute(
        ToolRequest(id, args, TrustOrigin.MODEL), setOf(id), ToolContext("project", "conversation", File("/tmp")), ToolRegistry.ToolLoopState(),
    )

    private class FakeReader(
        private val calendarAllowed: Boolean = false,
        private val contactsAllowed: Boolean = false,
    ) : AndroidPersonalDataReader {
        var events: List<LocalCalendarEvent> = emptyList()
        var contacts: List<LocalContactMatch> = emptyList()
        var calendarQueries = 0
        var lastRange: Pair<Long, Long>? = null
        var lastContactQuery: String? = null

        override fun hasPermission(permission: String): Boolean = when (permission) {
            Manifest.permission.READ_CALENDAR -> calendarAllowed
            Manifest.permission.READ_CONTACTS -> contactsAllowed
            else -> false
        }

        override suspend fun calendarInstances(startEpochMillis: Long, endEpochMillis: Long): PersonalDataBatch<LocalCalendarEvent> {
            calendarQueries++
            lastRange = startEpochMillis to endEpochMillis
            return PersonalDataBatch(events)
        }

        override suspend fun matchingPhoneContacts(query: String): PersonalDataBatch<LocalContactMatch> {
            lastContactQuery = query
            return PersonalDataBatch(contacts)
        }
    }
}
