package com.localai.workspace.semantic.v2

import android.content.Context
import android.os.Process
import com.localai.workspace.performance.DeviceMeasurements
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

/** Application-owned job: changing screens does not cancel or create a second benchmark. */
class SemanticDiagnostics(private val context:Context,private val scope:CoroutineScope,private val layer:()->SemanticLayer,private val diagnosticBusy:MutableStateFlow<Boolean>,private val anotherOperation:()->Boolean) {
    val journal=SemanticBenchmarkJournal(File(context.filesDir,"semantic-v2-reports"))
    val progress=MutableStateFlow<SemanticBenchmarkProgress?>(null)
    val running=MutableStateFlow(false)
    private val recovery=Mutex();private var recovered=false
    private var active:Job?=null
    suspend fun recover() = recovery.withLock {
        if(recovered)return@withLock
        val old=journal.read();progress.value=if(old?.status=="RUNNING")journal.recover(ProcessExitClassification.find(context,old))else old
        recovered=true
    }
    fun start(iterations:Int=5,compare:Boolean=false,dimension:Int=768) {
        require(iterations in 1..20 && dimension in setOf(768,256))
        check(!anotherOperation() && running.compareAndSet(false,true)) { "Another operation is active" }
        if(!diagnosticBusy.compareAndSet(false,true)){running.value=false;error("Device validation is active")}
        active=scope.launch {
            try {
                recover()
                var current=SemanticBenchmarkProgress(UUID.randomUUID().toString(),System.currentTimeMillis(),Process.myPid(),iterations=iterations,currentDimension=dimension)
                fun commit(value:SemanticBenchmarkProgress) {
                    current=value.copy(lastNativeOperation=value.lastNativeOperation ?: current.lastNativeOperation)
                    journal.write(current);progress.value=current
                }
                progress.value=current
                commit(current)
                val service=layer();val manager=service.manager
                try {
                    service.exclusive {
                        service.restore()
                        val model=manager.selected ?: throw SemanticFailure(SemanticError.MODEL_FILE_INVALID)
                        val originalProfile=manager.profile;val originalDimension=manager.configuredDimension
                        manager.nativeObserver={ stamp -> commit(current.copy(nativeInFlight=!stamp.completed,lastNativeOperation=stamp,engineState=manager.status.value.state.name,measurements=DeviceMeasurements.capture(context),errorClass=stamp.errorClass)) }
                        try {
                            val port=object:SemanticBenchmarkPort {
                                override val modelHash=model.sha256
                                override fun state()=manager.status.value
                                override fun capture()=DeviceMeasurements.capture(context)
                                override suspend fun prepare(dimension:Int):Long { manager.select(model,EmbeddingRuntimeProfile());manager.configureDimension(dimension);return manager.initialize(dimension) }
                                override suspend fun embed(input:SemanticInput,task:EmbeddingTask,dimension:Int)=manager.embed(input,task,dimension)
                                override suspend fun unload()=manager.unload()
                            }
                            val result=SemanticBenchmarkRunner(port,::commit).run(current,if(compare)listOf(768,256)else listOf(dimension))
                            commit(result)
                        } finally {
                            withContext(NonCancellable) {
                                try { commit(current.copy(currentPhase="RESTORE_CONFIGURATION"));manager.select(model,originalProfile);manager.configureDimension(originalDimension) }
                                finally { manager.nativeObserver=null }
                            }
                        }
                    }
                    commit(current.copy(status="SUCCESS",currentPhase="COMPLETE",completedAt=System.currentTimeMillis(),nativeInFlight=false))
                } catch(cancel:CancellationException) {
                    withContext(NonCancellable){commit(current.copy(status="CANCELLED",failureKind="USER_CANCELLED",completedAt=System.currentTimeMillis()))}
                } catch(error:Throwable) {
                    val kind=if(error is OutOfMemoryError || (error as? SemanticFailure)?.code==SemanticError.OUT_OF_MEMORY)"OUT_OF_MEMORY_ERROR"else"JAVA_EXCEPTION"
                    commit(current.copy(status="FAILED",failureKind=kind,errorClass=error.javaClass.simpleName,completedAt=System.currentTimeMillis()))
                } finally { journal.archive(current) }
            } catch(cancel:CancellationException) {
                progress.value=progress.value?.copy(status="CANCELLED",failureKind="USER_CANCELLED",completedAt=System.currentTimeMillis())
            } catch(error:Throwable) {
                // Even journal/restore/archive failures must not escape this application-owned job.
                val failed=progress.value?.copy(status="FAILED",failureKind=if(error is OutOfMemoryError)"OUT_OF_MEMORY_ERROR"else"JAVA_EXCEPTION",errorClass=error.javaClass.simpleName,completedAt=System.currentTimeMillis())
                progress.value=failed
                if(failed!=null)runCatching{journal.write(failed);journal.archive(failed)}
                android.util.Log.e("LocalAI/Semantic","benchmark_failed type=${error.javaClass.simpleName}")
            } finally { diagnosticBusy.value=false;running.value=false }
        }
    }
    fun cancel(){active?.cancel()}
    fun report():String?=progress.value?.let(journal::json)
}
