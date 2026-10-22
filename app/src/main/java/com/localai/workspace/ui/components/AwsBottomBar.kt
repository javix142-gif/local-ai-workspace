package com.localai.workspace.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.localai.workspace.R

private data class WorkspaceTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

/** Shared four-destination navigation used by the workspace shell and chat captures. */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun AwsBottomBar(
    selectedRoute: String,
    onDestinationSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val compactLabels = LocalDensity.current.fontScale >= 1.5f
    val tabs = listOf(
        WorkspaceTab("home", stringResource(R.string.navigation_home), Icons.Default.Home),
        WorkspaceTab("workspace", stringResource(R.string.navigation_chats), Icons.Default.ChatBubbleOutline),
        WorkspaceTab("memory", stringResource(R.string.navigation_memory), Icons.Default.Psychology),
        WorkspaceTab("settings", stringResource(R.string.navigation_more), Icons.Default.MoreHoriz),
    )
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        if (compactLabels) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { tab ->
                    val selected = selectedRoute == tab.route
                    Box(
                        Modifier.weight(if (selected) 1.8f else 1f)
                            .heightIn(min = 80.dp, max = 96.dp)
                            .clickable { onDestinationSelected(tab.route) }
                            .semantics {
                                contentDescription = tab.label
                                this.selected = selected
                                role = Role.Tab
                            }
                            .testTag("top-level-${tab.route}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(tab.icon, contentDescription = null,
                                modifier = Modifier.testTag("top-level-icon-${tab.route}"))
                            if (selected) Text(
                                tab.label,
                                modifier = Modifier.fillMaxWidth().testTag("top-level-label-${tab.route}"),
                                maxLines = 1,
                                softWrap = false,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        } else {
            tabs.forEach { tab ->
                val selected = selectedRoute == tab.route
                NavigationBarItem(
                    modifier = Modifier.testTag("top-level-${tab.route}"),
                    selected = selected,
                    onClick = { onDestinationSelected(tab.route) },
                    icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.testTag("top-level-icon-${tab.route}")) },
                    label = {
                        Text(
                            text = tab.label,
                            modifier = Modifier.testTag("top-level-label-${tab.route}")
                                .semantics { contentDescription = tab.label },
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    },
                    alwaysShowLabel = true,
                )
            }
        }
    }
}
