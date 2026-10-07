package com.localai.workspace.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.localai.workspace.AppGraph
import com.localai.workspace.domain.inference.GenerationProgress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ChatActivity(val projectId: String, val conversationId: String?, val title: String,
    val progress: GenerationProgress, val generating: Boolean, val importing: Boolean)

/** Application owns chat controllers; navigation only attaches/detaches their UI. */
class ChatSessions(private val graph: AppGraph, private val scope: CoroutineScope) {
    private data class Entry(val projectId: String, val requestedId: String?, val store: ViewModelStore,
        val model: ChatViewModel, var monitor: Job? = null, var expiry: Job? = null)
    private val entries = linkedMapOf<String, Entry>() // Main dispatcher only.
    private val mutableActivities = MutableStateFlow<List<ChatActivity>>(emptyList())
    val activities = mutableActivities.asStateFlow()
    val hasGeneration: Boolean get() = entries.values.any { it.model.isGenerating.value }
    fun stop(projectId: String, conversationId: String?) {
        entries.values.firstOrNull { it.projectId == projectId && it.requestedId == conversationId }?.model?.stop()
    }

    fun get(projectId: String, conversationId: String?): ChatViewModel {
        entries.values.firstOrNull { it.projectId == projectId &&
            (it.requestedId == conversationId || (conversationId != null && it.model.currentConversationId.value == conversationId))
        }?.let { it.expiry?.cancel(); return it.model }
        val key = "$projectId:${conversationId ?: "default"}"
        val store = ViewModelStore()
        val model = ViewModelProvider(store, ChatViewModelFactory(projectId, graph, conversationId))[ChatViewModel::class.java]
        val entry = Entry(projectId, conversationId, store, model)
        entries[key] = entry
        entry.monitor = scope.launch {
            combine(model.isGenerating, model.isImporting, model.generationProgress, model.conversation) { generating, importing, progress, chat ->
                ChatActivity(projectId, conversationId, chat?.title ?: "Chat", progress, generating, importing)
            }.collect { activity ->
                mutableActivities.value = mutableActivities.value.filterNot { it.projectId == projectId && it.conversationId == conversationId } +
                    listOfNotNull(activity.takeIf { it.generating || it.importing })
                entry.expiry?.cancel()
                if (!activity.generating && !activity.importing && model.screenCount == 0) {
                    model.releaseIdleConversation()
                    scheduleExpiry(key, entry)
                }
            }
        }
        return model
    }

    fun attach(model: ChatViewModel) { model.screenCount++; entries.values.firstOrNull { it.model === model }?.expiry?.cancel() }
    fun detach(model: ChatViewModel) {
        model.screenCount = (model.screenCount - 1).coerceAtLeast(0)
        val entry = entries.entries.firstOrNull { it.value.model === model } ?: return
        if (!model.isGenerating.value && !model.isImporting.value) {
            model.releaseIdleConversation()
            scheduleExpiry(entry.key, entry.value)
        }
    }
    private fun scheduleExpiry(key: String, entry: Entry) {
        entry.expiry?.cancel()
        entry.expiry = scope.launch {
            delay(10 * 60_000L)
            if (entry.model.screenCount == 0 && !entry.model.isGenerating.value && !entry.model.isImporting.value) remove(key)
        }
    }
    private fun remove(key: String) {
        val entry = entries.remove(key) ?: return
        entry.monitor?.cancel(); entry.expiry?.cancel(); entry.store.clear()
        mutableActivities.value = mutableActivities.value.filterNot { it.projectId == entry.projectId && it.conversationId == entry.requestedId }
    }
    suspend fun removeWorkspace(projectId: String) {
        for ((key, entry) in entries.toMap()) if (entry.projectId == projectId) {
            entry.model.cancelAndAwait(); remove(key)
        }
    }
    suspend fun removeConversation(projectId: String, conversationId: String) {
        for ((key, entry) in entries.toMap()) if (entry.projectId == projectId &&
            (entry.requestedId == conversationId || entry.model.currentConversationId.value == conversationId)) {
            entry.model.cancelAndAwait(); remove(key)
        }
    }
    fun forget(model: ChatViewModel) { scope.launch {
        yield() // Allow the deletion coroutine to finish before clearing its scope.
        entries.entries.firstOrNull { it.value.model === model }?.key?.let(::remove)
    } }
    suspend fun clear() { entries.keys.toList().forEach { key -> entries[key]?.model?.cancelAndAwait(); remove(key) } }
}
