package com.localai.workspace.semantic.v2

import android.app.Application
import android.net.Uri
import androidx.room.Room
import com.localai.workspace.AppGraph
import com.localai.workspace.context.ContextFoundation
import com.localai.workspace.context.ContextRequest
import com.localai.workspace.context.SourceRetrievalMode
import com.localai.workspace.context.ScopeAccess
import com.localai.workspace.data.*
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class SemanticContextPostfixTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var workspace:WorkspaceDatabase
    private lateinit var semanticDb:SemanticDatabase
    private lateinit var graph:AppGraph
    private lateinit var foundation:ContextFoundation
    private lateinit var layer:SemanticLayer
    private lateinit var manager:EmbeddingEngineManager
    private lateinit var driver:FakeDriver
    private lateinit var appScope:CoroutineScope
    private val temporaryFiles=mutableListOf<File>()
    private val prefsName="semantic-context-postfix-${UUID.randomUUID()}"
    private val modelFile=File(context.filesDir,"semantic-context-${UUID.randomUUID()}.litertlm")

    private class FakeDriver:EmbeddingNativeDriver {
        var initialized=false
        var calls=0
        var failNext=false
        var onCompute:(()->Unit)?=null
        override fun initialize(){initialized=true}
        override fun isInitialized()=initialized
        override fun compute(contents:List<InputData>,options:EmbeddingOptions):FloatArray {
            calls++
            if(failNext){failNext=false;error("controlled embedding failure")}
            onCompute?.also{onCompute=null;it.invoke()}
            return FloatArray(options.outputSize ?: 768){if(it==0)1f else 0f}
        }
        override fun close(){initialized=false}
    }

    @Before fun open()=runBlocking {
        context.getSharedPreferences("local_embedding_model",0).edit().clear().commit()
        appScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        workspace=Room.inMemoryDatabaseBuilder(context,WorkspaceDatabase::class.java).build()
        semanticDb=Room.inMemoryDatabaseBuilder(context,SemanticDatabase::class.java).build()
        graph=AppGraph(context,databaseOverride=workspace,applicationScope=appScope,nativeScope=appScope,validationId="semantic-postfix-${UUID.randomUUID()}")
        driver=FakeDriver()
        manager=EmbeddingEngineManager(context,graph.inferenceGate){driver}
        layer=SemanticLayer(context,workspace,graph.inferenceGate,semanticDb,prefsName,manager)
        foundation=ContextFoundation(graph){layer}
        modelFile.writeText("LITERTLM")
        val model=SemanticModelRecord("eg2-postfix","EmbeddingGemma 2 test","EMBEDDING_GEMMA_2",modelFile.name,modelFile.path,modelFile.length(),"a".repeat(64),"LITERTLM","litert-lm",768,"768,256","TEXT,CODE,IMAGE,AUDIO,VIDEO","TEXT","LiteRT-LM 0.17.1",1,"TEXT_PROBE_PASSED")
        semanticDb.dao().model(model)
        layer.activate(model.id)
    }

    @After fun close()=runBlocking {
        runCatching{manager.unload()}
        foundation.database.close()
        semanticDb.close()
        workspace.close()
        appScope.coroutineContext[Job]?.cancelAndJoin()
        modelFile.delete()
        temporaryFiles.forEach{it.delete()}
    }

    private suspend fun addProject(id:String) {
        val now=System.currentTimeMillis()
        workspace.projectDao().upsert(ProjectEntity(id,id,now,now))
    }

    private suspend fun addDocument(projectId:String,id:String,name:String,text:String):DocumentEntity {
        val path=File(context.cacheDir,"$id.txt").apply{writeText(text)}.also{temporaryFiles+=it}
        val now=System.currentTimeMillis()
        val doc=DocumentEntity(id,projectId,name,localPath=path.path,mimeType="text/plain",fileHash=fingerprint(text),byteSize=path.length(),extractionStatus="READY",indexingStatus="READY",importedAt=now,updatedAt=now)
        workspace.documentDao().insert(doc)
        workspace.documentDao().insertSegments(listOf(DocumentSegmentEntity(documentId=id,segmentIndex=0,text=text,normalizedText=text,contentHash=fingerprint(text))))
        return doc
    }

    private suspend fun index(vararg projects:String):Map<String,String> {
        val indexes=linkedMapOf<String,String>()
        for(project in projects)indexes[project]=layer.indexProject(project)
        return indexes
    }

    private fun request(project:String,selected:Set<String> = emptySet(),mode:SourceRetrievalMode=SourceRetrievalMode.SEMANTIC_V2_PREFERRED)=ContextRequest(
        "ORCHID",ScopeAccess(projectId=project),includeConversation=false,sourceRetrievalMode=mode,selectedDocumentIds=selected)

    @Test fun eg2ContextBuildRunsDespiteEmptyPrecomputedEvidenceAndReachesPrompt()=runBlocking {
        addProject("project-a")
        addDocument("project-a","doc-a","notes.txt","ORCHID is the semantic evidence passage for this project.")
        index("project-a")
        val before=driver.calls
        val bundle=foundation.build(request("project-a"),evidence=emptyList(),memoryEnabled=false)
        assertTrue("EG2 must embed the query even when legacy evidence is empty",driver.calls>before)
        assertTrue(bundle.included.any{it.kind==com.localai.workspace.context.ContextKind.SOURCE && it.text.contains("ORCHID")})
        assertTrue(bundle.conversation().userMessage.contains("ORCHID is the semantic evidence passage"))
        assertEquals(1,bundle.sourceEvidence.size)
        assertFalse(bundle.included.single{it.kind==com.localai.workspace.context.ContextKind.SOURCE}.provenance.excerpted)
    }

    @Test fun projectIsolationAndSelectedDocumentScopeArePreserved()=runBlocking {
        addProject("project-a");addProject("project-b")
        val selected=addDocument("project-a","selected","selected.txt","ORCHID selected document passage.")
        addDocument("project-a","other","other.txt","ORCHID other document passage.")
        addDocument("project-b","foreign","foreign.txt","ORCHID foreign project passage.")
        index("project-a","project-b")
        val selectedBundle=foundation.build(request("project-a",setOf(selected.id)),evidence=emptyList(),memoryEnabled=false)
        val sources=selectedBundle.included.filter{it.kind==com.localai.workspace.context.ContextKind.SOURCE}
        assertTrue(sources.isNotEmpty())
        assertTrue(sources.all{it.provenance.documentId==selected.id})
        assertFalse(selectedBundle.conversation().userMessage.contains("other document passage"))
        assertFalse(selectedBundle.conversation().userMessage.contains("foreign project passage"))
        val projectBundle=foundation.build(request("project-a"),evidence=emptyList(),memoryEnabled=false)
        assertTrue(projectBundle.included.filter{it.kind==com.localai.workspace.context.ContextKind.SOURCE}.all{it.scope.id=="project-a"})
        assertFalse(projectBundle.conversation().userMessage.contains("foreign project passage"))
    }

    @Test fun followUpCellQuestionSendsOnlyRelevantCellsWithCellProvenance()=runBlocking {
        addProject("project-a")
        val row1=org.json.JSONObject().put("sheet","Sales").put("row",1).put("cells",org.json.JSONArray()
            .put(org.json.JSONObject().put("column",1).put("address","A1").put("value","1")))
        val row2=org.json.JSONObject().put("sheet","Sales").put("row",2).put("cells",org.json.JSONArray()
            .put(org.json.JSONObject().put("column",1).put("address","A2").put("value","5")))
        val row3=org.json.JSONObject().put("sheet","Sales").put("row",3).put("cells",org.json.JSONArray()
            .put(org.json.JSONObject().put("column",1).put("address","A3").put("value","4").put("formula","A2-A1")))
        val row4=org.json.JSONObject().put("sheet","Sales").put("row",4).put("cells",org.json.JSONArray()
            .put(org.json.JSONObject().put("column",1).put("address","A4").put("value","whole unrelated workbook content")))
        val workbook=org.json.JSONObject().put("sheet","Sales").put("headerCandidate",org.json.JSONArray()
            .put(org.json.JSONObject().put("column",1).put("label","Amount"))).toString()+"\n"+
            listOf(row1,row2,row3,row4).joinToString("\n")
        val doc=addDocument("project-a","sales","sales.xlsx",workbook)
        index("project-a")

        val bundle=foundation.build(request("project-a",setOf(doc.id)).copy(query="¿Cuál es el valor de A3?"),evidence=emptyList(),memoryEnabled=false)
        val source=bundle.included.single{it.kind==com.localai.workspace.context.ContextKind.SOURCE}
        assertEquals(listOf("A3","A1","A2"),source.provenance.cellAddresses)
        assertTrue(bundle.conversation().userMessage.contains("A3=4"))
        assertTrue(bundle.conversation().userMessage.contains("formula=A2-A1"))
        assertFalse(bundle.conversation().userMessage.contains("whole unrelated workbook content"))
    }

    @Test fun eg2SpreadsheetRangeKeepsInteriorCellInPromptAndProvenance()=runBlocking {
        addProject("project-a")
        val workbook=(1..3).joinToString("\n") { row ->
            val value=listOf("1","5","4")[row-1]
            org.json.JSONObject().put("sheet","Hoja1").put("row",row).put("cells",org.json.JSONArray().put(
                org.json.JSONObject().put("address","A$row").put("column",1).put("value",value))).toString()
        }
        val document=addDocument("project-a","range-eg2","range.xlsx",workbook)
        index("project-a")

        val bundle=foundation.build(request("project-a",setOf(document.id)).copy(query="Suma A1:A3"),evidence=emptyList(),memoryEnabled=false)
        val source=bundle.included.single{it.kind==com.localai.workspace.context.ContextKind.SOURCE}
        val prompt=bundle.conversation().userMessage

        assertTrue(prompt.contains("A1=1"))
        assertTrue(prompt.contains("A2=5"))
        assertTrue(prompt.contains("A3=4"))
        assertEquals(listOf("A1","A2","A3"),source.provenance.cellAddresses)
        assertEquals(source.text,bundle.sourceEvidence.single().excerpt)
        assertTrue(bundle.sourceEvidenceIssues.isEmpty())
    }

    @Test fun lexicalFallbackSpreadsheetRangeKeepsInteriorCellInPromptAndProvenance()=runBlocking {
        addProject("project-a")
        val workbook=(1..3).joinToString("\n") { row ->
            val value=listOf("1","5","4")[row-1]
            org.json.JSONObject().put("sheet","Hoja1").put("row",row).put("cells",org.json.JSONArray().put(
                org.json.JSONObject().put("address","A$row").put("column",1).put("value",value))).toString()
        }
        val document=addDocument("project-a","range-legacy","range.xlsx",workbook)
        index("project-a")
        driver.failNext=true

        val bundle=foundation.build(request("project-a",setOf(document.id)).copy(query="Suma A1:A3"),evidence=emptyList(),memoryEnabled=false)
        val source=bundle.included.single{it.kind==com.localai.workspace.context.ContextKind.SOURCE}
        val prompt=bundle.conversation().userMessage

        assertTrue(bundle.notice.orEmpty().contains("SOURCE_SEMANTIC_V2_SKIPPED"))
        assertTrue(prompt.contains("A1=1"))
        assertTrue(prompt.contains("A2=5"))
        assertTrue(prompt.contains("A3=4"))
        assertEquals(listOf("A1","A2","A3"),source.provenance.cellAddresses)
        assertEquals(source.text,bundle.sourceEvidence.single().excerpt)
        assertTrue(bundle.sourceEvidenceIssues.isEmpty())
    }

    @Test fun eg2MissingSpreadsheetDependencyReachesPromptWithMaterialDiagnostic()=runBlocking {
        addProject("project-a")
        val workbook=listOf(
            org.json.JSONObject().put("sheet","Hoja1").put("row",1).put("cells",org.json.JSONArray().put(org.json.JSONObject().put("address","A1").put("column",1).put("value","1"))).toString(),
            org.json.JSONObject().put("sheet","Hoja1").put("row",3).put("cells",org.json.JSONArray().put(org.json.JSONObject().put("address","A3").put("column",1).put("value","4").put("formula","\$A\$2-\$A\$1"))).toString(),
            org.json.JSONObject().put("sheet","Hoja1").put("row",4).put("cells",org.json.JSONArray().put(org.json.JSONObject().put("address","A4").put("column",1).put("value","unrelated"))).toString(),
        ).joinToString("\n")
        val document=addDocument("project-a","missing-dependency","formula.xlsx",workbook)
        index("project-a")

        val bundle=foundation.build(request("project-a",setOf(document.id)).copy(query="Explica A3"),evidence=emptyList(),memoryEnabled=false)
        val source=bundle.included.single{it.kind==com.localai.workspace.context.ContextKind.SOURCE}
        val report=bundle.safeReport()

        assertTrue(bundle.conversation().userMessage.contains("A3=4"))
        assertFalse(bundle.conversation().userMessage.contains("A4=unrelated"))
        assertEquals(listOf("A3","A1"),source.provenance.cellAddresses)
        assertEquals(listOf("MISSING_REFERENCED_CELLS"),source.provenance.evidenceIncompleteReasons)
        assertEquals(listOf("A2"),source.provenance.missingCellAddresses)
        assertTrue(bundle.requiresUserNotice)
        assertTrue(report.toString().contains("MISSING_REFERENCED_CELLS"))
        assertTrue(report.toString().contains("A2"))
    }

    @Test fun eg2QualifiedCrossSheetDependencyKeepsCompositeIdentityInFinalPromptAndInspector()=runBlocking {
        addProject("project-a")
        val workbook=listOf(
            spreadsheetRow("Hoja1",0,"A1","999"),
            spreadsheetRow("Hoja1",0,"A3","4","'Hoja2'!A1"),
            spreadsheetRow("Hoja2",1,"A1","5"),
        ).joinToString("\n")
        val document=addDocument("project-a","cross-sheet-eg2","cross-sheet.xlsx",workbook)
        index("project-a")

        val bundle=foundation.build(request("project-a",setOf(document.id)).copy(query="Explica A3"),evidence=emptyList(),memoryEnabled=false)
        val source=bundle.included.single{it.kind==com.localai.workspace.context.ContextKind.SOURCE}
        val prompt=bundle.conversation().userMessage

        assertEquals(listOf("Hoja1!A3","Hoja2!A1"),source.provenance.cellReferences)
        assertTrue(prompt.contains("Hoja2!A1=5"))
        assertFalse(prompt.contains("Hoja1!A1=999"))
        assertTrue(prompt.contains("cell-references=\"Hoja1!A3,Hoja2!A1\""))
        assertTrue(bundle.sourceEvidenceIssues.isEmpty())
    }

    @Test fun selectedDocumentDoesNotResolveQualifiedDependencyFromAnotherDocument()=runBlocking {
        addProject("project-a")
        val selected=addDocument("project-a","formula-only","formula.xlsx",listOf(
            spreadsheetRow("Hoja1",0,"A1","999"),
            spreadsheetRow("Hoja1",0,"A3","4","'Hoja2'!A1"),
        ).joinToString("\n"))
        addDocument("project-a","other-sheet","other.xlsx",spreadsheetRow("Hoja2",0,"A1","777"))
        index("project-a")

        val bundle=foundation.build(request("project-a",setOf(selected.id)).copy(query="Explica A3"),evidence=emptyList(),memoryEnabled=false)
        val source=bundle.included.single{it.kind==com.localai.workspace.context.ContextKind.SOURCE}

        assertEquals(listOf("Hoja1!A3"),source.provenance.cellReferences)
        assertEquals(listOf("Hoja2!A1"),source.provenance.missingCellReferences)
        assertTrue(source.provenance.evidenceIncompleteReasons.contains("SHEET_NOT_FOUND"))
        assertTrue(bundle.requiresUserNotice)
        assertFalse(bundle.conversation().userMessage.contains("777"))
    }

    @Test fun eg2AmbiguousUnqualifiedCellProducesEmptyEvidenceAndMaterialDiagnostic()=runBlocking {
        addProject("project-a")
        val workbook=listOf(
            spreadsheetRow("Hoja1",0,"A1","one"),
            spreadsheetRow("Hoja2",1,"A1","two"),
        ).joinToString("\n")
        val document=addDocument("project-a","ambiguous-sheets","ambiguous.xlsx",workbook)
        index("project-a")

        val bundle=foundation.build(request("project-a",setOf(document.id)).copy(query="Explica A1"),evidence=emptyList(),memoryEnabled=false)
        val prompt=bundle.conversation().userMessage
        val issue=bundle.sourceEvidenceIssues.single()

        assertTrue(bundle.requiresUserNotice)
        assertTrue(issue.reasons.contains("AMBIGUOUS_UNQUALIFIED_REFERENCE"))
        assertEquals(listOf("Hoja1!A1","Hoja2!A1"),issue.ambiguousCellReferences)
        assertTrue(bundle.safeReport().toString().contains("AMBIGUOUS_UNQUALIFIED_REFERENCE"))
        assertFalse(prompt.contains("Hoja1!A1=one"))
        assertFalse(prompt.contains("Hoja2!A1=two"))
    }

    @Test fun lexicalFallbackKeepsQualifiedCrossSheetReferencesAndMaterialNotice()=runBlocking {
        addProject("project-a")
        val workbook=listOf(
            spreadsheetRow("Hoja1",0,"A1","999"),
            spreadsheetRow("Hoja1",0,"A3","4","'Hoja2'!A1"),
            spreadsheetRow("Hoja2",1,"A1","5"),
        ).joinToString("\n")
        val document=addDocument("project-a","cross-sheet-lexical","cross-sheet.xlsx",workbook)
        index("project-a")
        val lexical=graph.retrieval.retrieve("project-a","Explica A3",documentIds=setOf(document.id))
        assertTrue("the fixture must reach the production lexical retrieval path",lexical.isNotEmpty())
        driver.failNext=true

        val bundle=foundation.build(request("project-a",setOf(document.id)).copy(query="Explica A3"),evidence=lexical,memoryEnabled=false)
        val source=bundle.included.single{it.kind==com.localai.workspace.context.ContextKind.SOURCE}

        assertTrue(bundle.notice.orEmpty().contains("SOURCE_SEMANTIC_V2_SKIPPED"))
        assertTrue(source.provenance.cellReferences.contains("Hoja2!A1"))
        assertTrue(bundle.conversation().userMessage.contains("Hoja2!A1=5"))
        assertFalse(bundle.conversation().userMessage.contains("Hoja1!A1=999"))
        assertTrue(bundle.sourceEvidenceIssues.isEmpty())
    }

    @Test fun lexicalFallbackReportsMissingQualifiedSheetInsteadOfUsingAnotherDocument()=runBlocking {
        addProject("project-a")
        val selected=addDocument("project-a","formula-only-lexical","formula-only.xlsx",listOf(
            spreadsheetRow("Hoja1",0,"A1","999"),
            spreadsheetRow("Hoja1",0,"A3","4","'Hoja2'!A1"),
        ).joinToString("\n"))
        addDocument("project-a","other-sheet-lexical","other.xlsx",spreadsheetRow("Hoja2",0,"A1","777"))
        index("project-a")
        val lexical=graph.retrieval.retrieve("project-a","Explica A3",documentIds=setOf(selected.id))
        assertTrue("the fixture must use the production lexical retrieval path",lexical.isNotEmpty())
        driver.failNext=true

        val bundle=foundation.build(request("project-a",setOf(selected.id)).copy(query="Explica A3"),evidence=lexical,memoryEnabled=false)
        val source=bundle.included.single{it.kind==com.localai.workspace.context.ContextKind.SOURCE}
        val issue=bundle.sourceEvidenceIssues.single()

        assertTrue(bundle.notice.orEmpty().contains("SOURCE_SEMANTIC_V2_SKIPPED"))
        assertEquals(listOf("Hoja1!A3"),source.provenance.cellReferences)
        assertEquals(listOf("Hoja2!A1"),source.provenance.missingCellReferences)
        assertTrue(issue.reasons.contains("SHEET_NOT_FOUND"))
        assertTrue(bundle.requiresUserNotice)
        assertFalse(bundle.conversation().userMessage.contains("Hoja1!A1=999"))
        assertFalse(bundle.conversation().userMessage.contains("777"))
    }

    @Test fun lexicalFallbackSurfacesAmbiguousSheetValuesWithoutInjectingEitherCandidate()=runBlocking {
        addProject("project-a")
        val selected=addDocument("project-a","ambiguous-lexical","ambiguous-lexical.xlsx",listOf(
            spreadsheetRow("Hoja1",0,"A1","one"),
            spreadsheetRow("Hoja2",1,"A1","two"),
        ).joinToString("\n"))
        index("project-a")
        val lexical=graph.retrieval.retrieve("project-a","Explica A1",documentIds=setOf(selected.id))
        assertTrue("the fixture must use the production lexical retrieval path",lexical.isNotEmpty())
        driver.failNext=true

        val bundle=foundation.build(request("project-a",setOf(selected.id)).copy(query="Explica A1"),evidence=lexical,memoryEnabled=false)
        val prompt=bundle.conversation().userMessage
        val issue=bundle.sourceEvidenceIssues.single()

        assertTrue(bundle.notice.orEmpty().contains("SOURCE_SEMANTIC_V2_SKIPPED"))
        assertTrue(bundle.requiresUserNotice)
        assertTrue(issue.reasons.contains("AMBIGUOUS_UNQUALIFIED_REFERENCE"))
        assertEquals(listOf("Hoja1!A1","Hoja2!A1"),issue.ambiguousCellReferences)
        assertFalse(prompt.contains("Hoja1!A1=one"))
        assertFalse(prompt.contains("Hoja2!A1=two"))
    }

    private fun spreadsheetRow(sheet:String,order:Int,address:String,value:String,formula:String?=null):String {
        val row=address.dropWhile{it.isLetter()}.toInt()
        val column=address.takeWhile{it.isLetter()}.fold(0){acc,char->acc*26+char.uppercaseChar().code-64}
        val cell=org.json.JSONObject().put("address",address).put("column",column).put("value",value).apply{formula?.let{put("formula",it)}}
        return org.json.JSONObject().put("sheet",sheet).put("sheetOrder",order).put("row",row).put("cells",org.json.JSONArray().put(cell)).toString()
    }

    @Test fun equivalentLegacyAndV2EvidenceIsIncludedOnlyOnce()=runBlocking {
        addProject("project-a")
        val document=addDocument("project-a","doc-a","notes.txt","ORCHID is one canonical passage, not two copies.")
        index("project-a")
        val legacy=graph.retrieval.retrieve("project-a","ORCHID",documentIds=setOf(document.id))
        assertEquals(1,legacy.size)
        val bundle=foundation.build(request("project-a",setOf(document.id)),legacy,memoryEnabled=false)
        val sources=bundle.included.filter{it.kind==com.localai.workspace.context.ContextKind.SOURCE}
        assertEquals(1,sources.size)
        assertEquals(1,bundle.sourceEvidence.size)
        assertEquals(legacy.single().id,bundle.sourceEvidence.single().id)
        assertEquals(1,"ORCHID is one canonical passage".toRegex().findAll(bundle.conversation().userMessage).count())
    }

    @Test fun v2FailureFallsBackToPrecomputedLegacyEvidence()=runBlocking {
        addProject("project-a")
        val document=addDocument("project-a","doc-a","notes.txt","ORCHID fallback remains available from the lexical index.")
        index("project-a")
        val legacy=graph.retrieval.retrieve("project-a","ORCHID",documentIds=setOf(document.id))
        assertEquals(1,legacy.size)
        driver.failNext=true
        val bundle=foundation.build(request("project-a",setOf(document.id)),legacy,memoryEnabled=false)
        assertTrue(bundle.notice.orEmpty().contains("SOURCE_SEMANTIC_V2_SKIPPED"))
        assertTrue(bundle.included.any{it.kind==com.localai.workspace.context.ContextKind.SOURCE && it.text==legacy.single().excerpt})
        assertEquals(legacy.single().id,bundle.sourceEvidence.single().id)
    }

    @Test fun v2FailureWithEmptyLegacyEvidenceRunsProductionLexicalFallback()=runBlocking {
        addProject("project-a")
        addDocument("project-a","doc-a","notes.txt","ORCHID lexical fallback must run when precomputed evidence is empty.")
        index("project-a")
        driver.failNext=true
        val bundle=foundation.build(request("project-a"),evidence=emptyList(),memoryEnabled=false)
        assertTrue(bundle.notice.orEmpty().contains("SOURCE_SEMANTIC_V2_SKIPPED"))
        assertTrue(bundle.included.any{it.kind==com.localai.workspace.context.ContextKind.SOURCE && it.text.contains("lexical fallback")})
        assertEquals(1,bundle.sourceEvidence.size)
    }

    @Test fun retrievalFinishesBeforeTheGenerationGateCanBeAcquired()=runBlocking {
        addProject("project-a")
        addDocument("project-a","doc-a","notes.txt","ORCHID preparation must happen outside the generation lease.")
        index("project-a")
        val before=driver.calls
        driver.onCompute={assertFalse("embedding must finish before Gemma generation gate",graph.inferenceGate.isLocked)}
        foundation.build(request("project-a"),evidence=emptyList(),memoryEnabled=false)
        assertTrue(driver.calls>before)
        assertFalse("the generation lease is not retained by context preparation",graph.inferenceGate.isLocked)
        graph.inferenceGate.lock()
        try { assertTrue(graph.inferenceGate.isLocked) } finally { graph.inferenceGate.unlock() }
    }

    @Test fun explicitLegacyAndDisabledRetrievalPoliciesRemainAvailable()=runBlocking {
        addProject("project-a")
        val document=addDocument("project-a","doc-a","notes.txt","ORCHID legacy policy passage.")
        index("project-a")
        val before=driver.calls
        val legacy=graph.retrieval.retrieve("project-a","ORCHID",documentIds=setOf(document.id))
        val legacyBundle=foundation.build(request("project-a",setOf(document.id),SourceRetrievalMode.LEGACY_ONLY),legacy,memoryEnabled=false)
        assertEquals(before,driver.calls)
        assertEquals(legacy.single().excerpt,legacyBundle.included.single{it.kind==com.localai.workspace.context.ContextKind.SOURCE}.text)
        val disabled=foundation.build(request("project-a",setOf(document.id),SourceRetrievalMode.DISABLED),legacy,memoryEnabled=false)
        assertFalse(disabled.included.any{it.kind==com.localai.workspace.context.ContextKind.SOURCE})
    }

    @Test fun staleGenerationIsRejectedAndLexicalFallbackRemainsUsable()=runBlocking {
        addProject("project-a")
        addDocument("project-a","doc-a","notes.txt","ORCHID remains available from the current lexical document index.")
        index("project-a")
        val active=semanticDb.dao().active(SemanticScope(ScopeType.PROJECT,"project-a").key)!!
        layer.markNeedsReindex("project-a")
        val before=driver.calls
        try { layer.search(SemanticInput.Text("ORCHID"),SemanticScope(ScopeType.PROJECT,"project-a"));fail("stale vectors must not be used") }
        catch(expected:SemanticFailure){assertEquals(SemanticError.REINDEX_REQUIRED,expected.code)}
        assertEquals("stale state is checked before query embedding",before,driver.calls)
        val bundle=foundation.build(request("project-a"),evidence=null,memoryEnabled=false)
        assertTrue(bundle.notice.orEmpty().contains("REINDEX_REQUIRED"))
        assertTrue(bundle.included.any{it.kind==com.localai.workspace.context.ContextKind.SOURCE && it.text.contains("current lexical document index")})
        assertEquals("NEEDS_REINDEX",semanticDb.dao().job(active.indexId)?.state)
    }

    @Test fun indexingFailedAndCancelledGenerationsAreNeverSearchable()=runBlocking {
        addProject("project-a");addDocument("project-a","doc-a","notes.txt","ORCHID generation-state fixture.");index("project-a")
        val scope=SemanticScope(ScopeType.PROJECT,"project-a");val active=semanticDb.dao().active(scope.key)!!
        for(state in listOf("INDEXING","FAILED","CANCELLED")) {
            val ready=semanticDb.dao().job(active.indexId)!!
            semanticDb.dao().job(ready.copy(state=state,updatedAt=ready.updatedAt+1))
            val before=driver.calls
            try { layer.search(SemanticInput.Text("ORCHID"),scope);fail("$state generation must be rejected") }
            catch(expected:SemanticFailure){assertEquals(SemanticError.REINDEX_REQUIRED,expected.code)}
            assertEquals(before,driver.calls)
        }
    }

    @Test fun generationInvalidatedDuringQueryIsRejectedAfterEmbedding()=runBlocking {
        addProject("project-a");addDocument("project-a","doc-a","notes.txt","ORCHID must not escape a generation invalidated mid-query.");index("project-a")
        driver.onCompute={runBlocking{layer.markNeedsReindex("project-a")}}
        try { layer.search(SemanticInput.Text("ORCHID"),SemanticScope(ScopeType.PROJECT,"project-a"));fail("a generation invalidated during embedding must be rejected") }
        catch(expected:SemanticFailure){assertEquals(SemanticError.REINDEX_REQUIRED,expected.code)}
        assertEquals("NEEDS_REINDEX",semanticDb.dao().job(semanticDb.dao().active(SemanticScope(ScopeType.PROJECT,"project-a").key)!!.indexId)?.state)
    }

    @Test fun reindexPublishesReadyGenerationAndPreservesReadyRollback()=runBlocking {
        addProject("project-a");addDocument("project-a","doc-a","notes.txt","ORCHID original green-blue passage.")
        val oldId=index("project-a").getValue("project-a");layer.setDimension(256)
        val greenId=layer.indexProject("project-a")
        assertEquals(greenId,semanticDb.dao().active(SemanticScope(ScopeType.PROJECT,"project-a").key)?.indexId)
        assertTrue(layer.search(SemanticInput.Text("ORCHID"),SemanticScope(ScopeType.PROJECT,"project-a"),setOf(SemanticModality.TEXT)).isNotEmpty())
        layer.setDimension(768);layer.switchIndex(oldId)
        assertTrue("an older still-READY generation remains a rollback target",layer.search(SemanticInput.Text("ORCHID"),SemanticScope(ScopeType.PROJECT,"project-a"),setOf(SemanticModality.TEXT)).isNotEmpty())
        layer.markNeedsReindex("project-a")
        try { layer.switchIndex(oldId);fail("a stale generation is not a valid rollback target") }
        catch(expected:IllegalArgumentException){}
    }

    @Test fun changedDocumentCanBeReindexedIntoNewReadyGeneration()=runBlocking {
        addProject("project-a");val document=addDocument("project-a","doc-a","notes.txt","ORCHID original content.")
        val oldId=index("project-a").getValue("project-a")
        workspace.documentDao().deleteSegments(document.id)
        workspace.documentDao().insertSegments(listOf(DocumentSegmentEntity(documentId=document.id,segmentIndex=0,text="ORCHID updated content.",normalizedText="ORCHID updated content.",contentHash=fingerprint("ORCHID updated content."))))
        layer.markNeedsReindex("project-a")
        try { layer.search(SemanticInput.Text("ORCHID"),SemanticScope(ScopeType.PROJECT,"project-a"));fail("updated source must stale the active generation") }
        catch(expected:SemanticFailure){assertEquals(SemanticError.REINDEX_REQUIRED,expected.code)}
        val fresh=layer.indexProject("project-a")
        assertNotEquals(oldId,fresh)
        assertEquals("READY",semanticDb.dao().job(fresh)?.state)
        assertTrue(layer.search(SemanticInput.Text("ORCHID"),SemanticScope(ScopeType.PROJECT,"project-a"),setOf(SemanticModality.TEXT)).any{it.content=="ORCHID updated content."})
    }

    @Test fun realDocumentIngestionAwaitsStaleGenerationMark()=runBlocking {
        context.deleteDatabase("semantic_v2.db")
        val persisted=SemanticDatabase.create(context)
        val scope=SemanticScope(ScopeType.PROJECT,"ingested-project")
        val job=SemanticIndexJob("generation-before-import",scope.key,"space","READY",1,1,1,1,1,null)
        persisted.dao().job(job);persisted.dao().activate(SemanticActiveIndex(scope.key,job.id,job.spaceKey));persisted.close()

        val ws=Room.inMemoryDatabaseBuilder(context,WorkspaceDatabase::class.java).build()
        val id="ingested-project";val now=System.currentTimeMillis();ws.projectDao().upsert(ProjectEntity(id,id,now,now))
        val token="ingest-${UUID.randomUUID()}";val localScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val app=AppGraph(context,databaseOverride=ws,applicationScope=localScope,nativeScope=localScope,validationId=token)
        val uri=Uri.parse("content://local.fixture/postfix.txt")
        shadowOf(context.contentResolver).registerInputStream(uri,ByteArrayInputStream("new imported document content".toByteArray()))
        try {
            assertTrue(app.documents.ingest(id,uri).isSuccess)
            assertEquals("NEEDS_REINDEX",app.semanticV2.database.dao().job(job.id)?.state)
        } finally {
            app.semanticV2.database.close();ws.close();localScope.coroutineContext[Job]?.cancelAndJoin()
            File(context.filesDir,"documents/self-test-$token").deleteRecursively()
            context.deleteDatabase("semantic_v2.db")
        }
    }
}
