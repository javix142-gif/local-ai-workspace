package com.localai.workspace.domain.tools

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.localai.workspace.security.ConfirmationPolicy
import com.localai.workspace.security.ToolDescriptor
import com.localai.workspace.security.ToolPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.TimeZone

data class PersonalDataBatch<T>(val records: List<T>, val truncated: Boolean = false)

data class LocalCalendarEvent(
    val title: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val allDay: Boolean,
    val location: String?,
)

data class LocalContactMatch(val displayName: String, val phoneNumbers: List<String>)

/** Narrow query boundary; the platform adapter reads only the columns needed by these tools. */
interface AndroidPersonalDataReader {
    fun hasPermission(permission: String): Boolean
    suspend fun calendarInstances(startEpochMillis: Long, endEpochMillis: Long): PersonalDataBatch<LocalCalendarEvent>
    suspend fun matchingPhoneContacts(query: String): PersonalDataBatch<LocalContactMatch>
}

class CalendarReadTool(private val reader: AndroidPersonalDataReader) : LocalTool {
    override val timeoutMs = 12_000L
    override val descriptor = ToolDescriptor("calendar.read", setOf(ToolPermission.READ_LOCAL), ConfirmationPolicy.NEVER)

    override fun validate(arguments: JSONObject): String? = if (calendarRange(arguments) != null) null else calendarError(arguments)

    override suspend fun execute(arguments: JSONObject, context: ToolContext): String {
        val range = calendarRange(arguments) ?: throw ToolExecutionException("CALENDAR_RANGE_INVALID", "Supply a valid calendar interval of at most 31 days.")
        if (!reader.hasPermission(Manifest.permission.READ_CALENDAR)) {
            throw ToolExecutionException("PERMISSION_REQUIRED", "Allow calendar access from the in-chat permission card, then ask again.")
        }
        val batch = try {
            reader.calendarInstances(range.first, range.second)
        } catch (_: SecurityException) {
            throw ToolExecutionException("PERMISSION_REQUIRED", "Calendar access is unavailable. Review the Android permission and ask again.")
        }
        return boundedResult(
            source = "ANDROID_CALENDAR",
            key = "events",
            records = batch.records.take(MAX_RECORDS).map { event ->
                JSONObject()
                    .put("title", event.title.take(MAX_FIELD_CHARS))
                    .put("startEpochMillis", event.startEpochMillis)
                    .put("endEpochMillis", event.endEpochMillis)
                    .put("allDay", event.allDay)
                    .put("location", event.location?.take(MAX_FIELD_CHARS))
                    .put("fieldTextTruncated", event.title.length > MAX_FIELD_CHARS || (event.location?.length ?: 0) > MAX_FIELD_CHARS)
            },
            initiallyTruncated = batch.truncated || batch.records.size > MAX_RECORDS,
            metadata = JSONObject()
                .put("startEpochMillis", range.first)
                .put("endEpochMillis", range.second)
                .put("timeZone", TimeZone.getDefault().id),
        )
    }

    private fun calendarError(arguments: JSONObject): String = when {
        arguments.keys().asSequence().any { it !in CALENDAR_ARGUMENTS } -> "CALENDAR_ARGUMENTS_UNKNOWN"
        exactLong(arguments.opt("startEpochMillis")) == null || exactLong(arguments.opt("endEpochMillis")) == null -> "CALENDAR_EPOCH_MILLIS_REQUIRED"
        else -> "CALENDAR_RANGE_INVALID"
    }

    companion object {
        const val MAX_RECORDS = 40
        const val MAX_RANGE_MILLIS = 31L * 24 * 60 * 60 * 1_000
        const val MIN_EPOCH_MILLIS = -2_208_988_800_000L // 1900-01-01
        const val MAX_EPOCH_MILLIS = 4_102_444_800_000L // 2100-01-01
        private val CALENDAR_ARGUMENTS = setOf("startEpochMillis", "endEpochMillis")

        internal fun calendarRange(arguments: JSONObject): Pair<Long, Long>? {
            if (arguments.length() != 2 || arguments.keys().asSequence().any { it !in CALENDAR_ARGUMENTS }) return null
            val start = exactLong(arguments.opt("startEpochMillis")) ?: return null
            val end = exactLong(arguments.opt("endEpochMillis")) ?: return null
            if (start !in MIN_EPOCH_MILLIS..MAX_EPOCH_MILLIS || end !in MIN_EPOCH_MILLIS..MAX_EPOCH_MILLIS) return null
            if (end <= start || end - start > MAX_RANGE_MILLIS) return null
            return start to end
        }
    }
}

class ContactsLookupTool(private val reader: AndroidPersonalDataReader) : LocalTool {
    override val timeoutMs = 12_000L
    override val descriptor = ToolDescriptor("contacts.lookup", setOf(ToolPermission.READ_LOCAL), ConfirmationPolicy.NEVER)

    override fun validate(arguments: JSONObject): String? {
        if (arguments.keys().asSequence().any { it != "query" }) return "CONTACT_QUERY_ARGUMENTS_UNKNOWN"
        val query = arguments.opt("query") as? String ?: return "CONTACT_QUERY_REQUIRED"
        return when {
            query.length !in 1..MAX_QUERY_CHARS || query.isBlank() -> "CONTACT_QUERY_LENGTH_INVALID"
            query.any { it.code < 0x20 || it.code == 0x7f } -> "CONTACT_QUERY_CONTROL_CHARACTER"
            else -> null
        }
    }

    override suspend fun execute(arguments: JSONObject, context: ToolContext): String {
        val query = arguments.getString("query").trim()
        if (!reader.hasPermission(Manifest.permission.READ_CONTACTS)) {
            throw ToolExecutionException("PERMISSION_REQUIRED", "Allow contacts access from the in-chat permission card, then ask again.")
        }
        val batch = try {
            reader.matchingPhoneContacts(query)
        } catch (_: SecurityException) {
            throw ToolExecutionException("PERMISSION_REQUIRED", "Contacts access is unavailable. Review the Android permission and ask again.")
        }
        return boundedResult(
            source = "ANDROID_CONTACTS",
            key = "contacts",
            records = batch.records.take(MAX_CONTACTS).map { contact ->
                JSONObject()
                    .put("displayName", contact.displayName.take(MAX_FIELD_CHARS))
                    .put("phoneNumbers", JSONArray(contact.phoneNumbers.take(MAX_PHONES_PER_CONTACT).map { it.take(MAX_PHONE_CHARS) }))
                    .put("fieldTextTruncated", contact.displayName.length > MAX_FIELD_CHARS || contact.phoneNumbers.size > MAX_PHONES_PER_CONTACT || contact.phoneNumbers.any { it.length > MAX_PHONE_CHARS })
            },
            initiallyTruncated = batch.truncated || batch.records.size > MAX_CONTACTS,
            metadata = JSONObject(),
        )
    }

    companion object {
        const val MAX_QUERY_CHARS = 80
        const val MAX_CONTACTS = 20
        private const val MAX_PHONES_PER_CONTACT = 3
        private const val MAX_PHONE_CHARS = 64
    }
}

/** Keeps personal query terms and returned names/numbers/events out of tool-call history. */
internal object PersonalDataToolHistory {
    val ids = setOf("calendar.read", "contacts.lookup")

    fun persistedArguments(toolId: String, raw: String): String =
        if (toolId in ids) "{\"privateArgumentsOmitted\":true}" else raw.take(12_000)

    fun persistedResult(toolId: String, response: JSONObject): String {
        if (toolId !in ids) return response.toString().take(16_000)
        val result = response.optJSONObject("result")
        val summary = JSONObject()
            .put("status", response.optString("status"))
            .put("personalDataOmittedFromToolHistory", true)
        if (result != null) {
            summary.put("source", result.optString("source"))
                .put("returnedCount", result.optInt("returnedCount"))
                .put("truncated", result.optBoolean("truncated"))
        } else response.optString("error").takeIf(String::isNotBlank)?.let { summary.put("error", it) }
        return summary.toString()
    }
}

/** Android ContentResolver adapter. Cursor scans and result size are bounded; no values are logged. */
class ContentResolverPersonalDataReader(context: Context) : AndroidPersonalDataReader {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    override fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    override suspend fun calendarInstances(startEpochMillis: Long, endEpochMillis: Long): PersonalDataBatch<LocalCalendarEvent> = withContext(Dispatchers.IO) {
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startEpochMillis)
        ContentUris.appendId(builder, endEpochMillis)
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val records = ArrayList<LocalCalendarEvent>(CalendarReadTool.MAX_RECORDS + 1)
        var truncated = false
        resolver.query(builder.build(), projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                if (records.size == CalendarReadTool.MAX_RECORDS) { truncated = true; break }
                records += LocalCalendarEvent(
                    title = cursor.getString(0).orEmpty(),
                    startEpochMillis = cursor.getLong(1),
                    endEpochMillis = cursor.getLong(2),
                    allDay = cursor.getInt(3) != 0,
                    location = cursor.getString(4)?.takeIf(String::isNotBlank),
                )
            }
        }
        PersonalDataBatch(records, truncated)
    }

    override suspend fun matchingPhoneContacts(query: String): PersonalDataBatch<LocalContactMatch> = withContext(Dispatchers.IO) {
        val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(query))
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        data class ContactBuilder(val displayName: String, val numbers: LinkedHashSet<String> = linkedSetOf())
        val grouped = linkedMapOf<Long, ContactBuilder>()
        var scanned = 0
        var truncated = false
        resolver.query(uri, projection, null, null, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                scanned++
                if (scanned > MAX_CONTACT_ROWS_SCANNED) { truncated = true; break }
                val id = cursor.getLong(0)
                if (id !in grouped && grouped.size >= ContactsLookupTool.MAX_CONTACTS) { truncated = true; break }
                val name = cursor.getString(1).orEmpty()
                val number = cursor.getString(2).orEmpty()
                if (name.isBlank() || number.isBlank()) continue
                val contact = grouped.getOrPut(id) { ContactBuilder(name) }
                if (contact.numbers.size < MAX_PHONES_PER_CONTACT) contact.numbers += number
                else truncated = true
            }
        }
        PersonalDataBatch(grouped.values.map { LocalContactMatch(it.displayName, it.numbers.toList()) }, truncated)
    }

    private companion object {
        const val MAX_CONTACT_ROWS_SCANNED = 100
        const val MAX_PHONES_PER_CONTACT = 3
    }
}

private fun boundedResult(
    source: String,
    key: String,
    records: List<JSONObject>,
    initiallyTruncated: Boolean,
    metadata: JSONObject,
): String {
    val bounded = JSONArray(records)
    var truncated = initiallyTruncated
    while (true) {
        val result = JSONObject()
            .put("source", source)
            .put("trust", "UNTRUSTED_LOCAL_DATA")
            .put("returnedCount", bounded.length())
            .put("truncated", truncated)
            .put(key, bounded)
        val keys = metadata.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            result.put(name, metadata.get(name))
        }
        val encoded = result.toString()
        if (encoded.toByteArray(Charsets.UTF_8).size <= MAX_RESULT_BYTES) return encoded
        if (bounded.length() == 0) throw ToolExecutionException("PERSONAL_DATA_RESULT_TOO_LARGE", "The local result exceeded its safe response budget; no partial record was returned.")
        bounded.remove(bounded.length() - 1)
        truncated = true
    }
}

private fun exactLong(value: Any?): Long? = (value as? Number)?.let {
    runCatching { BigDecimal(it.toString()).longValueExact() }.getOrNull()
}

private const val MAX_FIELD_CHARS = 512
private const val MAX_PHONE_CHARS = 64
private const val MAX_PHONES_PER_CONTACT = 3
private const val MAX_RESULT_BYTES = 12_000
