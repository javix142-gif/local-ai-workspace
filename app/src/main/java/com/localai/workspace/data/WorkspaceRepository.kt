package com.localai.workspace.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

class WorkspaceRepository(private val database: WorkspaceDatabase, private val privateDocumentsDirectory: File? = null) {
    private val projects = database.projectDao()
    private val conversations = database.conversationDao()
    private val messages = database.messageDao()
    private val models = database.modelDao()
    val activeProjects: Flow<List<ProjectEntity>> = projects.observeActive()
    val allModels: Flow<List<ModelEntity>> = models.observeAll()
    val pendingDocumentCleanup = database.pendingDocumentDeletionDao().observeCount()
    private val cleanupMutex = Mutex()

    suspend fun createChat(): String = database.withTransaction {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        projects.upsert(ProjectEntity(id, "New chat", now, now, workspaceKind = WorkspaceKind.CHAT))
        createConversation(id)
        id
    }

    suspend fun ensureStarterProject() {
        if (projects.get(DEFAULT_PROJECT_ID) == null) {
            val now = System.currentTimeMillis()
            projects.upsert(
                ProjectEntity(
                    id = DEFAULT_PROJECT_ID,
                    name = "Personal workspace",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    suspend fun createProject(name: String): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        projects.upsert(ProjectEntity(id = id, name = name.trim().ifBlank { "Untitled project" }, createdAt = now, updatedAt = now))
        return id
    }

    suspend fun renameProject(id: String, name: String) {
        projects.rename(id, name.trim().ifBlank { "Untitled project" }, System.currentTimeMillis())
    }

    suspend fun archiveProject(id: String) = projects.archive(id, System.currentTimeMillis())

    fun observeProject(id: String): Flow<ProjectEntity?> = projects.observe(id)
    fun observeConversations(projectId: String) = conversations.observeForProject(projectId)
    fun observeConversation(id: String) = conversations.observe(id)

    suspend fun conversationForChat(projectId: String, conversationId: String?): ConversationEntity = database.withTransaction {
        check(projects.get(projectId) != null) { "This workspace was deleted" }
        if (conversationId == null) ensureConversation(projectId)
        else checkNotNull(conversations.get(conversationId)?.takeIf { it.projectId == projectId }) { "This chat was deleted" }
    }

    suspend fun createConversation(projectId: String): ConversationEntity = database.withTransaction {
        val owner = checkNotNull(projects.get(projectId)) { "This workspace was deleted" }
        if (owner.workspaceKind == WorkspaceKind.CHAT) {
            conversations.getForProject(projectId)?.let { return@withTransaction it }
        }
        val now = System.currentTimeMillis()
        ConversationEntity(UUID.randomUUID().toString(), projectId, "New chat", now, now).also { conversations.upsert(it) }
    }

    suspend fun ensureConversation(projectId: String): ConversationEntity = database.withTransaction {
        check(projects.get(projectId) != null) { "This workspace was deleted" }
        // Preserve legacy IDs; a new chat gets a fresh ID so late callbacks cannot
        // attach evidence or messages to a replacement of a deleted conversation.
        val current = conversations.getForProject(projectId)
        if (current != null) return@withTransaction current
        val now = System.currentTimeMillis()
        ConversationEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            title = "New conversation",
            createdAt = now,
            updatedAt = now,
        ).also { conversations.upsert(it) }
    }

    /** Keep project files, memories, model preferences and benchmark history. */
    suspend fun deleteProjectChats(projectId: String) = database.withTransaction {
        database.citationEvidenceDao().deleteForProject(projectId)
        database.toolCallDao().deleteForProject(projectId)
        database.pendingDocumentDeletionDao().insertAll(messages.imagesForProject(projectId).map { PendingDocumentDeletionEntity(it) })
        messages.deleteForProject(projectId)
        conversations.deleteForProject(projectId)
    }

    /** Remove one project conversation, or the entire isolated owner of a standalone chat. */
    suspend fun deleteConversation(id: String): Int {
        database.withTransaction {
            val conversation = conversations.get(id) ?: return@withTransaction
            val owner = conversation.projectId?.let { projects.get(it) }
            if (owner?.workspaceKind == WorkspaceKind.CHAT) deleteWorkspaceRows(owner.id)
            else {
                database.citationEvidenceDao().deleteForConversation(id)
                database.toolCallDao().deleteForConversation(id)
                database.pendingDocumentDeletionDao().insertAll(messages.imagesForConversation(id).map { PendingDocumentDeletionEntity(it) })
                messages.deleteForConversation(id)
                conversations.delete(id)
            }
        }
        return cleanupDeletedDocuments()
    }

    suspend fun deleteWorkspace(id: String): Int {
        database.withTransaction { deleteWorkspaceRows(id) }
        return cleanupDeletedDocuments()
    }

    private suspend fun deleteWorkspaceRows(id: String) {
        val documents = database.documentDao()
        database.pendingDocumentDeletionDao().insertAll(documents.forProject(id).map { PendingDocumentDeletionEntity(it.localPath) })
        deleteProjectChats(id)
        database.semanticVectorDao().deleteForProject(id)
        documents.deleteEmbeddingsForProject(id)
        // Room's external-content FTS triggers remove the deleted segments from the index.
        documents.deleteSegmentsForProject(id)
        documents.deleteForProject(id)
        database.memoryDao().deleteForProject(id)
        projects.delete(id)
    }

    /** Durable outbox: DB deletion is atomic; failed private-file cleanup can be retried after restart. */
    suspend fun cleanupDeletedDocuments(): Int = withContext(Dispatchers.IO) { cleanupMutex.withLock {
        val queue = database.pendingDocumentDeletionDao()
        val root = privateDocumentsDirectory?.canonicalFile
        for (item in queue.pending()) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            try {
                if (database.documentDao().pathReferences(item.localPath) > 0 || messages.imageReferences(item.localPath) > 0) {
                    queue.acknowledge(item.localPath); continue
                }
                if (root == null) continue
                val file = File(item.localPath).canonicalFile
                // Never touch originals, models, directories or targets outside private documents.
                val attachments = File(root.parentFile, "attachments").canonicalFile
                val audio = File(root.parentFile, "audio-attachments").canonicalFile
                if ((!file.path.startsWith(root.path + File.separator) && !file.path.startsWith(attachments.path + File.separator) && !file.path.startsWith(audio.path + File.separator)) || file.isDirectory) continue
                if (!file.exists() || file.delete()) queue.acknowledge(item.localPath)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: java.io.IOException) { /* Keep the durable item; UI exposes retry/count. */ }
            catch (_: SecurityException) { /* Keep the durable item; UI exposes retry/count. */ }
        }
        queue.pending().size
    } }

    suspend fun saveCitationEvidence(items: List<CitationEvidenceEntity>) = database.withTransaction {
        val retained = items.filter { conversations.get(it.conversationId) != null }
        if (retained.isNotEmpty()) database.citationEvidenceDao().insertAll(retained)
    }

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> = messages.observeForConversation(conversationId)

    suspend fun recoverInterruptedGeneration(conversationId: String) = database.withTransaction {
        messages.recoverInterruptedGeneration(conversationId)
        database.toolCallDao().recoverInterrupted(conversationId, System.currentTimeMillis())
    }

    suspend fun addMessage(message: MessageEntity) = database.withTransaction {
        val conversation = checkNotNull(conversations.get(message.conversationId)) { "This chat was deleted. Start a new chat." }
        if (message.role == "USER" && conversation.title in setOf("New chat", "New conversation")) {
            val title = message.content.trim().replace(Regex("\\s+"), " ").take(60).ifBlank { "New chat" }
            conversations.rename(conversation.id, title)
            conversation.projectId?.let { id ->
                if (projects.get(id)?.workspaceKind == WorkspaceKind.CHAT) projects.rename(id, title, message.createdAt)
            }
        }
        messages.insert(message)
        conversations.touch(message.conversationId, message.createdAt)
    }

    suspend fun updateGeneratedMessage(id: String, content: String, status: String, metrics: String?) =
        messages.updateGenerated(id, content, status, metrics)

    suspend fun recentMessages(conversationId: String, limit: Int): List<MessageEntity> = messages.recent(conversationId, limit)

    suspend fun getModel(id: String): ModelEntity? = models.get(id)

    fun observeModel(id: String): Flow<ModelEntity?> = models.observe(id)

    suspend fun setDefaultModel(projectId: String, modelId: String?) =
        projects.setDefaultModel(projectId, modelId, System.currentTimeMillis())

    suspend fun updateModelSettings(
        id: String,
        accelerator: String,
        contextSize: Int?,
        maxOutputTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        seed: Int,
    ) = models.updateSettings(
        id = id,
        accelerator = accelerator,
        contextSize = contextSize,
        maxOutputTokens = maxOutputTokens,
        temperature = temperature,
        topP = topP,
        topK = topK,
        repeatPenalty = repeatPenalty,
        seed = seed,
        updatedAt = System.currentTimeMillis(),
    )

    suspend fun updateModelMetrics(id: String, metrics: String) = models.updateMetrics(
        id = id,
        metrics = metrics,
        testedAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )

    companion object {
        const val DEFAULT_PROJECT_ID = "default-project"
    }
}
