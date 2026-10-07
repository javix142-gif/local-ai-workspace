package com.localai.workspace.validation

import android.app.Application
import androidx.room.Room
import com.localai.workspace.AppGraph
import com.localai.workspace.data.*
import com.localai.workspace.domain.inference.*
import com.localai.workspace.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ValidationPrerequisitesTest {
    private class FakeRuntime:InferenceRuntime {
        override val runtimeType=RuntimeType.LITERT_LM
        override val runtimeId="litert-lm-android"
        var loads=0
        override fun supports(format:ModelFormat)=true
        override fun availableAccelerators()=setOf(AcceleratorType.CPU)
        override suspend fun inspectModel(source:ModelSource):ModelMetadata=error("not a real runtime")
        override suspend fun load(config:ModelLoadConfig) { loads++ }
        override fun generate(request:GenerationRequest)=flow<GenerationEvent> { error("Must not be used for device certification") }
        override fun cancelGeneration()=Unit
        override suspend fun unload()=Unit
        override suspend fun resetConversation()=Unit
        override fun capabilities()=RuntimeCapabilities(runtimeId,true,false,false,false,false)
        override fun metrics()=RuntimeMetrics()
    }
    private fun run(model:Boolean,block:suspend (AppGraph,FakeRuntime)->Unit)=runBlocking {
        val main=Dispatchers.Default.limitedParallelism(1);Dispatchers.setMain(main)
        val context=RuntimeEnvironment.getApplication()
        context.getSharedPreferences("app_model_preparation",0).edit().clear().commit()
        val db=Room.inMemoryDatabaseBuilder(context,WorkspaceDatabase::class.java).build()
        val scope=CoroutineScope(SupervisorJob()+main);val fake=FakeRuntime();val graph=AppGraph(context,listOf(fake),db,scope,scope)
        try {
            if(model)db.modelDao().insert(ModelEntity("synthetic","gemma-4-E2B-it.litertlm","/fixture.litertlm",fileHash="synthetic",fileSize=1,format="LITERT_LM",runtimeId=fake.runtimeId,compatibilityStatus="COMPATIBLE",importedAt=1))
            db.projectDao().upsert(ProjectEntity("retained-user-project","User",1,1))
            withTimeout(20_000) { withContext(main) { block(graph,fake) } }
            assertNotNull(db.projectDao().get("retained-user-project"));assertEquals(if(model)1 else 0,db.modelDao().observeAll().first().size)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin();db.close();Dispatchers.resetMain() }
    }
    @Test fun missingGemmaIsBlockedAndNeverPassed()=run(false) { graph,fake->
        val v=graph.deviceValidation;v.initialized.first { it };v.start(ValidationMode.QUICK);v.running.first { !it }
        val r=v.current.value!!;assertEquals("BLOCKED",r.summary.overall);assertTrue(r.tests.filter { it.selected }.all { it.reasonCode=="GEMMA_E2B_NOT_INSTALLED" });assertEquals(0,fake.loads)
    }
    @Test fun aFakeLiteRtCannotProduceADevicePass()=run(true) { graph,fake->
        val profile=AssistantProfile(ThinkingMode.OFF,emptySet());graph.assistantSettings.update("retained-user-project",profile)
        val v=graph.deviceValidation;v.initialized.first { it };v.start(ValidationMode.QUICK);v.running.first { !it }
        val r=v.current.value!!;assertEquals(EvidenceEnvironment.HOST_TESTED,r.environment);assertTrue(r.tests.none { it.status==ValidationStatus.PASS });assertEquals("REAL_LITERT_RUNTIME_REQUIRED",r.tests.first().reasonCode)
        assertEquals(0,fake.loads);assertEquals(profile,graph.assistantSettings.forProject("retained-user-project"));assertFalse(graph.validationBusy.value)
    }
    @Test fun anotherBenchmarkPreventsReservationAndPreferenceChanges()=run(false) { graph,_->
        val v=graph.deviceValidation;v.initialized.first { it };graph.validationBusy.value=true;v.start(ValidationMode.FULL,3);assertFalse(v.running.value);graph.validationBusy.value=false
    }
}
