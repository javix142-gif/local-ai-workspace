package com.localai.workspace.agents

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.google.gson.GsonBuilder
import com.localai.workspace.AppGraph
import com.localai.workspace.context.*
import com.localai.workspace.context.ContextBuilder
import com.localai.workspace.data.*
import com.localai.workspace.domain.model.*
import com.localai.workspace.skills.*
import com.localai.workspace.capabilities.*
import com.localai.workspace.semantic.v2.*
import com.localai.workspace.ui.*
import com.localai.workspace.validation.ValidationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.UUID

/** Isolated synthetic data, production ChatViewModel and the owner's ONE shared native runtime. */
class AgentsSkillsValidation(private val owner:AppGraph) {
    suspend fun run():String=withContext(Dispatchers.IO) {
        check(!owner.chatSessions.hasGeneration && !owner.performance.running.value && !owner.semanticDiagnostics.running.value){"ANOTHER_OPERATION_RUNNING"}
        withValidationReservation(owner.validationBusy) {
        val id="agents-skills-${UUID.randomUUID()}";val context=ValidationContext(owner.contextForMeasurements)
        val workspace=Room.databaseBuilder(context,WorkspaceDatabase::class.java,"$id-workspace.db").build()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val graph=AppGraph(context,databaseOverride=workspace,applicationScope=scope,nativeScope=scope,validationOwner=owner,validationId=id).also{it.agentsSkillsValidationEnabled=true}
        val rows=mutableListOf<Map<String,Any?>>()
        val sessions=mutableListOf<Pair<ChatViewModel,ViewModelStore>>()
        val report=File(owner.contextForMeasurements.filesDir,"agents-skills-diagnostics/validation.json").apply{parentFile?.mkdirs()}
        var mainModel:ModelEntity?=null
        var preparationPaused=false
        fun persist(state:String):String {
            val json=GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(mapOf("suite" to "AGENTS_SKILLS_V1","runId" to id,"state" to state,"timestamp" to System.currentTimeMillis(),"environment" to "DEVICE","appVersion" to com.localai.workspace.BuildConfig.VERSION_NAME,"pass" to rows.count{it["status"]=="PASS"},"fail" to rows.count{it["status"]=="FAIL"},"blocked" to rows.count{it["status"]=="BLOCKED"},"overall" to if(state!="COMPLETE")state else if(rows.all{it["status"]=="PASS"})"PASS"else"NOT_PASS","cases" to rows))
            val temp=File(report.path+".tmp");temp.writeText(json);check(temp.renameTo(report));return json
        }
        suspend fun test(name:String,native:Boolean=false,block:suspend()->Map<String,Any?>) {
            val start=System.nanoTime()
            try {if(native && mainModel==null){rows+=mapOf("id" to name,"status" to "BLOCKED","reason" to "MODEL_UNAVAILABLE");persist("RUNNING");return}
                val metrics=withTimeout(if(native)180_000 else 30_000){block()}
                rows+=mapOf("id" to name,"status" to "PASS","durationMs" to (System.nanoTime()-start)/1_000_000,"metrics" to metrics)
            }catch(timeout:TimeoutCancellationException){rows+=mapOf("id" to name,"status" to "FAIL","reason" to "TIMEOUT")}
            catch(cancel:CancellationException){throw cancel}
            catch(error:Exception){rows+=mapOf("id" to name,"status" to "FAIL","reason" to error.javaClass.simpleName)}
            persist("RUNNING")
        }
        suspend fun newSession():ChatViewModel=withContext(Dispatchers.Main.immediate) {
            val project="self-test-${UUID.randomUUID()}";val conversation="self-test-${UUID.randomUUID()}";val now=System.currentTimeMillis()
            val model=requireNotNull(mainModel)
            workspace.modelDao().insert(model);workspace.projectDao().upsert(ProjectEntity(project,"Agents fixture",now,now,model.id,memoryEnabled=true,archived=true));workspace.conversationDao().upsert(ConversationEntity(conversation,project,"Fixture",now,now))
            graph.assistantSettings.update(project,AssistantProfile(ThinkingMode.OFF,emptySet()))
            val store=ViewModelStore();val vm=ViewModelProvider(store,ChatViewModelFactory(project,graph,conversation))[ChatViewModel::class.java];sessions+=vm to store
            vm.awaitValidationReady();vm.models.first{it.any{m->m.id==model.id}};vm
        }
        suspend fun ask(vm:ChatViewModel,query:String):MessageEntity=withContext(Dispatchers.Main.immediate) {
            check(vm.send(query)){"SEND_NOT_ACCEPTED"};vm.isGenerating.first{!it}
            workspace.messageDao().recent(requireNotNull(vm.currentConversationId.value),3).first{it.role=="ASSISTANT"}.also {check(it.status=="COMPLETE" && it.content.isNotBlank()){vm.generationError.value?.code ?: "GENERATION_INCOMPLETE"}}
        }
        var greeting:ChatViewModel?=null
        var greetingCompleted=false
        try {
        mainModel=owner.workspace.allModels.first().firstOrNull{it.id==owner.modelPreparation.state.value.modelId && it.toDescriptor().runtime==RuntimeType.LITERT_LM}
            ?: owner.workspace.allModels.first().firstOrNull{it.toDescriptor().runtime==RuntimeType.LITERT_LM && it.importStatus==ModelImportStatus.READY.name}
            preparationPaused=true
            owner.modelPreparation.pauseForBenchmark()
            test("general_normal_chat",true){val vm=newSession();greeting=vm;ask(vm,"hola");check(vm.agentTrace.value?.resolution?.agent?.id==AgentResolver.GENERAL);check(graph.performance.lastGeneration.value?.second?.nativeCompletionObserved==true){"NATIVE_COMPLETION_NOT_OBSERVED"};greetingCompleted=true;mapOf("nativeCompletion" to true)}
            test("zero_skill_greeting",true){val vm=if(greetingCompleted)requireNotNull(greeting) else newSession().also{ask(it,"hola");check(graph.performance.lastGeneration.value?.second?.nativeCompletionObserved==true){"NATIVE_COMPLETION_NOT_OBSERVED"}};check(vm.agentTrace.value!!.selection.active.isEmpty());mapOf("activeSkills" to 0,"nativeCompletion" to true)}
            test("spreadsheet_activation"){val t=graph.agentSkills.resolve(SkillRoutingRequest("Revisa este Excel y dime por qué no cuadran los totales",attachments=listOf(RoutingAttachment("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","report.xlsx"))),null,null);check(t.selection.active.map{it.id}==listOf("skill.spreadsheet-analysis"));t.safeReport()}
            test("code_activation"){val t=graph.agentSkills.resolve(SkillRoutingRequest("Revisa esta clase y dime por qué podría producir un deadlock",attachments=listOf(RoutingAttachment("text/plain","ExampleViewModel.kt"))),null,null);check(t.selection.active.map{it.id}==listOf("skill.code-assistant"));t.safeReport()}
            test("disabled_skill"){graph.skillRegistry.enable("skill.code-assistant",false);val t=graph.agentSkills.resolve(SkillRoutingRequest("Review this code"),null,null);check(t.selection.active.isEmpty());graph.skillRegistry.enable("skill.code-assistant",true);mapOf("disabledExcluded" to true)}
            test("project_preference"){graph.agentRegistry.update(AgentDefinition("fixture.agent","Fixture",systemRole="Fixture instruction"));graph.agentRegistry.prefer("fixture.project","fixture.agent");check(graph.agentRegistry.resolve(null,"fixture.project").agent.id=="fixture.agent");mapOf("reason" to "PROJECT_AGENT")}
            test("general_fallback"){graph.agentRegistry.remove("fixture.agent");check(graph.agentRegistry.resolve(null,"fixture.project").agent.id==AgentResolver.GENERAL);mapOf("reason" to "GENERAL_FALLBACK")}
            test("memory_scope_isolation"){
                val now=System.currentTimeMillis();workspace.projectDao().upsert(ProjectEntity("fixture-a","A",now,now));workspace.projectDao().upsert(ProjectEntity("fixture-b","B",now,now))
                val memory=graph.contextFoundation.memory
                memory.create("Nebula is the preferred editor",SemanticScope(ScopeType.USER,"local"));memory.create("Other editor is preferred",SemanticScope(ScopeType.PROJECT,"fixture-b"));memory.create("Other agent prefers editor",SemanticScope(ScopeType.AGENT,"foreign"))
                val result=graph.contextFoundation.build(ContextRequest("preferred editor",ScopeAccess(projectId="fixture-a",agentId=AgentResolver.GENERAL)),evidence=emptyList())
                check(result.included.any{it.kind==ContextKind.MEMORY});check(result.included.none{it.scope.id in setOf("fixture-b","foreign")});mapOf("foreignExcluded" to true,"memoryCount" to result.included.count{it.kind==ContextKind.MEMORY})
            }
            test("context_budget"){val c=ContextBuilder().build(ContextRequest("hola",activeSkillInstructions=listOf(SkillInstruction("huge","x".repeat(10000)))),emptyList());check(c.request.skillIds.isEmpty() && c.droppedSkills["huge"]=="SKILL_CONTEXT_BUDGET");check(c.estimatedInputTokens<=c.inputBudget);c.safeReport()}
            test("tool_availability"){val tools=CapabilityPolicy().tools(SkillDefinition.STANDARD_TOOLS,setOf("calculator.evaluate"),emptyList());check(tools.filter{it.available}.map{it.toolId}==listOf("calculator.evaluate"));mapOf("tools" to tools)}
            test("stop_cancel",true){
                val vm=newSession();val accepted=CompletableDeferred<Unit>();graph.validationRequestObserver={accepted.complete(Unit)}
                withContext(Dispatchers.Main.immediate){check(vm.send("Explica con detalle qué es una red neuronal."))};accepted.await()
                vm.streamingText.first{it.isNotBlank()};check(vm.isGenerating.value){"GENERATION_FINISHED_BEFORE_CANCEL"}
                withContext(Dispatchers.Main.immediate){vm.stop();vm.isGenerating.first{!it}}
                check(workspace.messageDao().recent(requireNotNull(vm.currentConversationId.value),3).any{it.role=="ASSISTANT"&&it.status=="CANCELED"});check(!owner.inferenceGate.isLocked);graph.validationRequestObserver=null
                mapOf("cancelled" to true,"gateReleased" to true)
            }
            test("no_reentrant_context_gate",true){val vm=newSession();ask(vm,"¿Cuál es mi editor preferido?");val phases=graph.normalGenerationTrace.state.value!!.events.map{it.phase};check(phases.indexOf("MEMORY_COMPLETE")>=0 && phases.indexOf("CONVERSATION_GATE_ACQUIRED")>=0 && phases.indexOf("MEMORY_COMPLETE")<phases.indexOf("CONVERSATION_GATE_ACQUIRED"));check(!owner.inferenceGate.isLocked);mapOf("preparationBeforeGate" to true)}
            persist("COMPLETE")
        } catch(cancel:CancellationException){persist("CANCELLED");throw cancel}
        finally {
            withContext(NonCancellable){
                withContext(Dispatchers.Main.immediate){sessions.forEach{(vm,store)->vm.closeValidationSession();store.clear()};graph.modelPreparation.cancel()}
                scope.coroutineContext[Job]?.cancelAndJoin();graph.contextFoundation.database.close();graph.agentsSkillsDatabase.close();workspace.close()
                listOf("$id-workspace.db","context-memory-$id.db","agents-skills-$id.db").forEach{context.deleteDatabase(it)}
                if(preparationPaused)owner.modelPreparation.resumeAfterBenchmark()
            }
        }
        }
    }
}

/** Covers startup failure/cancellation as well as the native portion of the suite. */
internal suspend fun <T> withValidationReservation(busy:MutableStateFlow<Boolean>,block:suspend()->T):T {
    check(busy.compareAndSet(false,true)){"VALIDATION_RUNNING"}
    try{return block()}finally{busy.value=false}
}
