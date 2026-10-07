package com.localai.workspace

import androidx.compose.material3.InputChip

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.localai.workspace.ui.ChatComposer
import com.localai.workspace.ui.ChatRichText
import com.localai.workspace.ui.humanModelName
import com.localai.workspace.ui.runtimeDisplayName
import com.localai.workspace.ui.compactGenerationLabel
import com.localai.workspace.ui.capabilityAvailability
import com.localai.workspace.ui.CapabilityAvailability
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.localai.workspace.data.MessageEntity
import com.localai.workspace.data.GenerationDiagnosticCodec
import com.localai.workspace.data.GenerationMetricsPresentation
import com.localai.workspace.data.ModelEntity
import com.localai.workspace.data.HuggingFaceFile
import com.localai.workspace.data.ModelDownloadState
import com.localai.workspace.domain.model.AcceleratorType
import com.localai.workspace.domain.model.ModelCapability
import com.localai.workspace.domain.model.ModelFormat
import com.localai.workspace.domain.model.ModelImportStatus
import com.localai.workspace.domain.model.toDescriptor
import com.localai.workspace.domain.model.RuntimeType
import com.localai.workspace.domain.inference.GenerationStage
import com.localai.workspace.domain.inference.GenerationError
import com.localai.workspace.domain.inference.RuntimePreparation
import com.localai.workspace.ui.ChatViewModel
import com.localai.workspace.ui.ChatViewModelFactory
import com.localai.workspace.ui.HomeViewModel
import com.localai.workspace.ui.HomeViewModelFactory
import com.localai.workspace.ui.LocalAiTheme
import com.localai.workspace.ui.MemoryViewModel
import com.localai.workspace.ui.MemoryViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.DisposableEffect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LocalAiTheme { LocalAiWorkspaceRoot() } }
    }
}

private data class TopLevelDestination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun LocalAiWorkspaceRoot() {
    val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as LocalAiApplication
    val graph = application.graph
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(graph))
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = listOf(
        TopLevelDestination("workspace", "Workspace", Icons.Default.Home),
        TopLevelDestination("models", "Models", Icons.Default.ModelTraining),
        TopLevelDestination("settings", "Settings", Icons.Default.Settings),
    )
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        homeViewModel.notices.collectLatest { snackbarHostState.showSnackbar(it) }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentRoute in topLevel.map { it.route }) {
                BottomAppBar {
                    topLevel.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo("workspace") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "workspace",
            modifier = Modifier.padding(padding),
        ) {
            composable("workspace") {
                WorkspaceScreen(homeViewModel, navController)
            }
            composable("models") {
                ModelsScreen(homeViewModel, navController)
            }
            composable("model/{modelId}") { entry ->
                ModelDetailsScreen(homeViewModel, navController, entry.arguments?.getString("modelId").orEmpty())
            }
            composable("settings") {
                SettingsScreen(openValidation={navController.navigate("device_validation")}) { navController.navigate("performance") }
            }
            composable("performance") {
                com.localai.workspace.ui.PerformanceScreen(graph) { navController.popBackStack() }
            }
            composable("device_validation") {
                com.localai.workspace.ui.DeviceValidationScreen(graph) { navController.popBackStack() }
            }
            composable("project/{projectId}") { entry ->
                ProjectScreen(homeViewModel, navController, entry.arguments?.getString("projectId").orEmpty())
            }
            composable("chat/{projectId}?conversationId={conversationId}",
                arguments = listOf(navArgument("conversationId") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
                val projectId = entry.arguments?.getString("projectId").orEmpty()
                val conversationId = entry.arguments?.getString("conversationId")
                val chatViewModel = remember(projectId, conversationId) { graph.chatSessions.get(projectId, conversationId) }
                ChatScreen(chatViewModel, navController)
            }
            composable("memory/{projectId}") { entry ->
                val projectId = entry.arguments?.getString("projectId").orEmpty()
                val memoryViewModel: MemoryViewModel = viewModel(
                    key = "memory-$projectId",
                    factory = MemoryViewModelFactory(projectId, graph),
                )
                MemoryScreen(memoryViewModel, navController)
            }
        }
    }
}

private fun chatRoute(projectId: String, conversationId: String? = null): String =
    "chat/$projectId" + (conversationId?.let { "?conversationId=$it" } ?: "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceScreen(viewModel: HomeViewModel, navController: NavHostController) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val deleting by viewModel.deletingChats.collectAsStateWithLifecycle()
    val creating by viewModel.creating.collectAsStateWithLifecycle()
    val pendingCleanup by viewModel.pendingDocumentCleanup.collectAsStateWithLifecycle()
    val preparation by viewModel.modelPreparation.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val activities by viewModel.chatActivities.collectAsStateWithLifecycle()
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val visibleChats = chats.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val visibleProjects = projects.filter { it.name.contains(query.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TopAppBar(title = { Text("Local AI Workspace", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            actions = { IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                Icon(androidx.compose.material.icons.Icons.Default.Search, contentDescription = "Search workspace")
            } })
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                LocalModeCard()
                Button(onClick = { viewModel.startChat { id, conversation -> navController.navigate(chatRoute(id, conversation)) } },
                    enabled = !creating, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ChatBubbleOutline, null); Spacer(Modifier.width(8.dp)); Text("Start chat")
                }
                OutlinedButton(onClick = { showCreate = true }, enabled = !creating, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Start project")
                }
                WorkspaceModelCard(models, preparation, viewModel::prepareModel,
                    viewModel::retryModelPreparation, viewModel::cancelModelPreparation)
                com.localai.workspace.ui.SemanticSearchEntry()
            }
            if (searching) item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Search titles") })
            }
            if (pendingCleanup > 0) item {
                Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("File cleanup pending", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = viewModel::retryDocumentCleanup) { Text("Retry") }
                } }
            }
            items(activities, key = { "activity-${it.projectId}-${it.conversationId}" }) { activity ->
                Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { navController.navigate(chatRoute(activity.projectId, activity.conversationId)) }.padding(vertical = 12.dp)) {
                        Text(activity.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (activity.generating) compactGenerationLabel(activity.progress.stage, activity.progress.cancellationRequested) else "Reading files…", style = MaterialTheme.typography.labelSmall)
                    }
                    if (activity.generating) IconButton(onClick = { viewModel.stopChatActivity(activity.projectId, activity.conversationId) }) { Icon(Icons.Default.Stop, "Stop generation") }
                } }
            }
            item { Text("Chats", style = MaterialTheme.typography.titleLarge) }
            if (visibleChats.isEmpty()) item { Text(if (query.isBlank()) "No chats yet" else "No matching chats", style = MaterialTheme.typography.bodyMedium) }
            items(visibleChats, key = { "chat-${it.id}" }) { chat ->
                WorkspaceEntryCard(chat.name, "", false, chat.id in deleting,
                    onOpen = { navController.navigate(chatRoute(chat.id)) }, onDelete = { deleteId = chat.id })
            }
            item { Text("Projects", style = MaterialTheme.typography.titleLarge) }
            if (visibleProjects.isEmpty()) item { Text(if (query.isBlank()) "No projects yet" else "No matching projects", style = MaterialTheme.typography.bodyMedium) }
            items(visibleProjects, key = { "project-${it.id}" }) { project ->
                WorkspaceEntryCard(project.name, "", true, project.id in deleting,
                    onOpen = { navController.navigate("project/${project.id}") }, onDelete = { deleteId = project.id })
            }
        }
    }
    (chats + projects).firstOrNull { it.id == deleteId }?.let { item ->
        DeleteWorkspaceDialog(item.name, item.workspaceKind == com.localai.workspace.data.WorkspaceKind.CHAT,
            onDismiss = { deleteId = null }, onConfirm = { deleteId = null; viewModel.deleteWorkspace(item.id) })
    }
    if (showCreate) CreateProjectDialog(onDismiss = { showCreate = false }, onCreate = { name ->
        showCreate = false; viewModel.createProject(name) { navController.navigate("project/$it") }
    })
}

@Composable
private fun WorkspaceModelCard(models: List<ModelEntity>, state: RuntimePreparation.State,
    select: (String) -> Unit, retry: () -> Unit, cancel: () -> Unit) {
    val usable = models.filter(com.localai.workspace.data.ApplicationModelPreparation::isUsable)
    if (usable.isEmpty()) return
    val selected = usable.firstOrNull { it.id == state.modelId }
    var details by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { details = true }) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(selected?.displayName?.let(::humanModelName) ?: "Choose model", style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(when { state.preparing -> compactGenerationLabel(state.progress.stage)
                    state.error != null -> "Could not load · tap to retry"
                    state.ready -> "Ready · Local"
                    else -> "Not loaded" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { details = true }) { Icon(Icons.Default.ChevronRight, "Active model") }
        }
        if (state.preparing) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text("Active model") },
        confirmButton = { TextButton(onClick = { details = false }) { Text("Done") } },
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            usable.forEach { model -> TextButton(onClick = { select(model.id); details = false }, modifier = Modifier.fillMaxWidth()) {
                Text(humanModelName(model.displayName), maxLines = 2, overflow = TextOverflow.Ellipsis)
            } }
            Text("One model stays in memory. Switching may reload it; Android can release it to free RAM.", style = MaterialTheme.typography.bodySmall)
            if (state.preparing) {
                Text(state.progress.label); state.progress.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = cancel) { Text("Cancel loading") }
            }
            state.error?.let { Text(it.displayText(), style = MaterialTheme.typography.bodySmall); TextButton(onClick = retry) { Text("Retry loading") } }
            if (!state.ready && !state.preparing && state.error == null) TextButton(onClick = retry) { Text("Load model") }
        } })
}

@Composable
private fun WorkspaceEntryCard(title: String, subtitle: String, isProject: Boolean, busy: Boolean,
    onOpen: () -> Unit, onDelete: () -> Unit) {
    var menu by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isProject) Icons.Default.FolderOpen else Icons.Default.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (busy || subtitle.isNotBlank()) Text(if (busy) "Deleting…" else subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { menu = true }, enabled = !busy) { Icon(Icons.Default.MoreVert, contentDescription = "Options for $title") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (isProject) "Delete project" else "Delete chat") },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                        onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectScreen(viewModel: HomeViewModel, navController: NavHostController, projectId: String) {
    val project by remember(projectId) { viewModel.observeProject(projectId) }.collectAsStateWithLifecycle(initialValue = null)
    val chats by remember(projectId) { viewModel.observeConversations(projectId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val documents by remember(projectId) { viewModel.observeDocuments(projectId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val models by viewModel.models.collectAsStateWithLifecycle()
    val deleting by viewModel.deletingChats.collectAsStateWithLifecycle()
    val creating by viewModel.creating.collectAsStateWithLifecycle()
    val busy = projectId in deleting
    var tab by rememberSaveable { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var deleteChatId by rememberSaveable { mutableStateOf<String?>(null) }
    var fileDetailsId by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.attachProjectDocument(projectId, it) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TopAppBar(title = { Text(project?.name ?: "Project", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { Box {
                IconButton(onClick = { menu = true }, enabled = !busy && project != null) { Icon(Icons.Default.MoreVert, "Project options") }
                DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("Delete project") }, onClick = { menu = false; showDelete = true }) }
            } })
        TabRow(tab) { listOf("Chats", "Files", "Memory", "Settings").forEachIndexed { index, label ->
            Tab(tab == index, onClick = { tab = index }, enabled = !busy, text = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) })
        } }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when (tab) {
            0 -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Button(onClick = { viewModel.startChat(projectId) { id, conversation -> navController.navigate(chatRoute(id, conversation)) } },
                    enabled = !busy && !creating && project != null, modifier = Modifier.fillMaxWidth()) { Text("Start chat") } }
                if (chats.isEmpty()) item { Text("No chats yet", Modifier.padding(12.dp)) }
                items(chats, key = { it.id }) { chat -> WorkspaceEntryCard(chat.title, "", false, busy,
                    onOpen = { navController.navigate(chatRoute(projectId, chat.id)) }, onDelete = { deleteChatId = chat.id }) }
            }
            1 -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy && project != null, modifier = Modifier.fillMaxWidth()) { Text("Add file") } }
                item { com.localai.workspace.ui.SemanticSearchEntry(projectId) }
                if (documents.isEmpty()) item { Text("No files yet", Modifier.padding(12.dp)) }
                items(documents, key = { it.id }) { document -> Card(Modifier.fillMaxWidth().clickable { fileDetailsId = document.id }) { Column(Modifier.padding(12.dp)) {
                    Text(document.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(when { document.indexingStatus == "READY" -> "Ready · ${formatBytes(document.byteSize)}"
                        document.errorMessage != null || document.extractionStatus == "FAILED" || document.indexingStatus == "FAILED" -> "Could not read · tap for details"
                        else -> "Reading…" }, style = MaterialTheme.typography.bodySmall)
                    document.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                } } }
            }
            2 -> {
                val graph = (androidx.compose.ui.platform.LocalContext.current.applicationContext as LocalAiApplication).graph
                val memoryViewModel: MemoryViewModel = viewModel(key = "memory-$projectId", factory = MemoryViewModelFactory(projectId, graph))
                Box(Modifier.weight(1f)) {
                    if (busy) Text("Deleting…", Modifier.padding(16.dp))
                    else Column { com.localai.workspace.ui.ContextMemoryEntry(projectId); Box(Modifier.weight(1f)) { MemoryScreen(memoryViewModel, navController, embedded = true) } }
                }
            }
            3 -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Current configuration", style = MaterialTheme.typography.titleMedium)
                com.localai.workspace.ui.ProjectAssistantSettings(projectId)
                val defaultModel = models.firstOrNull { it.id == project?.defaultModelId }
                Text("Model: ${defaultModel?.displayName?.let(::humanModelName) ?: "Active workspace model"}")
                defaultModel?.let { TextButton(onClick = { navController.navigate("model/${it.id}") }) { Text("Model settings") } }
                Text("Memory: ${if (project?.memoryEnabled == true) "Enabled · approved items only" else "Disabled"}")
                Text("Instructions", style = MaterialTheme.typography.labelLarge)
                Text(project?.systemInstructions?.takeIf { it.isNotBlank() } ?: "No project instructions", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    documents.firstOrNull { it.id == fileDetailsId }?.let { file -> AlertDialog(onDismissRequest = { fileDetailsId = null },
        title = { Text(file.displayName) }, confirmButton = { TextButton(onClick = { fileDetailsId = null }) { Text("Done") } },
        text = { Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            Text("${formatBytes(file.byteSize)} · ${file.mimeType}")
            Text("Extraction: ${file.extractionStatus}"); Text("Indexing: ${file.indexingStatus}")
            file.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }) }
    if (showDelete && project != null) DeleteWorkspaceDialog(project!!.name, false,
        onDismiss = { showDelete = false }, onConfirm = { showDelete = false; viewModel.deleteWorkspace(projectId) { navController.popBackStack() } })
    chats.firstOrNull { it.id == deleteChatId }?.let { chat -> DeleteChatDialog(chat.title, { deleteChatId = null }, {
        deleteChatId = null; viewModel.deleteConversation(projectId, chat.id)
    }) }
}

@Composable
private fun DeleteWorkspaceDialog(name: String, standalone: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
        title = { Text(if (standalone) "Delete chat?" else "Delete project?") },
        text = { Text(if (standalone) "Delete “$name”, its messages, attached private files and chat memory? This cannot be undone. Installed models and global memory are kept."
            else "Delete “$name”, all its chats, private files, source indexes and project memory? This cannot be undone. Installed models, global memory and other projects are kept.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelsScreen(viewModel: HomeViewModel, navController: NavHostController) {
    val contentResolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    val models by viewModel.models.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    var showAddModel by rememberSaveable { mutableStateOf(false) }
    var showHuggingFace by rememberSaveable { mutableStateOf(false) }
    var pendingMultimodalPrimary by remember { mutableStateOf<Uri?>(null) }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var sort by rememberSaveable { mutableStateOf("RECENT") }
    val ggufPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importModel(it, ModelFormat.GGUF) }
    }
    val liteRtPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importModel(it, ModelFormat.LITERT_LM) }
    }
    val projectorPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val primary = pendingMultimodalPrimary
        pendingMultimodalPrimary = null
        if (primary != null && uri != null) viewModel.importMultimodal(primary, uri)
    }
    val multimodalPrimaryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val pickedName = uri?.let { selectedUri ->
            contentResolver.query(selectedUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) cursor.getString(0) else selectedUri.lastPathSegment
            }
        }
        if (uri != null && pickedName?.lowercase()?.endsWith(".litertlm") == true) {
            viewModel.importModel(uri, ModelFormat.LITERT_LM)
        } else {
            pendingMultimodalPrimary = uri
            if (uri != null) projectorPicker.launch(arrayOf("*/*"))
        }
    }
    val visibleModels = models.filter { model ->
        val descriptor = model.toDescriptor()
        when (filter) {
            "GGUF" -> descriptor.format == ModelFormat.GGUF
            "LITERT" -> descriptor.format == ModelFormat.LITERT_LM
            "VISION" -> ModelCapability.VISION in descriptor.capabilities
            "DOWNLOADED" -> descriptor.source.name == "HUGGING_FACE" || descriptor.source.name == "APP_DOWNLOAD"
            else -> true
        }
    }.let { list ->
        when (sort) {
            "NAME" -> list.sortedBy { it.displayName.lowercase() }
            "SIZE" -> list.sortedByDescending { it.fileSize }
            "RUNTIME" -> list.sortedBy { it.runtimeId }
            else -> list.sortedByDescending { it.importedAt }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        TopAppBar(
            title = {
                Column {
                    Text("Model Library", fontWeight = FontWeight.SemiBold)
                    Text("${models.size} model${if (models.size == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall)
                }
            },
        )
        Button(onClick = { showAddModel = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add model")
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("ALL", "GGUF", "LITERT", "VISION", "DOWNLOADED")) { value ->
                FilterChip(
                    selected = filter == value,
                    onClick = { filter = value },
                    label = { Text(value.replace('_', ' ')) },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Models storage: ${formatBytes(models.sumOf { it.fileSize })}", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { sort = when (sort) { "RECENT" -> "NAME"; "NAME" -> "SIZE"; "SIZE" -> "RUNTIME"; else -> "RECENT" } }) {
                Text("Sort: ${sort.lowercase().replaceFirstChar { it.uppercase() }}")
            }
        }
        importProgress?.let { progress ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${progress.status.name.replace('_', ' ')} · ${progress.displayName}", style = MaterialTheme.typography.labelMedium)
                    if (progress.totalBytes != null && progress.totalBytes > 0) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("${formatBytes(progress.bytesCompleted)} / ${formatBytes(progress.totalBytes)}", style = MaterialTheme.typography.labelSmall)
                    }
                    progress.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        downloadProgress?.takeUnless { it.state == ModelDownloadState.COMPLETED }?.let { progress ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${progress.state.name.replace('_', ' ')} · ${progress.file.path.substringAfterLast('/')}", style = MaterialTheme.typography.labelMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("${formatBytes(progress.downloadedBytes)} / ${progress.totalBytes?.let(::formatBytes) ?: "?"} · ${formatBytes(progress.bytesPerSecond)}/s", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        com.localai.workspace.ui.SemanticV2Settings()
        if (visibleModels.isEmpty()) {
            EmptyState("No models in this view", "Add a model to get started.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(visibleModels, key = { it.id }) { model ->
                    ModelCard(model) { navController.navigate("model/${model.id}") }
                }
            }
        }
    }
    if (showAddModel) {
        AddModelDialog(
            onDismiss = { showAddModel = false },
            onGguf = { showAddModel = false; ggufPicker.launch(arrayOf("*/*")) },
            onLiteRt = { showAddModel = false; liteRtPicker.launch(arrayOf("*/*")) },
            onHuggingFace = { showAddModel = false; showHuggingFace = true },
            onMultimodal = { showAddModel = false; multimodalPrimaryPicker.launch(arrayOf("*/*")) },
        )
    }
    if (showHuggingFace) HuggingFaceDialog(viewModel, onDismiss = { showHuggingFace = false })
}

@Composable
internal fun ModelCard(model: ModelEntity, onClick: () -> Unit) {
    val descriptor = model.toDescriptor()
    val name = humanModelName(model.displayName)
    val available = listOf("Text" to ModelCapability.TEXT, "Images" to ModelCapability.VISION, "Audio" to ModelCapability.AUDIO)
        .filter { capabilityAvailability(descriptor, it.second) == CapabilityAvailability.AVAILABLE }.map { it.first }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${runtimeDisplayName(descriptor.runtime)} · ${formatBytes(descriptor.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
            if (available.isNotEmpty()) Text("Available: ${available.joinToString(" · ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (ModelCapability.VISION in descriptor.capabilities && "Images" !in available)
                Text("Vision: model support only", style = MaterialTheme.typography.labelSmall)
            if (name != model.displayName) Text(model.displayName, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (descriptor.importStatus !in setOf(ModelImportStatus.READY, ModelImportStatus.COMPATIBLE_WARNING))
                Text(descriptor.importStatus.name.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            descriptor.compatibilityWarning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        }
    }
}

@Composable
private fun AddModelDialog(onDismiss: () -> Unit, onGguf: () -> Unit, onLiteRt: () -> Unit,
    onHuggingFace: () -> Unit, onMultimodal: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add model") }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("FROM DEVICE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ModelOption("GGUF", "llama.cpp", ".gguf", onGguf)
            ModelOption("LiteRT-LM", "Android runtime", ".litertlm", onLiteRt)
            ModelOption("Multimodal", "Self-contained LiteRT-LM or GGUF + projector", "", onMultimodal)
            Text("ONLINE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ModelOption("Hugging Face", "Find and download models", "", onHuggingFace)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ModelOption(title: String, description: String, formats: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall)
            if (formats.isNotBlank()) Text(formats, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HuggingFaceDialog(viewModel: HomeViewModel, onDismiss: () -> Unit) {
    val repository by viewModel.huggingFaceRepository.collectAsStateWithLifecycle()
    val searchResults by viewModel.huggingFaceSearch.collectAsStateWithLifecycle()
    val progress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    var reference by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download from Hugging Face") },
        text = {
            Column(Modifier.heightIn(max = 580.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Downloading from Hugging Face requires Internet access. Prompts and conversations never leave the device.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(reference, { reference = it }, Modifier.weight(1f), label = { Text("owner/repository or URL") }, singleLine = true)
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = { viewModel.loadHuggingFaceFiles(reference, showAll) }, enabled = reference.isNotBlank()) { Text("Load") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(search, { search = it }, Modifier.weight(1f), label = { Text("Optional search") }, singleLine = true)
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = { viewModel.searchHuggingFace(search) }, enabled = search.isNotBlank()) { Text("Search") }
                }
                if (searchResults.isNotEmpty()) {
                    Text("Repositories", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(searchResults) { result ->
                            AssistChip(onClick = { reference = result.repositoryId; viewModel.loadHuggingFaceFiles(result.repositoryId, showAll) }, label = { Text(result.repositoryId) })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showAll, onCheckedChange = { showAll = it; if (reference.isNotBlank()) viewModel.loadHuggingFaceFiles(reference, it) })
                    Text("Show all files", style = MaterialTheme.typography.labelMedium)
                }
                repository?.let { repo ->
                    Text("${repo.repositoryId} · ${repo.files.size} relevant file(s)", style = MaterialTheme.typography.labelMedium)
                    LazyColumn(Modifier.heightIn(max = 250.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(repo.files, key = { it.path }) { file ->
                            HuggingFaceFileRow(file, onDownload = { viewModel.downloadHuggingFace(file) })
                        }
                    }
                }
                progress?.let { current ->
                    if (current.state != ModelDownloadState.COMPLETED) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("${current.state.name} · ${formatBytes(current.downloadedBytes)} / ${current.totalBytes?.let(::formatBytes) ?: "?"}", style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = viewModel::cancelDownload, enabled = current.state == ModelDownloadState.DOWNLOADING) { Text("Cancel") }
                    }
                }
                HorizontalDivider()
                Text("Optional gated-model token", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("HF token (stored in Android Keystore)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.setHuggingFaceToken(token); token = "" }, enabled = token.isNotBlank()) { Text("Save token") }
                    TextButton(onClick = viewModel::clearHuggingFaceToken) { Text("Clear token") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun HuggingFaceFileRow(file: HuggingFaceFile, onDownload: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(file.path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                Text("${file.format.name} · ${file.quantization ?: "quantization unknown"} · ${formatBytes(file.sizeBytes)}", style = MaterialTheme.typography.labelSmall)
                Text(file.runtimeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Button(onClick = onDownload) { Text("Download") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDetailsScreen(viewModel: HomeViewModel, navController: NavHostController, modelId: String) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val model = models.firstOrNull { it.id == modelId }
    var showRemove by rememberSaveable { mutableStateOf(false) }
    if (model == null) {
        EmptyState("Model not found", "It may have been removed from private storage.")
        return
    }
    val descriptor = model.toDescriptor()
    var contextText by remember(model.id) { mutableStateOf((model.configuredContext ?: model.declaredContext ?: 4096).toString()) }
    var outputText by remember(model.id) { mutableStateOf(model.maxOutputTokens.toString()) }
    var temperatureText by remember(model.id) { mutableStateOf(model.temperature.toString()) }
    var topPText by remember(model.id) { mutableStateOf(model.topP.toString()) }
    var topKText by remember(model.id) { mutableStateOf(model.topK.toString()) }
    var repeatPenaltyText by remember(model.id) { mutableStateOf(model.repeatPenalty.toString()) }
    var seedText by remember(model.id) { mutableStateOf(model.seed.toString()) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        TopAppBar(
            title = { Text("Model details", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(humanModelName(model.displayName), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("${descriptor.format.name} · ${descriptor.runtime.name.replace('_', ' ')} · ${formatBytes(descriptor.sizeBytes)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { DetailSection("GENERAL", listOfNotNull("Filename" to (model.originalFilename ?: java.io.File(model.localPath).name), "Family" to descriptor.family, "Architecture" to descriptor.architecture, "Parameters" to model.parameterLabel, "Quantization" to descriptor.quantization, "Size" to formatBytes(descriptor.sizeBytes), "Format" to descriptor.format.name, "Runtime" to model.runtimeId)) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("CAPABILITIES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        listOf("Text" to ModelCapability.TEXT, "Vision" to ModelCapability.VISION, "Audio" to ModelCapability.AUDIO, "Tool calling" to ModelCapability.TOOL_CALLING, "Thinking" to ModelCapability.THINKING, "Embeddings" to ModelCapability.EMBEDDING, "Image generation" to ModelCapability.IMAGE_GENERATION, "Speculative decoding" to ModelCapability.SPECULATIVE_DECODING).forEach { (name, capability) ->
                            val availability = capabilityAvailability(descriptor, capability)
                            Text("$name · ${when (availability) {
                                CapabilityAvailability.AVAILABLE -> "Available in app"
                                CapabilityAvailability.MODEL_ONLY -> "Supported by model · unavailable in app"
                                CapabilityAvailability.EXPERIMENTAL -> "Experimental · Diagnostics"
                                CapabilityAvailability.UNAVAILABLE -> "Unavailable"
                            }}", style = MaterialTheme.typography.bodySmall,
                                color = if (availability == CapabilityAvailability.AVAILABLE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Runtime and engine activation are shown in Settings → Developer / Diagnostics. Model declarations do not prove device support.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { DetailSection("CONTEXT", listOfNotNull("Declared context" to model.declaredContext?.toString(), "Recommended context" to model.recommendedContext?.toString(), "Configured context" to model.configuredContext?.toString(), "Max output tokens" to model.maxOutputTokens.toString())) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("RUNTIME SETTINGS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(if (descriptor.runtime == RuntimeType.LITERT_LM) "LiteRT defaults to CPU; GPU experiments use Diagnostics" else "Compatible accelerators", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AcceleratorType.entries.forEach { accelerator ->
                                val available = accelerator in descriptor.accelerators &&
                                    (descriptor.runtime != RuntimeType.LITERT_LM || accelerator == AcceleratorType.CPU)
                                FilterChip(selected = model.preferredAccelerator == accelerator.name, onClick = {
                                    if (available) viewModel.updateModelSettings(model.id, accelerator.name, contextText.toIntOrNull(), outputText.toIntOrNull() ?: 512, temperatureText.toFloatOrNull() ?: 0.3f, topPText.toFloatOrNull() ?: 0.95f, topKText.toIntOrNull() ?: 40, repeatPenaltyText.toFloatOrNull() ?: 1.0f, seedText.toIntOrNull() ?: 0)
                                }, enabled = available, label = { Text("${if (available) "✓" else "✕"} ${accelerator.name}") })
                            }
                        }
                        OutlinedTextField(contextText, { contextText = it }, label = { Text("Context size") }, singleLine = true)
                        OutlinedTextField(outputText, { outputText = it }, label = { Text("Max output tokens") }, singleLine = true)
                        OutlinedTextField(temperatureText, { temperatureText = it }, label = { Text("Temperature") }, singleLine = true)
                        OutlinedTextField(topPText, { topPText = it }, label = { Text("Top P") }, singleLine = true)
                        OutlinedTextField(topKText, { topKText = it }, label = { Text("Top K") }, singleLine = true)
                        OutlinedTextField(repeatPenaltyText, { repeatPenaltyText = it }, label = { Text("Repeat penalty") }, singleLine = true)
                        OutlinedTextField(seedText, { seedText = it }, label = { Text("Seed (0 = runtime default)") }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { contextText = "2048"; outputText = "256"; temperatureText = "0.2"; topPText = "0.8"; topKText = "20"; repeatPenaltyText = "1.1"; seedText = "0" }) { Text("Conservative") }
                            TextButton(onClick = { contextText = (model.recommendedContext ?: 4096).toString(); outputText = if (descriptor.runtime == RuntimeType.LITERT_LM) "256" else "512"; temperatureText = "0.3"; topPText = "0.95"; topKText = "40"; repeatPenaltyText = if (descriptor.runtime == RuntimeType.LITERT_LM) "1.1" else "1.0"; seedText = "0" }) { Text("Balanced") }
                            TextButton(onClick = { contextText = (model.declaredContext ?: 8192).coerceAtMost(8192).toString(); outputText = "1024"; temperatureText = "0.7"; topPText = "0.95"; topKText = "40"; repeatPenaltyText = "1.0"; seedText = "0" }) { Text("Performance") }
                        }
                        Button(onClick = { viewModel.updateModelSettings(model.id, model.preferredAccelerator, contextText.toIntOrNull(), outputText.toIntOrNull() ?: 512, temperatureText.toFloatOrNull() ?: 0.3f, topPText.toFloatOrNull() ?: 0.95f, topKText.toIntOrNull() ?: 40, repeatPenaltyText.toFloatOrNull() ?: 1.0f, seedText.toIntOrNull() ?: 0) }, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }
                        if (descriptor.runtime == RuntimeType.LITERT_LM) Text("For ordinary LiteRT chat, repeat penalty > 1.0 also enables the native 8-token repetition guard over a 256-token window. Set 1.0 to disable these extra decoding controls. Lower max output reduces the longest possible reply; it does not accelerate model loading.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { DetailSection("FILES", listOfNotNull("Primary model" to model.originalFilename, "Auxiliary files" to descriptor.auxiliaryFiles.joinToString { it.displayName }.ifBlank { "None" }, "SHA-256" to model.fileHash)) }
            item { DetailSection("SOURCE", listOfNotNull("Source" to descriptor.source.name.replace('_', ' '), "Repository" to descriptor.sourceRepository, "Imported" to model.importedAt.toString())) }
            item { DetailSection("BENCHMARKS", listOfNotNull("Last tested" to model.lastTestedAt?.toString(), "Metrics" to model.lastMetrics).ifEmpty { listOf("Status" to "Not measured on this device") }) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.setDefaultModel("default-project", model.id); navController.navigate("workspace") { popUpTo("models") } }, modifier = Modifier.weight(1f)) { Text("Use model") }
                    OutlinedButton(onClick = { showRemove = true }, modifier = Modifier.weight(1f)) { Text("Remove") }
                }
            }
        }
    }
    if (showRemove) {
        AlertDialog(onDismissRequest = { showRemove = false }, title = { Text("Remove model?") }, text = { Text("This deletes the primary and auxiliary files from app-private storage. Downloaded source files are not kept as library entries.") }, confirmButton = { Button(onClick = { showRemove = false; viewModel.deleteModel(model); navController.popBackStack() }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { showRemove = false }) { Text("Cancel") } })
    }
}

@Composable
private fun DetailSection(title: String, values: List<Pair<String, String?>>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            values.filter { !it.second.isNullOrBlank() }.forEach { (label, value) -> Text("$label: $value", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(viewModel: ChatViewModel, navController: NavHostController) {
    val project by viewModel.project.collectAsStateWithLifecycle()
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val standalone = project?.workspaceKind == com.localai.workspace.data.WorkspaceKind.CHAT
    val models by viewModel.models.collectAsStateWithLifecycle()
    val selectedModelId by viewModel.selectedModelId.collectAsStateWithLifecycle()
    val attachedImage by viewModel.attachedImagePath.collectAsStateWithLifecycle()
    val attachedAudio by viewModel.attachedAudioPath.collectAsStateWithLifecycle()
    val assistantProfile by viewModel.assistantProfile.collectAsStateWithLifecycle()
    val toolCalls by viewModel.toolCalls.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val selectedFiles by viewModel.selectedDocumentIds.collectAsStateWithLifecycle()
    val importing by viewModel.isImporting.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) { viewModel.enterScreen(); onDispose { viewModel.leaveScreen() } }
    var showFiles by rememberSaveable { mutableStateOf(false) }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    var previewText by remember { mutableStateOf("") }
    LaunchedEffect(previewId) { previewText = ""; previewId?.let { previewText = viewModel.previewDocument(it) } }
    val generating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val deletingChat by viewModel.isDeletingChat.collectAsStateWithLifecycle()
    var showChatMenu by rememberSaveable { mutableStateOf(false) }
    var showDeleteChat by rememberSaveable { mutableStateOf(false) }
    val streaming by viewModel.streamingText.collectAsStateWithLifecycle()
    LaunchedEffect(generating, streaming.isNotEmpty()) {
        if (generating && streaming.isNotEmpty()) viewModel.firstContentObservedByUi()
    }
    val generationProgress by viewModel.generationProgress.collectAsStateWithLifecycle()
    val generationError by viewModel.generationError.collectAsStateWithLifecycle()
    val preparation by viewModel.modelPreparation.collectAsStateWithLifecycle()
    val evidence by viewModel.evidence.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var input by rememberSaveable { mutableStateOf("") }
    var selectedEvidence by remember { mutableStateOf<String?>(null) }
    var showTechnicalError by rememberSaveable { mutableStateOf(false) }
    var historicalDiagnostic by remember { mutableStateOf<GenerationError?>(null) }
    var selectedMetrics by rememberSaveable { mutableStateOf<String?>(null) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var showModelPicker by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::attachDocument) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::attachAudio) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::attachImage) }
    LaunchedEffect(Unit) { viewModel.notices.collectLatest { snackbarHostState.showSnackbar(it) } }
    val citation = selectedEvidence?.let { id -> evidence.firstOrNull { it.evidenceId == id } }
    val selectedModel = models.firstOrNull { it.id == selectedModelId } ?: models.firstOrNull()
    val visionEnabled = selectedModel?.let { capabilityAvailability(it.toDescriptor(), ModelCapability.VISION) == CapabilityAvailability.AVAILABLE } == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.heightIn(min = 48.dp).clickable(enabled = !generating && !deletingChat) { showModelPicker = true }) {
                        Text(if (standalone) project?.name ?: "Chat" else "${conversation?.title ?: "Chat"} · ${project?.name ?: "Project"}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(selectedModel?.let { "${humanModelName(it.displayName)} · Local · ${when {
                            generating -> compactGenerationLabel(generationProgress.stage, generationProgress.cancellationRequested)
                            preparation.modelId == it.id && preparation.preparing -> compactGenerationLabel(preparation.progress.stage)
                            preparation.modelId == it.id && preparation.ready -> "Ready"
                            else -> "Not loaded"
                        }}" } ?: "Choose a model", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }, enabled = !deletingChat) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    Box {
                        IconButton(onClick = { showChatMenu = true }, enabled = !deletingChat) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Chat options")
                        }
                        DropdownMenu(expanded = showChatMenu, onDismissRequest = { showChatMenu = false }) {
                            DropdownMenuItem(text = { Text("Model") }, enabled = !generating,
                                onClick = { showChatMenu = false; showModelPicker = true })
                            DropdownMenuItem(text = { Text("Files") }, onClick = { showChatMenu = false; showFiles = true })
                            DropdownMenuItem(text = { Text("Memory") }, onClick = { showChatMenu = false; navController.navigate("memory/${project?.id.orEmpty()}") })
                            DropdownMenuItem(text = { Text("Diagnostics") }, onClick = { showChatMenu = false; showDiagnostics = true })
                            DropdownMenuItem(
                                text = { Text("Delete chat") },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                                onClick = { showChatMenu = false; showDeleteChat = true },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                if (attachedImage != null || attachedAudio != null || selectedFiles.isNotEmpty() || importing) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (attachedImage != null) {
                            com.localai.workspace.ui.PendingImageAttachment(attachedImage!!, !generating && !deletingChat, viewModel::clearImage)
                        }
                        if (attachedAudio != null) InputChip(selected = true, onClick = {}, label = { Text("Audio attached") }, trailingIcon = { IconButton(onClick = viewModel::clearAudio, enabled = !generating) { Icon(Icons.Default.Close, "Remove audio") } })
                        if (selectedFiles.isNotEmpty() || importing) TextButton(onClick = { showFiles = true }) {
                            Text(if (importing) "Reading files…" else "${selectedFiles.size} file${if (selectedFiles.size == 1) "" else "s"} selected")
                        }
                    }
                }
                ChatComposer(input = input, generating = generating || deletingChat,
                    onInputChange = { input = it }, onAttach = { picker.launch(arrayOf("*/*")) },
                    onAttachImage = { imagePicker.launch(arrayOf("image/*")) }, imageEnabled = visionEnabled,
                    onAttachAudio = { audioPicker.launch(arrayOf("audio/*")) }, audioEnabled = selectedModel?.let { capabilityAvailability(it.toDescriptor(), ModelCapability.AUDIO) == CapabilityAvailability.AVAILABLE } == true,
                    onFiles = if (documents.isNotEmpty()) ({ showFiles = true }) else null,
                    onStop = viewModel::stop, onSend = { if (viewModel.send(input)) input = "" },
                    contextualControls = { com.localai.workspace.ui.AssistantControls(assistantProfile,
                        selectedModel?.toDescriptor()?.let { it.runtime == RuntimeType.LITERT_LM && ModelCapability.TOOL_CALLING in it.capabilities } == true,
                        selectedModel?.toDescriptor()?.let { it.runtime == RuntimeType.LITERT_LM && ModelCapability.THINKING in it.capabilities } == true, !generating, viewModel::setAssistantProfile) })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (deletingChat) {
                Text("Deleting chat…", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (generating || (preparation.preparing && preparation.modelId == selectedModel?.id)) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            }
            if (!generating && preparation.preparing && preparation.modelId == selectedModel?.id) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(compactGenerationLabel(preparation.progress.stage), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = viewModel::cancelPreparation) { Text("Cancel") }
                }
            }
            if (!generating && (generationError != null || preparation.error != null)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Could not finish", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { historicalDiagnostic = generationError ?: preparation.error; showTechnicalError = true }) { Text("Details") }
                }
            }
            if (messages.isEmpty()) {
                EmptyState("How can I help?", "")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        val content = if (message.status == "GENERATING" && message.id == messages.lastOrNull()?.id) streaming else message.content
                        Column {
                        toolCalls.filter { it.messageId == message.id }.forEach { com.localai.workspace.ui.ToolExecutionCard(it) }
                        MessageBubble(message, content, evidence,
                            activeLabel = if (generating && message.id == messages.lastOrNull()?.id) compactGenerationLabel(generationProgress.stage, generationProgress.cancellationRequested) else null,
                            canContinue = !generating && !deletingChat && message.id == messages.lastOrNull()?.id && message.status == "COMPLETE",
                            onContinue = { viewModel.continueResponse(message.id) },
                            onRetry = if (!generating && !deletingChat && message.id == messages.lastOrNull()?.id && message.status in setOf("FAILED", "CANCELED")) {
                                messages.takeWhile { it.id != message.id }.lastOrNull { it.role == "USER" }?.let { previous -> ({ viewModel.send(previous.content, retryImagePath = previous.imagePath, retryAudioPath = previous.audioPath) }) }
                            } else null,
                            onMetrics = { selectedMetrics = it },
                            onDiagnostic = { historicalDiagnostic = it; showTechnicalError = true },
                            onCitation = { selectedEvidence = it })
                        }
                    }
                }
            }
        }
    }
    if (showModelPicker) AlertDialog(onDismissRequest = { showModelPicker = false }, title = { Text("Model") },
        confirmButton = { TextButton(onClick = { showModelPicker = false }) { Text("Done") } }, text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                if (models.isEmpty()) TextButton(onClick = { showModelPicker = false; navController.navigate("models") }) { Text("Add a model") }
                models.forEach { model -> Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewModel.selectModel(model.id); showModelPicker = false }, enabled = !generating && !deletingChat, modifier = Modifier.weight(1f)) {
                        Text(humanModelName(model.displayName), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { showModelPicker = false; navController.navigate("model/${model.id}") }) { Icon(Icons.Default.Settings, "Settings for ${humanModelName(model.displayName)}") }
                } }
            }
        })
    if (showDiagnostics) AlertDialog(onDismissRequest = { showDiagnostics = false }, title = { Text("Diagnostics") },
        confirmButton = { TextButton(onClick = { showDiagnostics = false }) { Text("Close") } }, text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val progress = if (generating || generationError != null) generationProgress else preparation.progress
                Text(progress.label); progress.detail?.let { Text(it) }
                Text("Thinking: ${assistantProfile.thinking}");Text("Tools: ${toolCalls.size} calls · ${toolCalls.count { it.status != "SUCCESS" }} non-success · ${toolCalls.sumOf { (it.finishedAt ?: it.startedAt) - it.startedAt }} ms")
                val rag by (androidx.compose.ui.platform.LocalContext.current.applicationContext as LocalAiApplication).graph.retrieval.diagnostics.collectAsStateWithLifecycle()
                Text("RAG: ${rag.mode} · ${rag.retrieved} chunks · ${rag.retrievalMs} ms · embedding ${rag.embeddingMs} ms")
                Text("Memory: ${rag.memoryRetrieved}");rag.notice?.let { Text(it) }
                Text("Last audio preprocessing: ${viewModel.audioPreprocessingMs.collectAsStateWithLifecycle().value ?: "unavailable"} ms", style = MaterialTheme.typography.bodySmall)
                Text(viewModel.audioDiagnostics.collectAsStateWithLifecycle().value, style = MaterialTheme.typography.bodySmall)
                Text("Thinking actual: ${viewModel.actualThinking.collectAsStateWithLifecycle().value}", style = MaterialTheme.typography.bodySmall)
                Text(viewModel.visionDiagnostics.collectAsStateWithLifecycle().value, style = MaterialTheme.typography.bodySmall)
                if (progress.bytesCompleted != null && progress.bytesTotal != null)
                    Text("Integrity: ${progress.bytesCompleted} / ${progress.bytesTotal} bytes · ${progress.elapsedMs} ms")
                preparation.metrics?.let { measured -> TextButton(onClick = { selectedMetrics = GenerationMetricsPresentation.encode(measured); showDiagnostics = false }) { Text("Model preparation details") } }
                TextButton(onClick = { showDiagnostics = false; navController.navigate("performance") }) { Text("Local AI Performance") }
                if (selectedModel?.toDescriptor()?.runtime == RuntimeType.LITERT_LM) {
                    TextButton(onClick = { showDiagnostics = false; viewModel.runLiteRtSmokeTest() }, enabled = !generating && !deletingChat && !preparation.preparing) { Text("Run CPU smoke test") }
                    Text("Text · 1024 context · 32 output", style = MaterialTheme.typography.labelSmall)
                }
            }
        })
    if (showFiles) AlertDialog(onDismissRequest = { showFiles = false },
        title = { Text("Local files") }, confirmButton = { TextButton(onClick = { showFiles = false }) { Text("Done") } },
        text = { Column {
            Text("Selected files send up to 3 readable excerpts with your next message. Large files are not sent in full.", style = MaterialTheme.typography.bodySmall)
            if (documents.isEmpty()) Text("No files yet")
            LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(documents, key = { it.id }) { doc -> Column {
                    val ready = doc.extractionStatus == "READY" && doc.indexingStatus == "READY"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = doc.id in selectedFiles, enabled = ready && !generating && !deletingChat,
                            onCheckedChange = { viewModel.selectDocument(doc.id, it) })
                        Text(doc.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    }
                    Text(if (ready) "Ready · readable text indexed" else "${doc.extractionStatus} · ${doc.indexingStatus}", style = MaterialTheme.typography.labelSmall)
                    doc.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row {
                        TextButton(onClick = { previewId = doc.id }) { Text("Preview text") }
                        TextButton(enabled = ready && !generating && !deletingChat, onClick = {
                            showFiles = false; viewModel.analyzeDocument(doc.id)
                        }) { Text("Summarize") }
                    }
                    HorizontalDivider()
                } }
            }
        } })
    if (previewId != null) AlertDialog(onDismissRequest = { previewId = null }, title = { Text("Extracted text · opening excerpts") },
        confirmButton = { TextButton(onClick = { previewId = null }) { Text("Close") } },
        text = { Box(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) { Text(previewText.ifBlank { "Reading…" }) } })
    if (showDeleteChat) {
        val confirm = { showDeleteChat = false; viewModel.deleteChat { navController.popBackStack() } }
        if (standalone) DeleteWorkspaceDialog(project?.name ?: "Chat", true, { showDeleteChat = false }, confirm)
        else DeleteChatDialog(conversation?.title ?: "Chat", { showDeleteChat = false }, confirm)
    }
    if (showTechnicalError) {
        AlertDialog(
            onDismissRequest = { showTechnicalError = false },
            confirmButton = { TextButton(onClick = { showTechnicalError = false }) { Text("Close") } },
            title = { Text("LiteRT generation diagnostic") },
            text = { Box(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text((historicalDiagnostic ?: generationError)?.let { it.displayText() + "\n\n" + it.technicalDetail.orEmpty() }.orEmpty())
            } },
        )
    }
    GenerationMetricsPresentation.decode(selectedMetrics)?.let { metrics ->
        ResponseDetailsDialog(metrics, onDismiss = { selectedMetrics = null })
    }
    citation?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedEvidence = null },
            confirmButton = { TextButton(onClick = { selectedEvidence = null }) { Text("Close") } },
            title = { Text("${item.evidenceId} · ${item.sourceLabel}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.pageStart?.let { "Page $it" } ?: "Local source")
                    Text(item.excerpt)
                    Text("Metadata comes from the local index, not from model-generated citation text.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

@Composable
private fun DeleteChatDialog(projectName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
        title = { Text("Delete chat?") },
        text = { Text("Delete “$projectName” and its messages, citations and tool history? This cannot be undone. Other chats, project files, memory and models are kept. An active response will be stopped.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryScreen(viewModel: MemoryViewModel, navController: NavHostController, embedded: Boolean = false) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = if (embedded) 0.dp else 16.dp)) {
        if (!embedded) TopAppBar(title = { Text("Memory") },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { info = true }) { Icon(Icons.Default.Info, "About memory") }
                IconButton(onClick = { editorId = null; showEditor = true }) { Icon(Icons.Default.Add, "Add memory") }
            })
        else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Approved memory", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = { info = true }) { Icon(Icons.Default.Info, "About memory") }
            IconButton(onClick = { editorId = null; showEditor = true }) { Icon(Icons.Default.Add, "Add memory") }
        }
        if (items.isEmpty()) EmptyState("No saved memory", "Add only what you want remembered.")
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(items, key = { it.id }) { item -> Card(Modifier.fillMaxWidth().testTag("memory-${item.id}")) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(item.content, style = MaterialTheme.typography.bodyMedium)
                        if (item.scopeType == "GLOBAL") Text("Global", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { editorId = item.id; showEditor = true }) { Icon(Icons.Default.Edit, "Edit memory") }
                    IconButton(onClick = { removeId = item.id }) { Icon(Icons.Default.DeleteOutline, "Remove memory") }
                }
            } }
        }
    }
    if (info) AlertDialog(onDismissRequest = { info = false }, title = { Text("Approved memory") },
        text = { Text("Only items you explicitly save are used as memory. Removing an item archives it and stops using it in new requests.") },
        confirmButton = { TextButton(onClick = { info = false }) { Text("Done") } })
    if (removeId != null) AlertDialog(onDismissRequest = { removeId = null }, title = { Text("Remove memory?") },
        text = { Text("This item will no longer be used in new requests.") },
        confirmButton = { TextButton(onClick = { removeId?.let(viewModel::archive); removeId = null }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { removeId = null }) { Text("Cancel") } })
    if (showEditor) MemoryEditorDialog(initial = items.firstOrNull { it.id == editorId }?.content.orEmpty(),
        initialKind = items.firstOrNull { it.id == editorId }?.kind ?: "CONTEXT", initialGlobal = items.firstOrNull { it.id == editorId }?.scopeType == "GLOBAL",
        onDismiss = { showEditor = false }, onSave = { content, kind, global -> viewModel.save(editorId, content, kind, global); showEditor = false })
}

@Composable
private fun MemoryEditorDialog(initial: String, initialKind: String = "CONTEXT", initialGlobal: Boolean = false, onDismiss: () -> Unit, onSave: (String,String,Boolean) -> Unit) {
    var content by rememberSaveable(initial) { mutableStateOf(initial) }
    var kind by rememberSaveable { mutableStateOf(initialKind) };var global by rememberSaveable { mutableStateOf(initialGlobal) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.isBlank()) "Add memory" else "Edit memory") },
        text = { Column { OutlinedTextField(value = content, onValueChange = { content = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Memory") }, minLines = 3, maxLines = 6)
            Row { listOf("PREFERENCE", "FACT", "CONTEXT").forEach { type -> FilterChip(kind==type, {kind=type}, label={Text(type.lowercase())}) } }
            Row { Text("Global",Modifier.weight(1f));androidx.compose.material3.Switch(global,{global=it}) }
        } },
        confirmButton = { Button(onClick = { onSave(content, kind, global) }, enabled = content.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MessageBubble(message: MessageEntity, content: String,
    evidence: List<com.localai.workspace.data.CitationEvidenceEntity>, activeLabel: String? = null,
    canContinue: Boolean = false, onContinue: () -> Unit, onRetry: (() -> Unit)? = null,
    onMetrics: (String) -> Unit, onDiagnostic: (GenerationError) -> Unit, onCitation: (String) -> Unit) {
    val user = message.role == "USER"
    val diagnostic = remember(message.generationMetrics) { GenerationDiagnosticCodec.decode(message.generationMetrics) }
    val metrics = remember(message.generationMetrics) { GenerationMetricsPresentation.decode(message.generationMetrics) }
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    var copied by remember(content) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Card(Modifier.fillMaxWidth(if (user) 0.86f else 1f), colors = CardDefaults.cardColors(
            containerColor = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val displayed = if (diagnostic != null && content == diagnostic.displayText()) diagnostic.message else content
                val body = displayed.ifBlank { activeLabel ?: when (message.status) {
                    "GENERATING" -> "Response interrupted"
                    "CANCELED" -> "Stopped"
                    "FAILED" -> "Could not finish"
                    else -> "No text returned"
                } }
                if (user && message.audioPath != null) Text("Audio attachment", style = MaterialTheme.typography.labelSmall)
                if (user) message.imagePath?.let { com.localai.workspace.ui.ImageAttachment(it) }
                if (user || content.isBlank()) Text(body, style = MaterialTheme.typography.bodyLarge) else ChatRichText(body)
                if (!user && evidence.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    evidence.forEach { item -> AssistChip(onClick = { onCitation(item.evidenceId) }, label = { Text(item.evidenceId) }) }
                }
                if (!user && metrics?.outputLimitReached == true) Text("Answer may be incomplete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!user && message.status != "GENERATING") Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (message.status == "FAILED") Text("Could not finish", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.weight(1f))
                    if (content.isNotBlank()) IconButton(onClick = { clipboard.setText(AnnotatedString(content)); copied = true }) {
                        Icon(if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy, "Copy response", Modifier.size(18.dp))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Response options", Modifier.size(18.dp)) }
                        DropdownMenu(menu, { menu = false }) {
                            if (metrics?.outputLimitReached == true && canContinue) DropdownMenuItem(text = { Text("Continue") }, onClick = { menu = false; onContinue() })
                            onRetry?.let { action -> DropdownMenuItem(text = { Text("Retry") }, onClick = { menu = false; action() }) }
                            if (diagnostic != null) DropdownMenuItem(text = { Text("Details") }, onClick = { menu = false; onDiagnostic(diagnostic) })
                            else if (metrics != null) DropdownMenuItem(text = { Text("Details") }, onClick = { menu = false; onMetrics(metrics.raw) })
                            if (diagnostic == null && metrics == null && onRetry == null && !(metrics?.outputLimitReached == true && canContinue))
                                DropdownMenuItem(text = { Text("Copy") }, onClick = { menu = false; clipboard.setText(AnnotatedString(content)) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResponseDetailsDialog(metrics: GenerationMetricsPresentation, onDismiss: () -> Unit) {
    var showRaw by rememberSaveable(metrics.raw) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Details") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                metrics.rows().forEach { (label, value) ->
                    Column {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                TextButton(onClick = { showRaw = !showRaw }) { Text(if (showRaw) "Hide technical metrics" else "Technical metrics") }
                if (showRaw) Text(metrics.raw, style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(openValidation: () -> Unit = {}, openPerformance: () -> Unit) {
    var developer by rememberSaveable { mutableStateOf(false) }
    var boundaries by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        TopAppBar(title = { Text("Settings") })
        LocalModeCard()
        com.localai.workspace.ui.SemanticSettings()
        com.localai.workspace.ui.ContextMemoryEntry()
        Card(Modifier.fillMaxWidth().clickable { developer = !developer }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Developer / Diagnostics", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(Icons.Default.ChevronRight, null)
            }
        }
        if (developer) com.localai.workspace.ui.ContextDiagnosticsEntry()
        if (developer) TextButton(onClick = openPerformance, modifier = Modifier.fillMaxWidth()) { Text("Local AI Performance") }
        if (developer) TextButton(onClick = openValidation, modifier = Modifier.fillMaxWidth()) { Text("Device Validation") }
        TextButton(onClick = { boundaries = true }) { Text("About capabilities") }
    }
    if (boundaries) AlertDialog(onDismissRequest = { boundaries = false }, title = { Text("Capability boundaries") },
        text = { Text("Model support does not mean a function is available in this app. Imported models are inspected; runtime and device determine compatibility. Evidence references resolve against the local index. Native performance requires Android device testing.") },
        confirmButton = { TextButton(onClick = { boundaries = false }) { Text("Done") } })
}

@Composable
private fun LocalModeCard() {
    var details by remember { mutableStateOf(false) }
    TextButton(onClick = { details = true }) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
        Text("Private · Local", style = MaterialTheme.typography.labelMedium)
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text("Private · Local") },
        text = { Text("Inference and imported models, documents and saved memory stay on this device. Internet is used only for an explicit Hugging Face download. External connectors and web search are not enabled.") },
        confirmButton = { TextButton(onClick = { details = false }) { Text("Done") } })
}

@Composable
private fun EmptyState(title: String, description: String) {
    Box(Modifier.fillMaxSize().padding(30.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CreateProjectDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start project") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Project name") }, singleLine = true) },
        confirmButton = { Button(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GiB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MiB".format(bytes / (1024.0 * 1024))
    else -> "%.0f KiB".format(bytes / 1024.0)
}
