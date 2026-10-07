package com.localai.workspace.semantic.v2

import android.app.Application
import android.app.ApplicationExitInfo
import androidx.room.Room
import com.localai.workspace.data.WorkspaceDatabase
import com.localai.workspace.performance.DeviceMeasurement
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class SemanticStabilityTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun model():SemanticModelRecord {
        val file=File(context.filesDir,UUID.randomUUID().toString()).apply{writeText("LITERTLM")}
        return SemanticModelRecord("eg2", "EmbeddingGemma 2", "EG2",file.name,file.path,8,"a".repeat(64),"LITERTLM","litert",768,"768,256","TEXT,CODE,IMAGE,AUDIO","TEXT","0.17.1",1,"TEXT_PROBE_PASSED")
    }
    @Test fun successfulRegistrationIsImmediatelyDiscoverableBySuiteLookup()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,SemanticDatabase::class.java).build()
        val prefs="test-${UUID.randomUUID()}";val selection=SemanticModelSelection(context,prefs);val record=model()
        try{db.dao().model(record);selection.select(record);assertEquals(record.id,selection.find(db.dao().observeModels().first())?.id);assertEquals(SemanticProviderChoice.EG2,selection.choice);assertEquals("CPU",context.getSharedPreferences(prefs,0).getString("backend",null))}finally{db.close()}
    }
    @Test fun registeredOldImportWithoutSelectionIsRepaired(){val selection=SemanticModelSelection(context,"test-${UUID.randomUUID()}");val m=model().copy(validationStatus="PROBED_TEXT_NOT_DEVICE_VALIDATED");assertEquals(m,selection.find(listOf(m)));assertEquals(m.id,selection.selectedId);assertEquals(SemanticProviderChoice.EG2,selection.choice)}
    @Test fun repairDoesNotOverrideExplicitEg1Selection(){val selection=SemanticModelSelection(context,"test-${UUID.randomUUID()}");selection.choose(SemanticProviderChoice.EG1);selection.find(listOf(model()));assertEquals(SemanticProviderChoice.EG1,selection.choice)}
    @Test fun missingPrivateFileIsNotInstalled(){assertNull(SemanticModelSelection(context,"test-${UUID.randomUUID()}").find(listOf(model().copy(privatePath="/missing"))))}
    @Test fun failedLegacyImportDoesNotInvalidateInstalledProvider(){assertEquals("Installed",SemanticModelPresentation.legacyStatus(true,true,true));assertEquals("Error",SemanticModelPresentation.legacyStatus(true,false,true));assertEquals("Ready",SemanticModelPresentation.status(true,true,ProviderState.UNLOADED))}
    @Test fun stateLabelsNeverExposeInternalReasons(){assertEquals("Not installed",SemanticModelPresentation.status(false,false,ProviderState.UNLOADED));assertEquals("Loading",SemanticModelPresentation.status(true,true,ProviderState.LOADING));assertEquals("Ready",SemanticModelPresentation.status(true,true,ProviderState.UNLOADED));assertEquals("Error",SemanticModelPresentation.status(true,true,ProviderState.ERROR));assertEquals("Indexing",SemanticModelPresentation.status(true,true,ProviderState.READY,true))}
    @Test fun fileTypeDetectedBeforeProviderImporter(){assertEquals(SemanticProviderChoice.EG2,SemanticImportCoordinator.detect("LITERTLM".toByteArray()));assertEquals(SemanticProviderChoice.EG1,SemanticImportCoordinator.detect("GGUFabcd".toByteArray()));assertNull(SemanticImportCoordinator.detect("invalid".toByteArray()))}
    private open class Driver:EmbeddingNativeDriver {
        var ready=false;var closes=0
        override fun initialize(){ready=true}
        override fun isInitialized()=ready
        override fun compute(contents:List<InputData>,options:EmbeddingOptions)=FloatArray(options.outputSize ?: 768){1f}
        override fun close(){check(ready);ready=false;closes++}
    }
    @Test fun repeatedDimensionSwitchNeverReusesWrongEngine()=runBlocking {
        val drivers=mutableListOf<Driver>();val manager=EmbeddingEngineManager(context,Mutex()){Driver().also{drivers+=it}}
        manager.select(model());repeat(12){for(d in listOf(768,256,768)){manager.configureDimension(d);assertEquals(d,manager.embed(SemanticInput.Text("fixed"),EmbeddingTask.SEARCH,d).vector.size)}};manager.unload()
        assertEquals(25,drivers.size);assertTrue(drivers.all{it.closes==1});assertEquals(768,manager.configuredDimension)
    }
    @Test fun failedInitializationWithLiveHandleIsClosed()=runBlocking {
        val driver=object:Driver(){override fun initialize(){super.initialize();error("late initialization failure")}}
        val manager=EmbeddingEngineManager(context,Mutex()){driver};manager.select(model())
        try{manager.embed(SemanticInput.Text("fixed"),EmbeddingTask.SEARCH);fail("expected failure")}catch(expected:SemanticFailure){assertEquals(SemanticError.MODEL_LOAD_FAILED,expected.code)}
        assertEquals(1,driver.closes);assertFalse(driver.ready)
    }
    @Test fun journalFailureBeforeCloseStillReleasesNativeHandle()=runBlocking {
        val driver=Driver();val manager=EmbeddingEngineManager(context,Mutex()){driver};manager.select(model());manager.initialize()
        manager.nativeObserver={if(it.operation=="CLOSE"&&!it.completed)error("storage full")}
        try{manager.unload();fail("expected journal error")}catch(expected:IllegalStateException){assertEquals("storage full",expected.message)}
        assertEquals(1,driver.closes);assertEquals(ProviderState.UNLOADED,manager.status.value.state)
    }
    @Test fun closeAndDimensionSwitchWaitForActiveCompute()=runBlocking {
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val computing=AtomicInteger();var overlap=false
        val driver=object:Driver(){override fun compute(contents:List<InputData>,options:EmbeddingOptions):FloatArray{computing.incrementAndGet();entered.countDown();check(release.await(5,TimeUnit.SECONDS));try{return super.compute(contents,options)}finally{computing.decrementAndGet()}};override fun close(){if(computing.get()>0)overlap=true;super.close()}}
        val manager=EmbeddingEngineManager(context,Mutex()){driver};manager.select(model())
        val request=async(Dispatchers.IO){manager.embed(SemanticInput.Text("fixed"),EmbeddingTask.SEARCH)}
        assertTrue(entered.await(5,TimeUnit.SECONDS));val change=async(Dispatchers.IO){manager.configureDimension(256)}
        yield();assertFalse(change.isCompleted);release.countDown();request.await();change.await();assertFalse(overlap);assertEquals(1,driver.closes);assertEquals(256,manager.configuredDimension)
    }
    @Test fun repeatedInitializeUnloadIsIdempotent()=runBlocking {
        val drivers=mutableListOf<Driver>();val manager=EmbeddingEngineManager(context,Mutex()){Driver().also{drivers+=it}};manager.select(model())
        repeat(10){manager.initialize();manager.initialize();manager.unload();manager.unload()};assertEquals(10,drivers.size);assertTrue(drivers.all{it.closes==1})
    }
    private class Port(val m:SemanticModelRecord):SemanticBenchmarkPort {
        override val modelHash=m.sha256;val prepared=mutableListOf<Int>();var unloads=0;var calls=0;var active=0;var peak=0
        override fun state()=ProviderStatus(ProviderState.READY,effective=SemanticBackend.CPU)
        override fun capture()=DeviceMeasurement(100L+calls,1000L-calls,2000,"NONE")
        override suspend fun prepare(dimension:Int):Long{prepared+=dimension;return 42}
        override suspend fun embed(input:SemanticInput,task:EmbeddingTask,dimension:Int):EmbeddingResult{active++;peak=maxOf(peak,active);yield();calls++;active--;return EmbeddingResult(EmbeddingEngineManager.space(m,dimension),SemanticVectors.normalize(FloatArray(dimension){1f}),calls.toLong())}
        override suspend fun unload(){unloads++}
    }
    private fun progress(n:Int=5)=SemanticBenchmarkProgress(UUID.randomUUID().toString(),1,123,iterations=n)
    @Test fun benchmarkSamplesAreSequentialAndColdLoadSeparate()=runBlocking {
        val port=Port(model());val checkpoints=mutableListOf<SemanticBenchmarkProgress>();val result=SemanticBenchmarkRunner(port){checkpoints+=it}.run(progress(10),listOf(768))
        assertEquals(1,port.peak);assertEquals(listOf(768),port.prepared);assertEquals(1,port.unloads);assertEquals(18,port.calls)
        val record=result.records.single();assertEquals(42L,record.loadMs);assertEquals(1L,record.firstEmbeddingMs);assertEquals(2L,record.warmupMs);assertEquals((3L..12L).toList(),record.warmSamplesMs);assertEquals(7L,record.p50Ms);assertEquals(12L,record.p95Ms);assertEquals(3072,record.vectorBytes)
        assertTrue(checkpoints.any{it.lastCompletedIteration==10&&it.warmSamplesMs.size==10})
    }
    @Test fun comparisonUsesIdenticalFixturesAndDimensions()=runBlocking {
        val port=Port(model());val result=SemanticBenchmarkRunner(port){}.run(progress(2),listOf(768,256));assertEquals(listOf(768,256),port.prepared);assertEquals(2,port.unloads);assertEquals(listOf(3072,1024),result.records.map{it.vectorBytes});assertEquals(1,result.records.map{it.fixtureVersion}.distinct().size);assertEquals("NO_AUTOMATIC_RECOMMENDATION",result.recommendation)
    }
    @Test fun cancellationKeepsCompletedSamples()=runBlocking {
        val journal=SemanticBenchmarkJournal(File(context.filesDir,UUID.randomUUID().toString()));val port=Port(model())
        try{SemanticBenchmarkRunner(port){journal.write(it);if(it.lastCompletedIteration==2)throw CancellationException("cancel")}.run(progress(),listOf(768));fail("expected cancel")}catch(expected:CancellationException){}
        assertEquals(2,journal.read()!!.warmSamplesMs.size);assertEquals(2,journal.read()!!.lastCompletedIteration);assertEquals(1,port.peak)
    }
    @Test fun restartPreservesPartialReportAndNeverReportsSuccess(){val journal=SemanticBenchmarkJournal(File(context.filesDir,UUID.randomUUID().toString()));val before=progress().copy(currentPhase="MEASURE",currentIteration=3,lastCompletedIteration=2,warmSamplesMs=listOf(10,11),nativeInFlight=true);journal.write(before);val after=journal.recover()!!;assertEquals("ABORTED_PROCESS_RESTART",after.status);assertEquals("UNKNOWN_PROCESS_RESTART",after.failureKind);assertEquals(before.warmSamplesMs,after.warmSamplesMs);assertTrue(after.nativeInFlight);assertEquals("MEASURE",after.currentPhase);assertEquals(after,journal.read())}
    @Test fun completedBenchmarkDoesNotBecomeAbortedOnRestart(){val journal=SemanticBenchmarkJournal(File(context.filesDir,UUID.randomUUID().toString()));val result=progress().copy(status="SUCCESS");journal.write(result);assertEquals(result,journal.recover())}
    @Test fun exitClassificationDoesNotInventOom(){assertEquals("UNKNOWN_PROCESS_RESTART",ProcessExitClassification.classify(null));assertEquals("NATIVE_PROCESS_DEATH",ProcessExitClassification.classify(ProcessExitEvidence(ApplicationExitInfo.REASON_CRASH_NATIVE,0,1)));assertEquals("ANDROID_LMK_SUSPECTED",ProcessExitClassification.classify(ProcessExitEvidence(ApplicationExitInfo.REASON_LOW_MEMORY,0,1)));assertEquals("NATIVE_ABORT_SUSPECTED",ProcessExitClassification.classify(ProcessExitEvidence(ApplicationExitInfo.REASON_SIGNALED,6,1)))}
    @Test fun comparisonRestoresUserProfileAndDimension()=runBlocking {
        val workspace=Room.inMemoryDatabaseBuilder(context,WorkspaceDatabase::class.java).build();val db=Room.inMemoryDatabaseBuilder(context,SemanticDatabase::class.java).build();val gate=Mutex();val drivers=mutableListOf<Driver>();val manager=EmbeddingEngineManager(context,gate){Driver().also{drivers+=it}}
        val layer=SemanticLayer(context,workspace,gate,db,"test-${UUID.randomUUID()}",manager);val record=model();db.dao().model(record)
        val profile=EmbeddingRuntimeProfile(SemanticBackend.CPU,RuntimeProfile.TEXT_VISION);layer.activate(record.id,profile);layer.setDimension(768)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO);val busy=MutableStateFlow(false);val diagnostics=SemanticDiagnostics(context,scope,{layer},busy){false}
        try{diagnostics.start(2,compare=true);withTimeout(15000){diagnostics.running.first{!it}};assertEquals("SUCCESS",diagnostics.progress.value!!.status);assertEquals(2,diagnostics.progress.value!!.records.size);assertEquals(profile,manager.profile);assertEquals(768,manager.configuredDimension);assertEquals(768,layer.dimension);assertFalse(busy.value);assertEquals(2,drivers.size);assertTrue(drivers.all{it.closes==1})}finally{scope.cancel();manager.unload();db.close();workspace.close()}
    }
    @Test fun storageFailureDoesNotEscapeApplicationOwnedBenchmarkJob()=runBlocking {
        val files=File(context.cacheDir,UUID.randomUUID().toString()).apply{mkdirs()};File(files,"semantic-v2-reports").writeText("not a directory")
        val wrapped=object:android.content.ContextWrapper(context){override fun getFilesDir()=files}
        var escaped=0;val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO+CoroutineExceptionHandler{_,_->escaped++});val busy=MutableStateFlow(false)
        val diagnostics=SemanticDiagnostics(wrapped,scope,{error("engine must not be called")},busy){false}
        try{diagnostics.start(1);withTimeout(10000){diagnostics.running.first{!it}};assertEquals(0,escaped);assertEquals("FAILED",diagnostics.progress.value!!.status);assertFalse(busy.value)}finally{scope.cancel();files.deleteRecursively()}
    }

    @Test fun concurrentInitializeUsesSingleNativeCandidate()=runBlocking {
        val drivers=mutableListOf<Driver>();val manager=EmbeddingEngineManager(context,Mutex()){Driver().also{drivers+=it}};manager.select(model())
        coroutineScope{(1..10).map{async(Dispatchers.IO){manager.initialize()}}.awaitAll()}
        assertEquals(1,drivers.size);manager.unload();assertEquals(1,drivers.single().closes)
    }
    @Test fun cancellingBenchmarkRestoresConfigurationAfterNativeReturns()=runBlocking {
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val workspace=Room.inMemoryDatabaseBuilder(context,WorkspaceDatabase::class.java).build();val db=Room.inMemoryDatabaseBuilder(context,SemanticDatabase::class.java).build();val gate=Mutex()
        val manager=EmbeddingEngineManager(context,gate){object:Driver(){var calls=0;override fun compute(contents:List<InputData>,options:EmbeddingOptions):FloatArray{if(++calls==3){entered.countDown();check(release.await(5,TimeUnit.SECONDS))};return super.compute(contents,options)}}}
        val layer=SemanticLayer(context,workspace,gate,db,"test-${UUID.randomUUID()}",manager);val record=model();db.dao().model(record)
        val profile=EmbeddingRuntimeProfile(SemanticBackend.CPU,RuntimeProfile.TEXT_VISION);layer.activate(record.id,profile);layer.setDimension(256)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO);val busy=MutableStateFlow(false);val diagnostics=SemanticDiagnostics(context,scope,{layer},busy){false}
        try{diagnostics.start(10,dimension=768);assertTrue(entered.await(5,TimeUnit.SECONDS));diagnostics.cancel();release.countDown();withTimeout(10000){diagnostics.running.first{!it}};assertEquals("CANCELLED",diagnostics.progress.value!!.status);assertEquals(profile,manager.profile);assertEquals(256,manager.configuredDimension);assertEquals(256,layer.dimension);assertFalse(busy.value)}finally{release.countDown();scope.cancel();manager.unload();db.close();workspace.close()}
    }

}
