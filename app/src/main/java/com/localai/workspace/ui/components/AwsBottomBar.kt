package com.localai.workspace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localai.workspace.R

private data class WorkspaceTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

/** Shared four-destination navigation. Labels remain visible without splitting words at large scales. */
@Composable
fun AwsBottomBar(
    selectedRoute: String,
    onDestinationSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        WorkspaceTab("home", stringResource(R.string.navigation_home), Icons.Default.Home),
        WorkspaceTab("workspace", stringResource(R.string.navigation_chats), Icons.Default.ChatBubbleOutline),
        WorkspaceTab("memory", stringResource(R.string.navigation_memory), Icons.Default.Psychology),
        WorkspaceTab("settings", stringResource(R.string.navigation_more), Icons.Default.MoreHoriz),
    )
    Surface(
        modifier = modifier.navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val isSelected = selectedRoute == tab.route
                Column(
                    // Reserve a minimum slot for short labels and extra width for the longest label.
                    Modifier.weight(maxOf(tab.label.length, 6).toFloat())
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Tab) { onDestinationSelected(tab.route) }
                        .semantics {
                            selected = isSelected
                            role = Role.Tab
                        }
                        .testTag("top-level-${tab.route}")
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.widthIn(min = 56.dp).heightIn(min = 32.dp)
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(18.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp).testTag("top-level-icon-${tab.route}"),
                        )
                    }
                    Text(
                        text = tab.label,
                        modifier = Modifier.fillMaxWidth()
                            .semantics { contentDescription = tab.label }
                            .testTag("top-level-label-${tab.route}"),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
