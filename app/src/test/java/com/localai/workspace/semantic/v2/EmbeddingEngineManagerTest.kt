package com.localai.workspace.semantic.v2

import android.app.Application
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=Application::class)
class EmbeddingEngineManagerTest {
    private fun model(hash:String="a".repeat(64))=SemanticModelRecord(hash,"EG2","EG2","eg2.litertlm","unused",42,hash,"LITERTLM","litert",768,"768,256","TEXT,CODE,IMAGE,AUDIO","TEXT","0.17.1",1,"PROBED")
    private class Driver(val fail:Boolean=false,val badVector:Boolean=false):EmbeddingNativeDriver {
        var ready=false;var closes=0
        override fun initialize(){if(fail)error("GPU failure");ready=true}
        override fun isInitialized()=ready
        override fun compute(contents:List<InputData>,options:EmbeddingOptions)=FloatArray(options.outputSize ?: 768){if(badVector)Float.NaN else 1f}
        override fun close(){ready=false;closes++}
    }
    @Test fun twoRequestsUseOneEngineAndUnloadClosesIt()=runBlocking {
        val drivers=mutableListOf<Driver>();val manager=EmbeddingEngineManager(RuntimeEnvironment.getApplication(),Mutex()){Driver().also{drivers+=it}}
        manager.select(model());manager.embed(SemanticInput.Text("one"),EmbeddingTask.SEARCH);manager.embed(SemanticInput.Text("two"),EmbeddingTask.SEARCH);assertEquals(1,drivers.size);manager.unload();assertEquals(1,drivers.single().closes);assertEquals(ProviderState.UNLOADED,manager.status.value.state)
    }
    @Test fun gpuFailureIsVisibleAndNotRetriedOnNextRequest()=runBlocking {
        val configs=mutableListOf<EmbeddingEngineConfig>();val manager=EmbeddingEngineManager(RuntimeEnvironment.getApplication(),Mutex()){config->configs+=config;Driver(fail=config.backend is Backend.GPU)}
        manager.select(model(),EmbeddingRuntimeProfile(SemanticBackend.GPU_EXPERIMENTAL));manager.embed(SemanticInput.Text("one"),EmbeddingTask.SEARCH);manager.embed(SemanticInput.Text("two"),EmbeddingTask.SEARCH)
        assertEquals(1,configs.count{it.backend is Backend.GPU});assertEquals(SemanticBackend.CPU,manager.status.value.effective);assertTrue(manager.status.value.fallbackReason!!.startsWith("GPU_INITIALIZATION_FAILED"));manager.unload()
    }
    @Test fun modelChangeDoesNotKeepNativeEngine()=runBlocking {
        val drivers=mutableListOf<Driver>();val manager=EmbeddingEngineManager(RuntimeEnvironment.getApplication(),Mutex()){Driver().also{drivers+=it}}
        manager.select(model());manager.embed(SemanticInput.Text("one"),EmbeddingTask.SEARCH);manager.select(model("b".repeat(64)));assertEquals(1,drivers.single().closes);manager.embed(SemanticInput.Text("two"),EmbeddingTask.SEARCH);assertEquals(2,drivers.size);manager.unload()
    }
    @Test fun invalidVectorInvalidatesEngine()=runBlocking {
        val driver=Driver(badVector=true);val manager=EmbeddingEngineManager(RuntimeEnvironment.getApplication(),Mutex()){driver};manager.select(model())
        try{manager.embed(SemanticInput.Text("one"),EmbeddingTask.SEARCH);fail("Invalid vector accepted")}catch(e:SemanticFailure){assertEquals(SemanticError.NON_FINITE_VECTOR,e.code)}
        assertEquals(1,driver.closes);assertEquals(ProviderState.ERROR,manager.status.value.state)
    }
    @Test fun visionThenTextKeepsExpandedEngine()=runBlocking {
        val context=RuntimeEnvironment.getApplication();val image=File(context.filesDir,"test-image.bytes").apply{writeBytes(byteArrayOf(1))};val drivers=mutableListOf<Driver>();val configs=mutableListOf<EmbeddingEngineConfig>();val manager=EmbeddingEngineManager(context,Mutex()){config->configs+=config;Driver().also{drivers+=it}}
        try{manager.select(model());manager.embed(SemanticInput.Text("one"),EmbeddingTask.SEARCH);manager.embed(SemanticInput.Image(image.path),EmbeddingTask.DOCUMENT);manager.embed(SemanticInput.Text("two"),EmbeddingTask.SEARCH);assertEquals(2,drivers.size);assertNotNull(configs.last().visionBackend)}finally{manager.unload();image.delete()}
    }
    @Test fun cancellationWhileWaitingForResourceGateNeverInitializesNativeEngine()=runBlocking {
        val gate=Mutex();var created=0;val manager=EmbeddingEngineManager(RuntimeEnvironment.getApplication(),gate){created++;Driver()};manager.select(model());gate.lock()
        val job=launch { manager.embed(SemanticInput.Text("one"),EmbeddingTask.SEARCH) };yield();job.cancelAndJoin();gate.unlock();assertEquals(0,created);manager.unload()
    }
    @Test fun deselectClearsTemporaryModelAndNativeEngine()=runBlocking {
        val driver=Driver();val manager=EmbeddingEngineManager(RuntimeEnvironment.getApplication(),Mutex()){driver}
        manager.select(model());manager.embed(SemanticInput.Text("probe"),EmbeddingTask.SEARCH);manager.deselect()
        assertNull(manager.selected);assertEquals(1,driver.closes);assertEquals(ProviderState.UNLOADED,manager.status.value.state)
    }
}
