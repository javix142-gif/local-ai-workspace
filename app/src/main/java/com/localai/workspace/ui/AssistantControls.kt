package com.localai.workspace.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.localai.workspace.data.*
import com.localai.workspace.domain.tools.ChatToolSchemas
import com.localai.workspace.domain.tools.AndroidActionPlanner
import com.localai.workspace.ui.components.minimumAppTouchTarget
import com.localai.workspace.ui.theme.LocalAiSpacing
import com.localai.workspace.ui.theme.LocalAiRadii
import com.localai.workspace.ui.theme.LocalAiStatusColors
import com.localai.workspace.R

@Composable
fun AssistantControls(profile: AssistantProfile, toolsAvailable: Boolean, thinkingAvailable: Boolean,
    enabled: Boolean, onChange: (AssistantProfile) -> Unit) =
    AssistantControls(profile, toolsAvailable, thinkingAvailable, enabled, compact = false, onChange = onChange)

@Composable
fun AssistantControls(profile: AssistantProfile, toolsAvailable: Boolean, thinkingAvailable: Boolean,
    enabled: Boolean, compact: Boolean, onChange: (AssistantProfile) -> Unit) {
    var tools by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    val thinkingLabel = stringResource(when (profile.thinking) {
        ThinkingMode.OFF -> R.string.chat_thinking_off
        ThinkingMode.AUTO -> R.string.chat_thinking_auto
        ThinkingMode.ON -> R.string.chat_thinking_on
    })
    val toolsLabel = when {
        profile.tools.isEmpty() -> stringResource(R.string.chat_tools_off)
        profile.tools == ChatToolSchemas.defaults -> stringResource(R.string.chat_tools_auto)
        else -> stringResource(R.string.chat_tools_custom)
    }
    val summary = buildList {
        if (thinkingAvailable) add(stringResource(R.string.chat_thinking_summary, thinkingLabel))
        if (toolsAvailable) add(stringResource(R.string.chat_tools_summary, toolsLabel))
    }.joinToString(" · ")
    val optionsContentDescription = stringResource(R.string.chat_options_label) + " · " + summary

    if (thinkingAvailable || toolsAvailable) {
        Row(
            if (compact) Modifier else Modifier.fillMaxWidth().padding(horizontal = LocalAiSpacing.x3, vertical = LocalAiSpacing.x1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                if (compact) IconButton(
                    onClick = { options = true }, enabled = enabled,
                    modifier = Modifier.minimumAppTouchTarget().testTag("assistant-options-button")
                        .semantics { contentDescription = optionsContentDescription },
                ) { Icon(Icons.Default.Tune, contentDescription = null) }
                else TextButton(
                    onClick = { options = true },
                    enabled = enabled,
                    modifier = Modifier.minimumAppTouchTarget().testTag("assistant-options-button"),
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null)
                    Spacer(Modifier.width(LocalAiSpacing.x1))
                    Text(stringResource(R.string.chat_options_label))
                }
                DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                    if (compact && summary.isNotEmpty()) Text(summary,
                        Modifier.padding(horizontal = LocalAiSpacing.x4, vertical = LocalAiSpacing.x2)
                            .testTag("assistant-options-summary"),
                        style = MaterialTheme.typography.labelSmall)
                    if (thinkingAvailable) {
                        DropdownMenuItem(
                            modifier = Modifier.testTag("assistant-options-thinking"),
                            text = { Text(stringResource(R.string.chat_thinking_option, thinkingLabel)) },
                            onClick = { options = false; thinking = true },
                        )
                    }
                    if (toolsAvailable) {
                        DropdownMenuItem(
                            modifier = Modifier.testTag("assistant-options-tools"),
                            text = { Text(stringResource(R.string.chat_tools_option, toolsLabel)) },
                            onClick = { options = false; tools = true },
                        )
                    }
                }
            }
            if (!compact && summary.isNotEmpty()) {
                Text(
                    summary,
                    Modifier.weight(1f).padding(start = LocalAiSpacing.x2).testTag("assistant-options-summary"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (thinking) AlertDialog(onDismissRequest = { thinking = false }, title = { Text(stringResource(R.string.chat_thinking)) }, confirmButton = { TextButton(onClick = { thinking = false }) { Text(stringResource(R.string.common_done)) } }, text = {
        Column { ThinkingMode.entries.forEach { mode ->
            val label = stringResource(when (mode) {
                ThinkingMode.OFF -> R.string.chat_thinking_off
                ThinkingMode.AUTO -> R.string.chat_thinking_auto
                ThinkingMode.ON -> R.string.chat_thinking_on
            })
            TextButton(onClick = { onChange(profile.copy(thinking = mode)); thinking = false }) { Text(label) }
        } }
    })
    if (tools) AlertDialog(onDismissRequest = { tools = false }, title = { Text(stringResource(R.string.chat_tools)) }, confirmButton = { TextButton(onClick = { tools = false }) { Text(stringResource(R.string.common_done)) } }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) { TextButton(onClick = { onChange(profile.copy(tools = emptySet())) }) { Text(stringResource(R.string.chat_tools_off)) }
            TextButton(onClick = { onChange(profile.copy(tools = ChatToolSchemas.defaults)) }) { Text(stringResource(R.string.chat_tools_auto)) }
            ChatToolSchemas.available.filter { it != "python.execute" || com.localai.workspace.python.PythonAssets.available() }.forEach { id ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        id in profile.tools,
                        { checked -> onChange(profile.copy(tools = if(checked) profile.tools + id else profile.tools - id)) },
                        modifier = Modifier.testTag("assistant-tool-checkbox-$id"),
                    )
                    Text(id, Modifier.padding(start = LocalAiSpacing.x1))
                }
            }
        }
    })
}
@Composable
fun ToolExecutionCard(
    call: ToolCallEntity,
    onOpenAndroidAction: (ToolCallEntity) -> Unit = {},
    onDeclineAndroidAction: (ToolCallEntity) -> Unit = {},
    onReportAndroidActionComplete: (ToolCallEntity) -> Unit = {},
    onRequestPersonalDataPermission: (ToolCallEntity) -> Unit = {},
    personalDataPermissionGranted: Boolean = false,
    personalDataPermissionDenied: Boolean = false,
) {
    if (call.toolId == com.localai.workspace.domain.tools.AndroidActionPlanner.TOOL_ID) {
        AndroidActionExecutionCard(call, onOpenAndroidAction, onDeclineAndroidAction, onReportAndroidActionComplete)
        return
    }
    if (call.toolId in ChatToolSchemas.personalDataToolIds && call.status == "PERMISSION_REQUIRED") {
        PersonalDataPermissionCard(call, personalDataPermissionGranted, personalDataPermissionDenied, onRequestPersonalDataPermission)
        return
    }
    var details by remember { mutableStateOf(false) }
    val toolName = stringResource(when {
        call.toolId.startsWith("artifacts.") -> R.string.chat_tool_artifact
        call.toolId == "calculator.evaluate" -> R.string.chat_tool_calculator
        call.toolId == "python.execute" -> R.string.chat_tool_python
        call.toolId == "calendar.read" -> R.string.chat_tool_calendar
        call.toolId == "contacts.lookup" -> R.string.chat_tool_contacts
        else -> R.string.chat_tool_files
    })
    val toolStatus = if (call.status == "SUCCESS") stringResource(R.string.chat_tool_complete) else call.status
    val elapsed = (call.finishedAt ?: call.startedAt) - call.startedAt
    TextButton(onClick = { details = true }) { Text(stringResource(R.string.chat_tool_status, toolName, toolStatus)) }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text(call.toolId) }, confirmButton = { TextButton(onClick = { details = false }) { Text(stringResource(R.string.common_close)) } }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) { Text(stringResource(R.string.chat_tool_elapsed, toolStatus, elapsed)); Text(call.validatedArgumentsJson); Text(call.resultJson.orEmpty().take(16000)) }
    })
}

@Composable
private fun PersonalDataPermissionCard(
    call: ToolCallEntity,
    granted: Boolean,
    denied: Boolean,
    onRequestPermission: (ToolCallEntity) -> Unit,
) {
    val isCalendar = call.toolId == "calendar.read"
    val title = stringResource(if (isCalendar) R.string.chat_tool_calendar else R.string.chat_tool_contacts)
    val help = stringResource(if (isCalendar) R.string.personal_data_calendar_permission_help else R.string.personal_data_contacts_permission_help)
    Card(Modifier.fillMaxWidth().testTag("personal-data-permission-card-${call.id}")) {
        Column(Modifier.fillMaxWidth().padding(LocalAiSpacing.x3), verticalArrangement = Arrangement.spacedBy(LocalAiSpacing.x2)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(if (granted) stringResource(R.string.personal_data_permission_granted) else help, style = MaterialTheme.typography.bodySmall)
            if (!granted) {
                if (denied) Text(stringResource(R.string.personal_data_permission_denied), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Button(onClick = { onRequestPermission(call) }, modifier = Modifier.minimumAppTouchTarget().testTag("personal-data-request-permission-${call.id}")) {
                    Text(stringResource(R.string.personal_data_permission_request))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AndroidActionExecutionCard(
    call: ToolCallEntity,
    onOpen: (ToolCallEntity) -> Unit,
    onDecline: (ToolCallEntity) -> Unit,
    onReportComplete: (ToolCallEntity) -> Unit,
) {
    var review by remember(call.id) { mutableStateOf(false) }
    var details by remember(call.id) { mutableStateOf(false) }
    val action = remember(call.id, call.validatedArgumentsJson) {
        runCatching { com.localai.workspace.domain.tools.AndroidActionPlanner.prepare(call.validatedArgumentsJson) }.getOrNull()
    }
    val title = action?.let { androidActionTitle(it.kind) } ?: stringResource(R.string.android_action_invalid)
    val statusLabel = when (call.status) {
        "CONFIRMATION_REQUIRED" -> stringResource(R.string.android_action_status_prepared)
        "LAUNCHING" -> stringResource(R.string.android_action_status_opening)
        "OPENED" -> stringResource(R.string.android_action_status_opened)
        "CONFIRMED_BY_USER" -> stringResource(R.string.android_action_status_confirmed)
        "DECLINED_BY_USER" -> stringResource(R.string.android_action_status_declined)
        "OPEN_FAILED" -> stringResource(R.string.android_action_status_error)
        else -> call.status
    }
    val statusExplanation = when (call.status) {
        "CONFIRMATION_REQUIRED" -> stringResource(R.string.android_action_waiting)
        "LAUNCHING" -> stringResource(R.string.android_action_opening)
        "OPENED" -> stringResource(R.string.android_action_opened)
        "CONFIRMED_BY_USER" -> stringResource(R.string.android_action_user_reported_complete)
        "DECLINED_BY_USER" -> stringResource(R.string.android_action_declined)
        "OPEN_FAILED" -> stringResource(R.string.android_action_error_explanation)
        else -> call.status
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("android-action-card-${call.id}"),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(LocalAiRadii.card),
    ) {
        Column(Modifier.fillMaxWidth().padding(LocalAiSpacing.x4), verticalArrangement = Arrangement.spacedBy(LocalAiSpacing.x3)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LocalAiSpacing.x2)) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.padding(10.dp))
                }
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            }
            AndroidActionStatusPill(call.status, statusLabel)
            Text(statusExplanation, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (call.status == "OPEN_FAILED") {
                call.errorCode?.takeIf(String::isNotBlank)?.let { code ->
                    Text(
                        stringResource(R.string.android_action_error_code, code),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("android-action-error-code"),
                    )
                }
            }
            when (call.status) {
                "CONFIRMATION_REQUIRED" -> Button(
                    onClick = { review = true }, enabled = action != null,
                    modifier = Modifier.fillMaxWidth().minimumAppTouchTarget().testTag("android-action-review-button-${call.id}"),
                ) { Text(stringResource(R.string.android_action_review)) }
                "OPENED" -> OutlinedButton(
                    onClick = { onReportComplete(call) },
                    modifier = Modifier.fillMaxWidth().minimumAppTouchTarget().testTag("android-action-user-reported-complete"),
                ) { Text(stringResource(R.string.android_action_report_complete)) }
                else -> Unit
            }
            TextButton(onClick = { details = true }, modifier = Modifier.align(Alignment.End).minimumAppTouchTarget()) {
                Text(stringResource(R.string.common_details))
            }
        }
    }

    if (review) {
        val reviewed = action
        val notice = when (reviewed?.kind) {
            AndroidActionPlanner.CALENDAR_EVENT -> R.string.android_action_review_calendar_notice
            AndroidActionPlanner.ALARM, AndroidActionPlanner.TIMER -> R.string.android_action_review_clock_notice
            else -> R.string.android_action_review_generic_notice
        }
        ModalBottomSheet(
            onDismissRequest = { review = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.testTag("android-action-confirm-sheet-${call.id}"),
        ) {
            Column(
                Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
                    .heightIn(max = 720.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = LocalAiSpacing.x4, vertical = LocalAiSpacing.x2),
                verticalArrangement = Arrangement.spacedBy(LocalAiSpacing.x3),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LocalAiSpacing.x3)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.padding(14.dp))
                    }
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                }
                Text(stringResource(notice), Modifier.testTag("android-action-review-notice"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.padding(LocalAiSpacing.x3), verticalArrangement = Arrangement.spacedBy(LocalAiSpacing.x2)) {
                        if (reviewed != null) AndroidActionPlanner.displayFields(reviewed).forEach { (key, value) ->
                            Column(Modifier.fillMaxWidth()) {
                                Text(stringResource(androidActionFieldLabel(key)), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(displayAndroidActionValue(key, value), style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.testTag("android-action-value-$key"))
                            }
                        }
                    }
                }
                Button(
                    onClick = { review = false; onOpen(call) }, enabled = reviewed != null,
                    modifier = Modifier.fillMaxWidth().minimumAppTouchTarget().testTag("android-action-confirm-button"),
                ) { Text(stringResource(R.string.android_action_open_app)) }
                OutlinedButton(
                    onClick = { review = false; onDecline(call) },
                    modifier = Modifier.fillMaxWidth().minimumAppTouchTarget().testTag("android-action-decline-button"),
                ) { Text(stringResource(R.string.android_action_decline)) }
            }
        }
    }
    if (details) AlertDialog(
        onDismissRequest = { details = false },
        title = { Text(title) },
        confirmButton = { TextButton(onClick = { details = false }) { Text(stringResource(R.string.common_close)) } },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(LocalAiSpacing.x2)) {
                Text(stringResource(R.string.android_action_status_detail, statusExplanation))
                action?.let { AndroidActionPlanner.displayFields(it).forEach { (key, value) -> Text("${stringResource(androidActionFieldLabel(key))}: ${displayAndroidActionValue(key, value)}") } }
                if (call.status == "CONFIRMED_BY_USER") Text(stringResource(R.string.android_action_user_reported_note))
                call.errorCode?.takeIf(String::isNotBlank)?.let { Text(stringResource(R.string.android_action_error_code, it)) }
            }
        },
    )
}

@Composable
private fun AndroidActionStatusPill(status: String, label: String) {
    val statusColors = LocalAiStatusColors.current
    val foreground = when (status) {
        "CONFIRMATION_REQUIRED" -> statusColors.warning
        "OPEN_FAILED" -> MaterialTheme.colorScheme.error
        "DECLINED_BY_USER" -> statusColors.disabled
        else -> MaterialTheme.colorScheme.primary
    }
    val background = when (status) {
        "CONFIRMATION_REQUIRED" -> statusColors.warningContainer
        "OPEN_FAILED" -> MaterialTheme.colorScheme.errorContainer
        "DECLINED_BY_USER" -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val content = when (status) {
        "CONFIRMATION_REQUIRED" -> statusColors.onWarningContainer
        "OPEN_FAILED" -> MaterialTheme.colorScheme.onErrorContainer
        "DECLINED_BY_USER" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(shape = MaterialTheme.shapes.small, color = background, contentColor = content) {
        Row(
            Modifier.padding(horizontal = LocalAiSpacing.x2, vertical = LocalAiSpacing.x1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LocalAiSpacing.x1),
        ) {
            Surface(shape = CircleShape, color = foreground, modifier = Modifier.size(8.dp)) {}
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun androidActionTitle(kind: String): String = stringResource(when (kind) {
    com.localai.workspace.domain.tools.AndroidActionPlanner.ALARM -> R.string.android_action_alarm
    com.localai.workspace.domain.tools.AndroidActionPlanner.TIMER -> R.string.android_action_timer
    com.localai.workspace.domain.tools.AndroidActionPlanner.CALENDAR_EVENT -> R.string.android_action_calendar_event
    com.localai.workspace.domain.tools.AndroidActionPlanner.DIAL -> R.string.android_action_dial
    com.localai.workspace.domain.tools.AndroidActionPlanner.SMS_DRAFT -> R.string.android_action_sms_draft
    com.localai.workspace.domain.tools.AndroidActionPlanner.EMAIL_DRAFT -> R.string.android_action_email_draft
    com.localai.workspace.domain.tools.AndroidActionPlanner.MAP_SEARCH -> R.string.android_action_map_search
    com.localai.workspace.domain.tools.AndroidActionPlanner.SHARE_TEXT -> R.string.android_action_share_text
    com.localai.workspace.domain.tools.AndroidActionPlanner.SETTINGS -> R.string.android_action_settings
    else -> R.string.android_action_invalid
})

private fun androidActionFieldLabel(key: String): Int = when (key) {
    "hour" -> R.string.android_action_hour
    "minute" -> R.string.android_action_minute
    "seconds" -> R.string.android_action_seconds
    "title" -> R.string.android_action_field_title
    "startsAtEpochMillis" -> R.string.android_action_start_time
    "endsAtEpochMillis" -> R.string.android_action_end_time
    "location" -> R.string.android_action_location
    "description" -> R.string.android_action_description
    "phoneNumber" -> R.string.android_action_phone
    "body" -> R.string.android_action_body
    "recipient" -> R.string.android_action_recipient
    "subject" -> R.string.android_action_subject
    "query" -> R.string.android_action_query
    "text" -> R.string.android_action_text
    "setting" -> R.string.android_action_setting
    "message" -> R.string.android_action_message
    else -> R.string.android_action_invalid
}

private fun displayAndroidActionValue(key: String, value: String): String = if (key in setOf("startsAtEpochMillis", "endsAtEpochMillis")) {
    runCatching { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(value.toLong())) }.getOrDefault(value)
} else value
