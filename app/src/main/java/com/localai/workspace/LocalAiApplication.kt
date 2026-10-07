package com.localai.workspace

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

open class LocalAiApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        // Worker processes construct only their own scoped services, never the workspace graph.
        if (Application.getProcessName().substringAfterLast(':') in setOf("litert", "python")) return
        graph = AppGraph(this)
        graph.modelPreparation.start()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { graph.semanticDiagnostics.recover() } catch(error:Exception) { android.util.Log.e("LocalAI/Semantic","benchmark_recovery_failed type=${error.javaClass.simpleName}") }
            com.localai.workspace.inference.LiteRtCapabilityRefresh.refresh(graph.database.modelDao(),graph.liteRtLmRuntime)
            graph.workspace.cleanupDeletedDocuments()
            try { graph.contextFoundation.recoverCanonical() } catch(error:Exception) {
                graph.contextFoundation.notice.value = "MEMORY_RECOVERY_FAILED:${error.javaClass.simpleName}"
                android.util.Log.e("LocalAI/Context", "memory_recovery_failed type=${error.javaClass.simpleName}")
            }
        }
    }

    @Suppress("DEPRECATION") // Retain older Android memory-pressure callbacks; idle TTL also evicts.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::graph.isInitialized && level != TRIM_MEMORY_UI_HIDDEN &&
            level >= TRIM_MEMORY_RUNNING_LOW) graph.releaseIdleModel()
    }
}
