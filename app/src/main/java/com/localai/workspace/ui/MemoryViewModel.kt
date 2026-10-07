package com.localai.workspace.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localai.workspace.AppGraph
import com.localai.workspace.data.MemoryItemEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MemoryViewModel(
    private val projectId: String,
    private val graph: AppGraph,
) : ViewModel() {
    private val dao = graph.database.memoryDao()
    val items: StateFlow<List<MemoryItemEntity>> = dao.observeRelevant(projectId).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun save(id: String?, content: String, kind: String = "CONTEXT", global: Boolean = false) {
        val value = content.trim()
        if (value.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (id == null) {
                dao.upsert(
                    MemoryItemEntity(
                        id = UUID.randomUUID().toString(),
                        scopeType = if(global) "GLOBAL" else "PROJECT",
                        scopeId = if(global) null else projectId,
                        kind = kind,
                        content = value,
                        sourceType = "USER_APPROVED",
                        relevance = 1f,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            } else {
                dao.get(id)?.let { dao.upsert(it.copy(content=value,kind=kind,scopeType=if(global) "GLOBAL" else "PROJECT",scopeId=if(global) null else projectId,updatedAt=now)) };graph.database.semanticVectorDao().deleteMemory(id)
            }
        }
    }

    fun archive(id: String) {
        viewModelScope.launch { dao.archive(id, System.currentTimeMillis());graph.database.semanticVectorDao().deleteMemory(id) }
    }
}

class MemoryViewModelFactory(private val projectId: String, private val graph: AppGraph) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MemoryViewModel(projectId, graph) as T
}
