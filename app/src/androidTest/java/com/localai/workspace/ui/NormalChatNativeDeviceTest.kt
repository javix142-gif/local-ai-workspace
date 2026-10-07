package com.localai.workspace.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.localai.workspace.LocalAiApplication
import com.localai.workspace.MainActivity
import com.localai.workspace.data.*
import com.localai.workspace.domain.model.*
import com.localai.workspace.semantic.v2.*
import com.localai.workspace.context.MemoryKind
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** PHYSICAL_DEVICE_TEST_REQUIRED. Real app ChatViewModel -> LiteRT worker, no diagnostic bypass.
 * Uses installed Gemma and existing data; removes only its own synthetic workspace/memory. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class NormalChatNativeDeviceTest {
 private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as LocalAiApplication
 private fun run(block:suspend (com.localai.workspace.AppGraph,ChatViewModel,String)->Unit,contextOn:Boolean,project:Boolean=false)=runBlocking<Unit>{
  ActivityScenario.launch(MainActivity::class.java).use {
   val graph=app.graph
   Assume.assumeFalse("Finish user requests/validation first",graph.chatSessions.hasGeneration||graph.validationBusy.value||graph.performance.running.value)
   val model=graph.workspace.allModels.first().firstOrNull{m->m.toDescriptor().runtime==RuntimeType.LITERT_LM&&File(m.localPath).isFile&&(m.displayName+" "+m.family).lowercase().let{"gemma" in it && "e2b" in it}}
   Assume.assumeNotNull("Installed Gemma E2B required; never fake native output",model)
   val flag=graph.contextFoundation.enabled.value
   val id=if(project)graph.workspace.createProject("QA normal-chat 0.4.1")else graph.workspace.createChat()
   var vm:ChatViewModel?=null
   var memoryId:String?=null
   try {
    graph.contextFoundation.enable(contextOn)
    if(project)memoryId=graph.contextFoundation.memory.create("La base de datos de este proyecto es SQLite.",SemanticScope(ScopeType.PROJECT,id),MemoryKind.FACT).id
    withContext(Dispatchers.Main){graph.assistantSettings.update(id,AssistantProfile(ThinkingMode.OFF,emptySet()));vm=graph.chatSessions.get(id,null);vm!!.enterScreen();vm!!.selectModel(model!!.id)}
    withTimeout(240000){graph.modelPreparation.state.first{it.ready&&it.modelId==model!!.id}}
    block(graph,vm!!,id)
   }finally{
    withContext(NonCancellable){withContext(Dispatchers.Main){vm?.cancelAndAwait();graph.chatSessions.removeWorkspace(id)}
     memoryId?.let{graph.contextFoundation.memory.delete(it)};graph.workspace.deleteWorkspace(id);graph.contextFoundation.enable(flag)}
   }
  }
 }
 private suspend fun answer(graph:com.localai.workspace.AppGraph,vm:ChatViewModel,text:String):String {
  assertTrue(withContext(Dispatchers.Main){vm.send(text)})
  withTimeout(240000){vm.streamingText.first{it.isNotBlank()};vm.isGenerating.first{!it}}
  val row=graph.workspace.recentMessages(vm.currentConversationId.value!!,10).last{it.role=="ASSISTANT"}
  assertEquals("COMPLETE",row.status);assertTrue(row.content.isNotBlank())
  assertFalse(graph.inferenceGate.isLocked)
  val trace=graph.normalGenerationTrace.state.value!!
  assertTrue(trace.events.any{it.phase=="SDK_CALLBACK_FINAL"});assertTrue(trace.events.any{it.phase=="REQUEST_COMPLETE"})
  // Native metric must actually exist; a UI placeholder/error is not a callback.
  assertNotNull(trace.firstCallbackMs)
  return row.content
 }
 @Test fun plain_chat_legacy_off_real_native()=run({g,v,_->answer(g,v,"hola");answer(g,v,"¿cómo estás?")},false)
 @Test fun plain_chat_context_v1_on_real_native()=run({g,v,_->answer(g,v,"hola")},true)
 @Test fun project_chat_context_v1_on_real_native()=run({g,v,_->assertTrue(answer(g,v,"¿Qué base de datos utiliza este proyecto?").contains("SQLite",true))},true,true)
 @Test fun cancel_then_retry_real_native()=run({g,v,_->
  assertTrue(withContext(Dispatchers.Main){v.send("Explica en 200 palabras cómo funciona una computadora.")})
  withTimeout(240000){v.streamingText.first{it.isNotBlank()}}
  withContext(Dispatchers.Main){v.stop()}
  withTimeout(30000){v.isGenerating.first{!it}}
  answer(g,v,"hola")
 },false)
}
