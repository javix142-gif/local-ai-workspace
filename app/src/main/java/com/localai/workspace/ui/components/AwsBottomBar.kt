package com.localai.workspace.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
    val tabs = listOf(
        WorkspaceTab("home", stringResource(R.string.navigation_home), Icons.Default.Home),
        WorkspaceTab("workspace", stringResource(R.string.navigation_chats), Icons.Default.ChatBubbleOutline),
        WorkspaceTab("memory", stringResource(R.string.navigation_memory), Icons.Default.Memory),
        WorkspaceTab("settings", stringResource(R.string.navigation_more), Icons.Default.MoreHoriz),
    )
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        tabs.forEach { tab ->
            NavigationBarItem(
                modifier = Modifier.testTag("top-level-${tab.route}"),
                selected = selectedRoute == tab.route,
                onClick = { onDestinationSelected(tab.route) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                alwaysShowLabel = true,
            )
        }
    }
}
