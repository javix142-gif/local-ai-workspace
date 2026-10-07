package com.localai.workspace.context

import android.app.Application
import androidx.room.Room
import com.localai.workspace.semantic.v2.*
import kotlinx.coroutines.*
import com.localai.workspace.AppGraph
import com.localai.workspace.data.WorkspaceDatabase
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class MemoryRelevanceTest {
    private lateinit var db:MemoryContextDatabase
    private lateinit var memory:MemoryManager
    private val user=SemanticScope(ScopeType.USER,"local")
    private val access=ScopeAccess()
    private val nebula="Mi editor de prueba preferido es Nebula."
    @Before fun setup(){db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MemoryContextDatabase::class.java).build();memory=MemoryManager(db){_,_->null}}
    @After fun close(){db.close()}
    @Test fun irrelevant_memory_is_dropped()=runBlocking {memory.create(nebula,user);val s=memory.select("¿Qué base de datos utiliza este proyecto?",access);assertTrue(s.included.isEmpty());assertEquals(1,s.dropped.size)}
    @Test fun relevant_memory_is_included()=runBlocking {val m=memory.create(nebula,user);for(q in listOf("¿Cuál es mi editor de prueba preferido?","¿Qué editor prefiero?"))assertEquals(m.id,memory.search(q,access).single().record.id)}
    @Test fun small_talk_skips_memory()=runBlocking {var calls=0;val manager=MemoryManager(db){_,_->calls++;error("must not embed")};manager.create(nebula,user);for(q in listOf("hola","gracias","cómo estás?","¡Hola! Gracias.","buenos días","ok","adiós")){val s=manager.select(q,access);assertTrue(q,s.lookupSkipped);assertTrue(s.included.isEmpty())};assertEquals(0,calls)}
    @Test fun greeting_plus_real_query_does_not_skip_memory()=runBlocking {memory.create(nebula,user);val q="Hola, ¿cuál es mi editor de prueba preferido?";assertFalse(MemoryRelevance.smallTalk(q));assertEquals(1,memory.search(q,access).size);assertFalse(MemoryRelevance.smallTalk("Buenos días, ¿qué base de datos usa este proyecto?"))}
    @Test fun pinned_irrelevant_memory_is_dropped()=runBlocking {val m=memory.create(nebula,user);memory.pin(m.id);assertTrue(memory.search("hola",access).isEmpty());val s=memory.select("base de datos",access);assertTrue(s.included.isEmpty());assertEquals(2.0,s.dropped.single().relevance!!.pinBonus,0.0)}
    @Test fun important_irrelevant_memory_is_dropped()=runBlocking {val m=memory.create(nebula,user);db.dao().put(m.copy(importance=100.0));assertTrue(memory.search("hola",access).isEmpty());assertTrue(memory.select("base de datos",access).included.isEmpty())}
    @Test fun project_memory_scope_and_relevance()=runBlocking {memory.create(nebula,user);val a=memory.create("La base de datos de este proyecto es SQLite.",SemanticScope(ScopeType.PROJECT,"a"));val b=memory.create("La base de datos de este proyecto es PostgreSQL.",SemanticScope(ScopeType.PROJECT,"b"));for((id,wanted) in listOf("a" to a,"b" to b)){val s=memory.select("¿Qué base de datos utiliza este proyecto?",ScopeAccess(projectId=id));assertEquals(wanted.id,s.included.single().record.id);assertEquals(nebula,s.dropped.single().record.text)}}
    @Test fun user_memory_does_not_pollute_project_query()=runBlocking {memory.create(nebula,user);assertTrue(memory.search("Buenos días, ¿qué base de datos usa este proyecto?",ScopeAccess(projectId="a")).isEmpty())}
    @Test fun no_candidate_above_threshold_returns_empty()=runBlocking {memory.create(nebula,user);val s=memory.select("orbital mechanics",access);assertTrue(s.included.isEmpty());val c=ContextBuilder().build(ContextRequest("orbital mechanics"),emptyList());assertFalse(c.perSection.containsKey(ContextKind.MEMORY));assertFalse(c.conversation().userMessage.contains("CONTEXT DATA"))}
    @Test fun structured_exact_match_survives_gate()=runBlocking {val m=memory.create("Nebula",user,subject="editor",predicate="preferred",objectValue="Nebula");val s=memory.select("¿Qué editor prefiero?",access);assertEquals(m.id,s.included.single().record.id);assertTrue(s.included.single().relevance!!.structuredMatch)}
    @Test fun lexical_fallback_has_own_relevance_gate()=runBlocking {val m=memory.create(nebula,user);assertTrue(memory.select("editor astronomia planetas galaxias",access).included.isEmpty());assertEquals(m.id,memory.select("editor",access).included.single().record.id)}
    @Test fun many_memories_only_relevant_items_selected()=runBlocking {val m=memory.create(nebula,user);for(t in listOf("Mi lenguaje preferido es Kotlin","Prefiero reuniones cortas","Mi comida favorita es pasta","pantalla oscura","estilo sangria","ciudad Santiago","zona horaria Chile","formato PDF","musica jazz","deporte tenis","mascota gato","transporte tren","navegador Firefox","bebida cafe","vacaciones playa","presupuesto mensual","cumpleanos junio","trabajo remoto","telefono Motorola","base datos SQLite"))memory.create(t,user);assertEquals(21,memory.lookup(access).size);val s=memory.select("¿Cuál es mi editor preferido?",access);assertEquals(m.id,s.included.single().record.id);assertEquals(20,s.dropped.size)}
    @Test fun semantic_gate_is_independent_of_ranking_bonus()=runBlocking {val m=memory.create(nebula,user).copy(pinned=true,importance=99.0);assertFalse(MemoryRelevance.assess("base de datos",m,.619).accepted);assertTrue(MemoryRelevance.assess("different phrasing",m,.9).accepted)}
    @Test fun diagnostics_do_not_export_dropped_content()=runBlocking {val m=memory.create(nebula,user);val h=memory.select("base de datos",access).dropped.single();val item=ContextItem("MD1",ContextKind.MEMORY,m.text,user,ContextTrust.APPROVED_MEMORY,memoryRelevance=h.relevance);val bundle=ContextBuilder().build(ContextRequest("base de datos"),emptyList()).copy(dropped=listOf(DroppedContext(item,"BELOW_RELEVANCE_THRESHOLD")));val report=bundle.safeReport().toString();assertTrue(report.contains("BELOW_RELEVANCE_THRESHOLD"));assertFalse(report.contains("Nebula"));assertFalse(bundle.conversation().userMessage.contains("Nebula"))}
    @Test fun small_talk_foundation_avoids_embedding_gate_and_memory_budget()=runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val workspace=Room.inMemoryDatabaseBuilder(context,WorkspaceDatabase::class.java).build()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val graph=AppGraph(context,databaseOverride=workspace,applicationScope=scope)
        val foundation=graph.contextFoundation
        try {
            foundation.memory.create(nebula,user)
            graph.inferenceGate.lock()
            val bundle=withTimeout(5000){foundation.build(ContextRequest("hola"),evidence=emptyList())}
            assertTrue(bundle.memoryLookupSkipped)
            assertTrue(bundle.included.none{it.kind==ContextKind.MEMORY})
            assertFalse(bundle.perSection.containsKey(ContextKind.MEMORY))
            assertFalse(bundle.conversation().userMessage.contains("Nebula"))
        } finally { if(graph.inferenceGate.isLocked)graph.inferenceGate.unlock();scope.cancel();foundation.database.close();workspace.close() }
    }
    @Test fun semantic_nonfinite_never_passes_gate()=runBlocking {val m=memory.create(nebula,user);assertFalse(MemoryRelevance.assess("unrelated",m,Double.NaN).accepted)}
}
