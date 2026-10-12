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
import androidx.compose.ui.unit.dp
import com.localai.workspace.data.*
import com.localai.workspace.domain.tools.ChatToolSchemas
import com.localai.workspace.ui.components.minimumAppTouchTarget
import com.localai.workspace.ui.theme.LocalAiSpacing

@Composable
fun AssistantControls(profile: AssistantProfile, toolsAvailable: Boolean, thinkingAvailable: Boolean,
    enabled: Boolean, onChange: (AssistantProfile) -> Unit) {
    var tools by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    val thinkingLabel = profile.thinking.name.lowercase().replaceFirstChar { it.uppercase() }
    val toolsLabel = when {
        profile.tools.isEmpty() -> "Off"
        profile.tools == ChatToolSchemas.defaults -> "Auto"
        else -> "Custom"
    }
    val summary = buildList {
        if (thinkingAvailable) add("Think: $thinkingLabel")
        if (toolsAvailable) add("Tools: $toolsLabel")
    }.joinToString(" · ")

    if (thinkingAvailable || toolsAvailable) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = LocalAiSpacing.x3, vertical = LocalAiSpacing.x1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                TextButton(
                    onClick = { options = true },
                    enabled = enabled,
                    modifier = Modifier.minimumAppTouchTarget().testTag("assistant-options-button"),
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null)
                    Spacer(Modifier.width(LocalAiSpacing.x1))
                    Text("Options")
                }
                DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                    if (thinkingAvailable) {
                        DropdownMenuItem(
                            modifier = Modifier.testTag("assistant-options-thinking"),
                            text = { Text("Thinking · $thinkingLabel") },
                            onClick = { options = false; thinking = true },
                        )
                    }
                    if (toolsAvailable) {
                        DropdownMenuItem(
                            modifier = Modifier.testTag("assistant-options-tools"),
                            text = { Text("Tools · $toolsLabel") },
                            onClick = { options = false; tools = true },
                        )
                    }
                }
            }
            if (summary.isNotEmpty()) {
                Text(
                    summary,
                    Modifier.weight(1f).padding(start = LocalAiSpacing.x2).testTag("assistant-options-summary"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (thinking) AlertDialog(onDismissRequest = { thinking = false }, title = { Text("Thinking") }, confirmButton = { TextButton(onClick = { thinking = false }) { Text("Done") } }, text = {
        Column { ThinkingMode.entries.forEach { mode -> TextButton(onClick = { onChange(profile.copy(thinking = mode)); thinking = false }) { Text(mode.name) } } }
    })
    if (tools) AlertDialog(onDismissRequest = { tools = false }, title = { Text("Tools") }, confirmButton = { TextButton(onClick = { tools = false }) { Text("Done") } }, text = {
        Column { TextButton(onClick = { onChange(profile.copy(tools = emptySet())) }) { Text("Off") }
            TextButton(onClick = { onChange(profile.copy(tools = ChatToolSchemas.defaults)) }) { Text("Auto") }
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
    TextButton(onClick = { details = true }) { Text("${when(call.toolId) { "calculator.evaluate" -> "Calculator"; "python.execute" -> "Python"; else -> "Files" }} · ${if(call.status == "SUCCESS") "✓ Completed" else call.status}") }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text(call.toolId) }, confirmButton = { TextButton(onClick = { details = false }) { Text("Close") } }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) { Text("${call.status} · ${(call.finishedAt ?: call.startedAt) - call.startedAt} ms"); Text(call.validatedArgumentsJson); Text(call.resultJson.orEmpty().take(16000)) }
    })
}
