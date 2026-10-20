package com.localai.workspace.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.localai.workspace.ui.components.minimumAppTouchTarget
import com.localai.workspace.ui.theme.LocalAiSpacing
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
fun ToolExecutionCard(call: ToolCallEntity) {
    var details by remember { mutableStateOf(false) }
    val toolName = stringResource(when {
        call.toolId.startsWith("artifacts.") -> R.string.chat_tool_artifact
        call.toolId == "calculator.evaluate" -> R.string.chat_tool_calculator
        call.toolId == "python.execute" -> R.string.chat_tool_python
        else -> R.string.chat_tool_files
    })
    val toolStatus = if (call.status == "SUCCESS") stringResource(R.string.chat_tool_complete) else call.status
    val elapsed = (call.finishedAt ?: call.startedAt) - call.startedAt
    TextButton(onClick = { details = true }) { Text(stringResource(R.string.chat_tool_status, toolName, toolStatus)) }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text(call.toolId) }, confirmButton = { TextButton(onClick = { details = false }) { Text(stringResource(R.string.common_close)) } }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) { Text(stringResource(R.string.chat_tool_elapsed, toolStatus, elapsed)); Text(call.validatedArgumentsJson); Text(call.resultJson.orEmpty().take(16000)) }
    })
}
