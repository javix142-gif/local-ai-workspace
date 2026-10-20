package com.localai.workspace.domain.tools

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import com.localai.workspace.security.ConfirmationPolicy
import com.localai.workspace.security.ToolDescriptor
import com.localai.workspace.security.ToolPermission
import org.json.JSONObject

/** A data-only proposal. Creating this value never opens an activity or changes device state. */
data class PreparedAndroidAction(val kind: String, val fields: JSONObject) {
    fun toJson(): String = JSONObject(fields.toString()).put("action", kind).toString()
}

/**
 * Strict, allow-listed Android handoff actions. Every result is an Intent for a user-facing
 * system/application screen; this class never invokes startActivity, writes a provider, dials,
 * sends a message, or schedules an alarm by itself.
 */
object AndroidActionPlanner {
    const val TOOL_ID = "android.action.prepare"

    const val ALARM = "alarm"
    const val TIMER = "timer"
    const val CALENDAR_EVENT = "calendar_event"
    const val DIAL = "dial"
    const val SMS_DRAFT = "sms_draft"
    const val EMAIL_DRAFT = "email_draft"
    const val MAP_SEARCH = "map_search"
    const val SHARE_TEXT = "share_text"
    const val SETTINGS = "settings"

    private val settingActions = mapOf(
        "wifi" to Settings.ACTION_WIFI_SETTINGS,
        "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "apps" to Settings.ACTION_APPLICATION_SETTINGS,
        "notifications" to Settings.ACTION_APP_NOTIFICATION_SETTINGS,
        "language" to Settings.ACTION_LOCALE_SETTINGS,
        "input" to Settings.ACTION_INPUT_METHOD_SETTINGS,
    )

    fun validate(arguments: JSONObject): String? = runCatching { prepare(arguments) }
        .exceptionOrNull()?.message ?: null

    fun prepare(json: String): PreparedAndroidAction = prepare(JSONObject(json))

    fun prepare(arguments: JSONObject): PreparedAndroidAction {
        require(arguments.opt("action") is String) { "ACTION_REQUIRED" }
        val action = arguments.getString("action")
        val allowed = when (action) {
            ALARM -> setOf("action", "hour", "minute", "message")
            TIMER -> setOf("action", "seconds", "message")
            CALENDAR_EVENT -> setOf("action", "title", "startsAtEpochMillis", "endsAtEpochMillis", "location", "description")
            DIAL -> setOf("action", "phoneNumber")
            SMS_DRAFT -> setOf("action", "phoneNumber", "body")
            EMAIL_DRAFT -> setOf("action", "recipient", "subject", "body")
            MAP_SEARCH -> setOf("action", "query")
            SHARE_TEXT -> setOf("action", "text")
            SETTINGS -> setOf("action", "setting")
            else -> throw IllegalArgumentException("ACTION_NOT_ALLOWLISTED")
        }
        require(arguments.keys().asSequence().all { it in allowed }) { "UNKNOWN_ACTION_ARGUMENT" }
        val normalized = JSONObject().put("action", action)
        when (action) {
            ALARM -> {
                val hour = integer(arguments, "hour", 0..23)
                val minute = integer(arguments, "minute", 0..59)
                normalized.put("hour", hour).put("minute", minute)
                optionalText(arguments, "message", 64)?.let { normalized.put("message", it) }
            }
            TIMER -> {
                normalized.put("seconds", integer(arguments, "seconds", 1..86_399))
                optionalText(arguments, "message", 64)?.let { normalized.put("message", it) }
            }
            CALENDAR_EVENT -> {
                normalized.put("title", requiredText(arguments, "title", 1, 120))
                val start = longInteger(arguments, "startsAtEpochMillis", 1L..Long.MAX_VALUE)
                val end = longInteger(arguments, "endsAtEpochMillis", 1L..Long.MAX_VALUE)
                require(end > start) { "CALENDAR_END_MUST_FOLLOW_START" }
                normalized.put("startsAtEpochMillis", start).put("endsAtEpochMillis", end)
                optionalText(arguments, "location", 200)?.let { normalized.put("location", it) }
                optionalText(arguments, "description", 500)?.let { normalized.put("description", it) }
            }
            DIAL -> normalized.put("phoneNumber", phone(arguments))
            SMS_DRAFT -> normalized.put("phoneNumber", phone(arguments)).put("body", requiredText(arguments, "body", 1, 2_000, allowLines = true))
            EMAIL_DRAFT -> {
                val recipient = requiredText(arguments, "recipient", 3, 254)
                require(EMAIL.matches(recipient)) { "EMAIL_INVALID" }
                normalized.put("recipient", recipient)
                normalized.put("subject", optionalText(arguments, "subject", 120).orEmpty())
                normalized.put("body", requiredText(arguments, "body", 1, 4_000, allowLines = true))
            }
            MAP_SEARCH -> normalized.put("query", requiredText(arguments, "query", 1, 240))
            SHARE_TEXT -> normalized.put("text", requiredText(arguments, "text", 1, 4_000, allowLines = true))
            SETTINGS -> {
                val setting = requiredText(arguments, "setting", 1, 32)
                require(setting in settingActions) { "SETTING_NOT_ALLOWLISTED" }
                normalized.put("setting", setting)
            }
        }
        return PreparedAndroidAction(action, normalized)
    }

    fun toIntent(action: PreparedAndroidAction, applicationPackageName: String? = null): Intent = when (action.kind) {
        ALARM -> Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, action.fields.getInt("hour"))
            .putExtra(AlarmClock.EXTRA_MINUTES, action.fields.getInt("minute"))
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            .apply { action.fields.optString("message").takeIf(String::isNotBlank)?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) } }
        TIMER -> Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, action.fields.getInt("seconds"))
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            .apply { action.fields.optString("message").takeIf(String::isNotBlank)?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) } }
        CALENDAR_EVENT -> Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, action.fields.getLong("startsAtEpochMillis"))
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, action.fields.getLong("endsAtEpochMillis"))
            putExtra(CalendarContract.Events.TITLE, action.fields.getString("title"))
            action.fields.optString("location").takeIf(String::isNotBlank)?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            action.fields.optString("description").takeIf(String::isNotBlank)?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
        }
        DIAL -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", action.fields.getString("phoneNumber"), null))
        SMS_DRAFT -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", action.fields.getString("phoneNumber"), null))
            .putExtra("sms_body", action.fields.getString("body"))
        EMAIL_DRAFT -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", action.fields.getString("recipient"), null))
            .putExtra(Intent.EXTRA_SUBJECT, action.fields.getString("subject"))
            .putExtra(Intent.EXTRA_TEXT, action.fields.getString("body"))
        MAP_SEARCH -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(action.fields.getString("query"))}"))
        SHARE_TEXT -> Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, action.fields.getString("text"))
        SETTINGS -> Intent(settingActions.getValue(action.fields.getString("setting"))).apply {
            if (action.fields.getString("setting") == "notifications") {
                require(!applicationPackageName.isNullOrBlank()) { "APPLICATION_PACKAGE_REQUIRED_FOR_NOTIFICATIONS" }
                putExtra(Settings.EXTRA_APP_PACKAGE, applicationPackageName)
            }
        }
        else -> throw IllegalArgumentException("ACTION_NOT_ALLOWLISTED")
    }

    fun displayFields(action: PreparedAndroidAction): List<Pair<String, String>> = when (action.kind) {
        ALARM -> listOf("hour" to action.fields.getInt("hour").toString().padStart(2, '0'), "minute" to action.fields.getInt("minute").toString().padStart(2, '0')) + optionalDisplay(action, "message")
        TIMER -> listOf("seconds" to action.fields.getInt("seconds").toString()) + optionalDisplay(action, "message")
        CALENDAR_EVENT -> listOf("title" to action.fields.getString("title"), "startsAtEpochMillis" to action.fields.getLong("startsAtEpochMillis").toString(), "endsAtEpochMillis" to action.fields.getLong("endsAtEpochMillis").toString()) + optionalDisplay(action, "location") + optionalDisplay(action, "description")
        DIAL -> listOf("phoneNumber" to action.fields.getString("phoneNumber"))
        SMS_DRAFT -> listOf("phoneNumber" to action.fields.getString("phoneNumber"), "body" to action.fields.getString("body"))
        EMAIL_DRAFT -> listOf("recipient" to action.fields.getString("recipient"), "subject" to action.fields.getString("subject"), "body" to action.fields.getString("body"))
        MAP_SEARCH -> listOf("query" to action.fields.getString("query"))
        SHARE_TEXT -> listOf("text" to action.fields.getString("text"))
        SETTINGS -> listOf("setting" to action.fields.getString("setting"))
        else -> emptyList()
    }

    private fun optionalDisplay(action: PreparedAndroidAction, key: String): List<Pair<String, String>> =
        action.fields.optString(key).takeIf(String::isNotBlank)?.let { listOf(key to it) }.orEmpty()

    private fun phone(arguments: JSONObject): String {
        val raw = requiredText(arguments, "phoneNumber", 3, 32)
        require(PHONE.matches(raw)) { "PHONE_NUMBER_INVALID" }
        val normalized = raw.filter { it.isDigit() || it == '+' }
        require(normalized.count(Char::isDigit) in 3..15 && normalized.drop(1).none { it == '+' }) { "PHONE_NUMBER_INVALID" }
        return normalized
    }

    private fun requiredText(arguments: JSONObject, key: String, min: Int, max: Int, allowLines: Boolean = false): String {
        val value = arguments.opt(key) as? String ?: throw IllegalArgumentException("${key.uppercase()}_REQUIRED")
        require(value.length in min..max && value.isNotBlank()) { "${key.uppercase()}_LENGTH_INVALID" }
        require(value.none { it.code < 0x20 && !(allowLines && it in setOf('\n', '\r', '\t')) || it.code == 0x7f }) { "${key.uppercase()}_CONTROL_CHARACTER" }
        return value
    }

    private fun optionalText(arguments: JSONObject, key: String, max: Int): String? =
        if (!arguments.has(key) || arguments.isNull(key)) null else requiredText(arguments, key, 0, max, allowLines = true)

    private fun integer(arguments: JSONObject, key: String, range: IntRange): Int {
        val number = arguments.opt(key) as? Number ?: throw IllegalArgumentException("${key.uppercase()}_INTEGER_REQUIRED")
        val asDouble = number.toDouble()
        require(asDouble.isFinite() && asDouble % 1.0 == 0.0 && asDouble >= range.first && asDouble <= range.last) { "${key.uppercase()}_OUT_OF_RANGE" }
        return asDouble.toInt()
    }

    private fun longInteger(arguments: JSONObject, key: String, range: LongRange): Long {
        val number = arguments.opt(key) as? Number ?: throw IllegalArgumentException("${key.uppercase()}_INTEGER_REQUIRED")
        val asDouble = number.toDouble()
        require(asDouble.isFinite() && asDouble % 1.0 == 0.0 && asDouble >= range.first.toDouble() && asDouble <= range.last.toDouble()) { "${key.uppercase()}_OUT_OF_RANGE" }
        return number.toLong()
    }

    private val PHONE = Regex("^\\+?[0-9 ()-]{3,32}$")
    private val EMAIL = Regex("^[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+$")
}

/** The model can only prepare a proposal; ToolPolicy always returns NeedsConfirmation. */
class AndroidActionTool : LocalTool {
    override val descriptor = ToolDescriptor(
        id = AndroidActionPlanner.TOOL_ID,
        permissions = setOf(ToolPermission.EXTERNAL_SIDE_EFFECT),
        confirmationPolicy = ConfirmationPolicy.ALWAYS,
    )

    override fun validate(arguments: JSONObject): String? = AndroidActionPlanner.validate(arguments)

    override suspend fun execute(arguments: JSONObject, context: ToolContext): String =
        AndroidActionPlanner.prepare(arguments).toJson()
}
