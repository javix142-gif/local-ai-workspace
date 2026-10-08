package com.localai.workspace.ui

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import androidx.lifecycle.viewModelScope
import com.localai.workspace.AppGraph
import com.localai.workspace.data.*
import com.localai.workspace.domain.inference.*
import com.localai.workspace.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Actual ChatViewModel + Room/pool/gate; only native boundary is a test double. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NormalChatHotfixTest {
 private val context get()=RuntimeEnvironment.getApplication()
 private val main=Dispatchers.Default.limitedParallelism(1)
 private lateinit var db:WorkspaceDatabase
 private lateinit var scope:CoroutineScope
 private lateinit var graph:AppGraph
 private lateinit var backend:Backend
 private val controllers=mutableListOf<ChatViewModel>()
 private data class Turn(val request:GenerationRequest,val finish:CompletableDeferred<Unit> = CompletableDeferred())
 private class Backend:InferenceRuntime {
  override val runtimeType=RuntimeType.LITERT_LM
  override val runtimeId="litert-lm-android"
  val turns=Channel<Turn>(Channel.UNLIMITED)
  var active:Turn?=null
  private val progress=MutableStateFlow(GenerationProgress())
  var replayOldCallbackOnLoad=false
  private var loads=0L
  override fun observeProgress()=progress
  override fun supports(format:ModelFormat)=format==ModelFormat.LITERT_LM
  override fun availableAccelerators()=setOf(AcceleratorType.CPU)
  override suspend fun inspectModel(source:ModelSource):ModelMetadata=error("not used")
  override suspend fun load(config:ModelLoadConfig){progress.value=if(replayOldCallbackOnLoad)GenerationProgress(GenerationStage.GENERATING,event="LITERT_FIRST_TOKEN",elapsedMs=++loads)else GenerationProgress(GenerationStage.IDLE)}
  override fun generate(request:GenerationRequest)=flow {
   val turn=Turn(request);active=turn
   try{emit(GenerationEvent.Token("Hola"));turns.send(turn);turn.finish.await();emit(GenerationEvent.Token(" local"));emit(GenerationEvent.Completed)}finally{active=null}
  }
  override fun cancelGeneration(){active?.finish?.cancel()}
  override suspend fun unload()=Unit
  override suspend fun resetConversation()=Unit
  override fun capabilities()=RuntimeCapabilities(runtimeId,true,false,false,false,false)
  override fun metrics()=RuntimeMetrics(backend=runtimeId,modelRetainedAfterStop=true)
 }
 @Before fun open(){
  Dispatchers.setMain(main)
  listOf("app_model_preparation","litert_chat_defaults_v017","context_foundation_v1","local_assistant_profiles").forEach{context.getSharedPreferences(it,0).edit().clear().commit()}
  context.getSharedPreferences("context_foundation_v1",0).edit().putBoolean("enabled",false).commit()
  db=Room.inMemoryDatabaseBuilder(context,WorkspaceDatabase::class.java).build()
  scope=CoroutineScope(SupervisorJob()+main);backend=Backend();graph=AppGraph(context,listOf(backend),db,scope,scope)
 }
 @After fun close()=runBlocking {
  withContext(main){graph.chatSessions.clear();graph.modelPreparation.cancel()}
  // ViewModel scopes are independent of appScope; await their children before replacing Main.
  controllers.forEach{it.viewModelScope.coroutineContext[Job]?.join()}
  scope.coroutineContext[Job]!!.cancelAndJoin();Dispatchers.resetMain();db.close()
 }
 private fun check(block:suspend ()->Unit)=runBlocking<Unit>{withTimeout(20000){withContext(main){block()}}}
 private suspend fun chat(project:String?=null):ChatViewModel{
  if(db.modelDao().get("model")==null)db.modelDao().insert(ModelEntity("model","Gemma","/private/model.litertlm",fileHash="hash",fileSize=4096,format="LITERT_LM",runtimeId=backend.runtimeId,compatibilityStatus="COMPATIBLE",configuredContext=4096,maxOutputTokens=256,importedAt=1))
  val id=project ?: graph.workspace.createChat()
  val vm=graph.chatSessions.get(id,null);controllers+=vm;vm.enterScreen();vm.currentConversationId.first{it!=null};graph.modelPreparation.state.first{it.ready};return vm
 }
 private suspend fun complete(vm:ChatViewModel):Turn {
  val turn=backend.turns.receive();assertEquals("Hola",vm.streamingText.value);turn.finish.complete(Unit);vm.isGenerating.first{!it}
  assertEquals("COMPLETE",graph.workspace.recentMessages(vm.currentConversationId.value!!,10).last().status)
  assertEquals("Hola local",vm.streamingText.value);assertFalse(graph.inferenceGate.isLocked);return turn
 }
 // 0.5.0 deliberately prepares context before the gate on the rollback path too.
 @Test fun plain_chat_legacy_off()=check{val vm=chat();assertTrue(vm.send("hola"));complete(vm);assertTrue(graph.normalGenerationTrace.state.value!!.events.indexOfFirst{it.phase=="CONVERSATION_GATE_ACQUIRED"}>graph.normalGenerationTrace.state.value!!.events.indexOfFirst{it.phase=="CONTEXT_BUILD_COMPLETE"})}
 @Test fun plain_chat_context_v1_on()=check{
  graph.contextFoundation.enable(true);val vm=chat();assertTrue(vm.send("hola"));val turn=complete(vm)
  assertNotNull(turn.request.conversation!!.systemInstruction)
  assertTrue(graph.normalGenerationTrace.state.value!!.contextBuilderEnabled)
  val phases=graph.normalGenerationTrace.state.value!!.events.map{it.phase}
  assertTrue(phases.indexOf("CONTEXT_V1_BUILD_COMPLETE")<phases.indexOf("CONVERSATION_GATE_ACQUIRED"))
 }
 @Test fun project_chat_context_v1_on()=check{
  graph.contextFoundation.enable(true);val id=graph.workspace.createProject("A")
  graph.contextFoundation.memory.create("La base de datos de este proyecto es SQLite.",com.localai.workspace.semantic.v2.SemanticScope(com.localai.workspace.semantic.v2.ScopeType.PROJECT,id),com.localai.workspace.context.MemoryKind.FACT)
  val vm=chat(id);vm.send("¿Qué base de datos utiliza este proyecto?");val turn=complete(vm)
  assertTrue(turn.request.conversation!!.userMessage.contains("SQLite"))
 }
 @Test fun plain_chat_think_off_tools_off()=check{
  val vm=chat();graph.assistantSettings.update(vm.project.value?.id ?: db.conversationDao().get(vm.currentConversationId.value!!)!!.projectId!!,AssistantProfile(ThinkingMode.OFF,emptySet()))
  vm.send("hola");val turn=complete(vm);assertFalse(turn.request.conversation!!.enableThinking)
  assertEquals(0,graph.normalGenerationTrace.state.value!!.toolsEffectiveCount)
 }
 @Test fun plain_chat_think_auto_tools_auto()=check{val vm=chat();vm.send("hola");val turn=complete(vm);assertFalse(turn.request.conversation!!.enableThinking)}
 @Test fun generation_first_callback()=check{
  val vm=chat();vm.send("hola");val turn=backend.turns.receive()
  assertEquals("Hola",vm.streamingText.value);assertNotNull(graph.normalGenerationTrace.state.value!!.firstVisibleUiMs)
  turn.finish.complete(Unit);vm.isGenerating.first{!it}
 }
 @Test fun generation_final_completion()=check{
  val vm=chat();vm.send("hola");complete(vm)
  val trace=graph.normalGenerationTrace.state.value!!;assertNotNull(trace.completedAt)
  assertTrue(trace.events.any{it.phase=="SDK_CALLBACK_FINAL"});assertTrue(trace.events.any{it.phase=="PERSIST_COMPLETE"})
  assertTrue(trace.events.any{it.phase=="REQUEST_COMPLETE"});assertEquals(false,trace.nativeInFlight)
 }
 @Test fun cancel_then_retry()=check{val vm=chat();vm.send("hola");backend.turns.receive();vm.stop();vm.isGenerating.first{!it};assertFalse(graph.inferenceGate.isLocked);vm.send("hola");complete(vm)}
 @Test fun cancel_then_new_chat()=check{val vm=chat();vm.send("hola");backend.turns.receive();vm.stop();vm.isGenerating.first{!it};val next=chat();next.send("hola");complete(next)}
 @Test fun new_chat_after_app_recreation()=check{
  val vm=chat();vm.send("hola");complete(vm)
  graph.chatSessions.clear();graph.modelPreparation.cancel()
  graph=AppGraph(context,listOf(backend),db,scope,scope)
  val next=chat();next.send("hola");complete(next)
 }
 @Test fun context_v1_toggle_on_off()=check{val vm=chat();for(on in listOf(true,false,true,false)){graph.contextFoundation.enable(on);vm.send("hola");complete(vm);assertEquals(on,graph.normalGenerationTrace.state.value!!.contextBuilderEnabled)}}
 @Test fun normal_chat_does_not_wait_for_memory_when_off()=check{
  val vm=chat();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
  val lock=scope.launch(Dispatchers.IO){graph.contextFoundation.database.withTransaction{entered.complete(Unit);release.await()}}
  entered.await();try{vm.send("hola");complete(vm)}finally{release.complete(Unit);lock.join()}
 }
 @Test fun normal_chat_does_not_wait_for_semantic_engine_when_off()=check{
  val vm=chat();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
  val lock=scope.launch{graph.semanticV2.manager.exclusive{entered.complete(Unit);release.await()}}
  entered.await();try{vm.send("hola");complete(vm)}finally{release.complete(Unit);lock.join()}
 }
 @Test fun legacy_off_does_not_open_context_database()=check{
  val vm=chat();val file=context.getDatabasePath("memory_context.db");assertFalse(file.exists())
  vm.send("hola");complete(vm);assertFalse(file.exists())
 }
 @Test fun stale_load_progress_is_not_a_generation_callback()=check {
  backend.replayOldCallbackOnLoad=true;val vm=chat();vm.send("hola");complete(vm)
  val trace=graph.normalGenerationTrace.state.value!!
  assertFalse(trace.events.any{it.phase=="SDK_CALLBACK_FIRST"})
  assertTrue(trace.events.any{it.phase=="WORKER_CHECKPOINT"})
 }
 @Test fun stop_before_acquire_then_retry()=check{
  val vm=chat();vm.send("hola");vm.stop();vm.isGenerating.first{!it}
  vm.send("hola");complete(vm)
 }
 @Test fun canonicalDefaultAndZeroSkillGreeting()=check {
  context.getSharedPreferences("context_foundation_v1",0).edit().remove("enabled").commit()
  val vm=chat();vm.send("hola");complete(vm)
  assertTrue(graph.normalGenerationTrace.state.value!!.contextBuilderEnabled)
  assertTrue(vm.agentTrace.value!!.selection.active.isEmpty());assertEquals("agent.general",vm.agentTrace.value!!.resolution.agent.id)
 }
 @Test fun agentInstructionsAndActiveSkillReachRealRequestButInactiveBodiesDoNot()=check {
  graph.contextFoundation.enable(true)
  graph.agentRegistry.update(com.localai.workspace.agents.AgentDefinition("agent.custom","Custom",systemRole="Include the marker CUSTOM_ROLE",skillIds=setOf("skill.code-assistant")))
  val vm=chat();vm.selectAgent("agent.custom");vm.send("Revisa código Kotlin");val turn=complete(vm)
  assertTrue(turn.request.conversation!!.systemInstruction!!.contains("CUSTOM_ROLE"))
  assertTrue(turn.request.conversation!!.systemInstruction!!.contains(com.localai.workspace.skills.BuiltInSkills.definitions.first{it.id=="skill.code-assistant"}.instructions))
  assertFalse(turn.request.conversation!!.systemInstruction!!.contains(com.localai.workspace.skills.BuiltInSkills.definitions.first{it.id=="skill.spreadsheet-analysis"}.instructions))
 }
 @Test fun embeddingCapableMemoryPreparationFinishesBeforeGenerationGate()=check {
  graph.contextFoundation.enable(true);val vm=chat();vm.send("¿Cuál es mi editor preferido?");complete(vm)
  val phases=graph.normalGenerationTrace.state.value!!.events.map{it.phase}
  assertTrue(phases.indexOf("MEMORY_COMPLETE")<phases.indexOf("CONVERSATION_GATE_ACQUIRED"))
  assertFalse(graph.inferenceGate.isLocked)
 }

}
