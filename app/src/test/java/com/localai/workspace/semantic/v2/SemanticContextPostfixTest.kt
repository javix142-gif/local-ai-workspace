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
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executor
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    private val semanticQueries=Collections.synchronizedList(mutableListOf<Pair<String,List<Any?>>>() )
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
        semanticDb=Room.inMemoryDatabaseBuilder(context,SemanticDatabase::class.java)
            .setQueryCallback({ sql,args -> semanticQueries+=sql to args.toList() },Executor { it.run() })
            .build()
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

    private suspend fun addDocumentWithSegments(projectId:String,id:String,name:String,texts:List<String>):DocumentEntity {
        val path=File(context.cacheDir,"$id.txt").apply{writeText(texts.joinToString("\n"))}.also{temporaryFiles+=it}
        val now=System.currentTimeMillis()
        val document=DocumentEntity(id,projectId,name,localPath=path.path,mimeType="text/plain",fileHash=fingerprint(path.readText()),byteSize=path.length(),extractionStatus="READY",indexingStatus="READY",importedAt=now,updatedAt=now)
        workspace.documentDao().insert(document)
        workspace.documentDao().insertSegments(texts.mapIndexed { index,text ->
            DocumentSegmentEntity(documentId=id,segmentIndex=index,text=text,normalizedText=text.lowercase(),contentHash=fingerprint(text))
        })
        return document
    }

    private suspend fun seedReadySemanticIndex(projectId:String,indexId:String,documents:List<DocumentEntity>) {
        val dao=semanticDb.dao()
        val scope=SemanticScope(ScopeType.PROJECT,projectId)
        val model=manager.selected ?: error("fixture EG2 model must be selected")
        val dimension=layer.dimension
        val space=EmbeddingEngineManager.space(model,dimension)
        val vector=SemanticVectors.encode(FloatArray(dimension){if(it==0)1f else 0f})
        var count=0
        for(document in documents) {
            val sourceId="$indexId:${document.id}"
            dao.source(SemanticSourceRecord(sourceId,scope.key,"DOCUMENT",document.displayName,document.mimeType,document.localPath,document.byteSize,document.updatedAt,document.fileHash))
            for(segment in workspace.documentDao().segmentsForDocument(document.id)) {
                val segmentId="$sourceId:${segment.segmentIndex.toString().padStart(4,'0')}"
                val taskContent=TaskPromptProfile.format(EmbeddingTask.DOCUMENT,segment.text,document.displayName)
                val contentHash=fingerprint(taskContent)
                dao.segment(SemanticSegmentRecord(segmentId,sourceId,"TEXT",segment.text,document.displayName,null,null,null,null,null,contentHash,legacySegmentId=segment.id))
                dao.embedding(SemanticEmbeddingRecord(indexId,segmentId,space.id,model.provider,model.id,model.sha256,dimension,true,"DOCUMENT",contentHash,SemanticVectors.FORMAT,vector,1))
                count++
            }
        }
        dao.job(SemanticIndexJob(indexId,scope.key,space.id,"READY",documents.size,count,count,1,1,null))
        dao.activate(SemanticActiveIndex(scope.key,indexId,space.id))
    }

    private suspend fun addCodeArchive(projectId:String,id:String,entries:Map<String,String>):DocumentEntity {
        val archive=File(context.cacheDir,"$id.zip").also{temporaryFiles+=it}
        ZipOutputStream(archive.outputStream()).use { output -> entries.forEach { (name,text) ->
            output.putNextEntry(ZipEntry(name));output.write(text.toByteArray(Charsets.UTF_8));output.closeEntry()
        } }
        val parsed=DocumentParser(context).parse(archive,"application/zip",archive.name)
        val chunks=TextChunker().chunk(parsed)
        val now=System.currentTimeMillis()
        val document=DocumentEntity(id,projectId,archive.name,localPath=archive.path,mimeType="application/zip",fileHash=fingerprint("archive:$id"),byteSize=archive.length(),pageCount=parsed.pages.size,extractionStatus="READY",indexingStatus="READY",importedAt=now,updatedAt=now)
        workspace.documentDao().insert(document)
        workspace.documentDao().insertSegments(chunks.mapIndexed { index,chunk ->
            DocumentSegmentEntity(documentId=id,segmentIndex=index,pageStart=chunk.pageStart,pageEnd=chunk.pageEnd,charStart=chunk.charStart,charEnd=chunk.charEnd,
                text=chunk.text,normalizedText=chunk.text.lowercase(),sectionPath=chunk.sectionPath,contentHash=fingerprint(chunk.text))
        })
        return document
    }

    private suspend fun addPagedDocument(projectId:String,id:String):DocumentEntity {
        val first="ORCHID marker from page one"
        val second="ORCHID marker from page two"
        val document=addDocument(projectId,id,"report.pdf","$first\n$second")
        workspace.documentDao().deleteSegments(document.id)
        workspace.documentDao().insertSegments(listOf(first,second).mapIndexed { index,text ->
            DocumentSegmentEntity(documentId=document.id,segmentIndex=index,pageStart=index+1,pageEnd=index+1,
                charStart=0,charEnd=text.length,text=text,normalizedText=text.lowercase(),contentHash=fingerprint("${index+1}:$text"))
        })
        return document
    }

    private suspend fun index(vararg projects:String):Map<String,String> {
        val indexes=linkedMapOf<String,String>()
        for(project in projects)indexes[project]=layer.indexProject(project)
        return indexes
    }

    private fun request(project:String,selected:Set<String> = emptySet(),mode:SourceRetrievalMode=SourceRetrievalMode.SEMANTIC_V2_PREFERRED)=ContextRequest(
        "ORCHID",ScopeAccess(projectId=project),includeConversation=false,sourceRetrievalMode=mode,selectedDocumentIds=selected)

    @Test fun repeatedContextQueryReusesEmbeddingButRejectsStaleIndexAndRechecksSelection()=runBlocking {
        addProject("cache-a");addProject("cache-b")
        addDocument("cache-a","a-one","one.txt","ORCHID first passage")
        addDocument("cache-a","a-two","two.txt","ORCHID second passage")
        addDocument("cache-b","b-one","other.txt","ORCHID other project")
        index("cache-a","cache-b")
        val before=driver.calls
        val first=foundation.build(request("cache-a",setOf("a-one")),memoryEnabled=false)
        val second=foundation.build(request("cache-a",setOf("a-two")),memoryEnabled=false)
        assertEquals(before+1,driver.calls)
        assertTrue(layer.queryEmbeddingReused.value)
        assertEquals(setOf("a-one"),first.sourceEvidence.map{it.documentId}.toSet())
        assertEquals(setOf("a-two"),second.sourceEvidence.map{it.documentId}.toSet())
        foundation.build(request("cache-b"),memoryEnabled=false)
        assertEquals(before+2,driver.calls)
        layer.markNeedsReindex("cache-a")
        val stale=foundation.build(request("cache-a",setOf("a-one")),memoryEnabled=false)
        assertFalse(layer.queryEmbeddingReused.value)
        assertTrue(stale.notice.orEmpty().contains("REINDEX_REQUIRED"))
        assertEquals(before+2,driver.calls)
        index("cache-a")
        val indexed=driver.calls
        foundation.build(request("cache-a"),memoryEnabled=false)
        assertEquals(indexed+1,driver.calls)
    }

    @Test fun configurationChangeDuringQueryMustNotPublishOrCacheOldDescriptor()=runBlocking {
        addProject("config-race");addDocument("config-race","config-doc","notes.txt","ORCHID configuration race fixture")
        index("config-race")
        driver.onCompute={layer.configurationRevision.value=layer.configurationRevision.value+1}
        try {
            layer.search(SemanticInput.Text("ORCHID"),SemanticScope(ScopeType.PROJECT,"config-race"))
            fail("configuration changed while the query was embedding")
        }catch(e:SemanticFailure){assertEquals(SemanticError.SPACE_MISMATCH,e.code)}
        val before=driver.calls
        layer.search(SemanticInput.Text("ORCHID"),SemanticScope(ScopeType.PROJECT,"config-race"))
        assertEquals(before+1,driver.calls)
        assertFalse(layer.queryEmbeddingReused.value)
    }

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

    @Test fun eg2ExactPageQueryIncludesOnlyRequestedPageAndAbstainsWhenPageIsMissing()=runBlocking {
        addProject("page-eg2")
        val document=addPagedDocument("page-eg2","page-eg2-doc")
        index("page-eg2")

        val requested=foundation.build(
            request("page-eg2",setOf(document.id)).copy(query="Busca ORCHID en la página 2"),
            evidence=emptyList(),memoryEnabled=false,
        )
        val requestedSources=requested.included.filter{it.kind==com.localai.workspace.context.ContextKind.SOURCE}
        assertTrue("EG2 exact-page query must not include page 1",requestedSources.isNotEmpty() && requestedSources.all{it.provenance.page==2})
        assertTrue(requested.conversation().userMessage.contains("page two"))
        assertFalse(requested.conversation().userMessage.contains("page one"))
        assertEquals(listOf(2),requested.sourceEvidence.mapNotNull{it.pageStart}.distinct())

        val absent=foundation.build(
            request("page-eg2",setOf(document.id)).copy(query="Busca ORCHID en la página 9"),
            evidence=emptyList(),memoryEnabled=false,
        )
        assertTrue("a missing exact page must abstain instead of citing another page",absent.included.none{it.kind==com.localai.workspace.context.ContextKind.SOURCE})
        assertTrue(absent.sourceEvidence.isEmpty())
    }

    @Test fun lexicalExactPageQueryIncludesOnlyRequestedPageAndAbstainsWhenPageIsMissing()=runBlocking {
        addProject("page-legacy")
        val document=addPagedDocument("page-legacy","page-legacy-doc")
        val precomputedWrongPage=graph.retrieval.retrieve("page-legacy","ORCHID",documentIds=setOf(document.id))
            .filter{it.pageStart==1}
        assertTrue("fixture must supply stale precomputed evidence from page 1",precomputedWrongPage.isNotEmpty())

        val requested=foundation.build(
            request("page-legacy",setOf(document.id),SourceRetrievalMode.LEGACY_ONLY)
                .copy(query="Busca ORCHID en la página 2"),
            evidence=precomputedWrongPage,memoryEnabled=false,
        )
        val requestedSources=requested.included.filter{it.kind==com.localai.workspace.context.ContextKind.SOURCE}
        assertTrue("legacy exact-page query must not include page 1",requestedSources.isNotEmpty() && requestedSources.all{it.provenance.page==2})
        assertTrue(requested.conversation().userMessage.contains("page two"))
        assertFalse(requested.conversation().userMessage.contains("page one"))
        assertEquals(listOf(2),requested.sourceEvidence.mapNotNull{it.pageStart}.distinct())

        val absent=foundation.build(
            request("page-legacy",setOf(document.id),SourceRetrievalMode.LEGACY_ONLY)
                .copy(query="Busca ORCHID en la page 9"),
            evidence=precomputedWrongPage,memoryEnabled=false,
        )
        assertTrue("a missing exact page must abstain instead of citing another page",absent.included.none{it.kind==com.localai.workspace.context.ContextKind.SOURCE})
        assertTrue(absent.sourceEvidence.isEmpty())
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

    @Test fun searchReadsSelectedSemanticCorpusInBoundedPages()=runBlocking {
        addProject("paged-search")
        val unselectedTexts=(0 until 256).map { "Unselected passage $it." }
        val unselected=addDocumentWithSegments("paged-search","a-unselected","a-unselected.txt",unselectedTexts)
        val selectedText="Selected evidence contains BOUNDARYTOKEN."
        val selected=addDocumentWithSegments("paged-search","z-selected","z-selected.txt",listOf(selectedText))
        seedReadySemanticIndex("paged-search","paged-search-index",listOf(unselected,selected))

        semanticQueries.clear()
        val embeddingCalls=driver.calls
        val modalityMiss=layer.search(
            SemanticInput.Text("BOUNDARYTOKEN"),SemanticScope(ScopeType.PROJECT,"paged-search"),
            setOf(SemanticModality.IMAGE),limit=100,documentIds=setOf(selected.id),
        )
        assertTrue(modalityMiss.isEmpty())
        assertEquals("No query embedding when every selected row has another modality",embeddingCalls,driver.calls)

        semanticQueries.clear()
        val hits=layer.search(
            SemanticInput.Text("BOUNDARYTOKEN"),SemanticScope(ScopeType.PROJECT,"paged-search"),
            setOf(SemanticModality.TEXT),limit=100,documentIds=setOf(selected.id),
        )
        assertTrue("selected lexical evidence beyond the first corpus page remains searchable",hits.any{it.content==selectedText})
        assertEquals("only the query is embedded after the already seeded index",embeddingCalls+1,driver.calls)

        val corpusReads=semanticQueries.toList().filter { (sql,_) ->
            sql.contains("FROM semantic_segments s",ignoreCase=true) &&
                sql.contains("semantic_vector_payloads_v2",ignoreCase=true)
        }
        assertEquals("257 eligible rows across two pages rather than one 10,000-row materialization",2,corpusReads.size)
        assertTrue("page queries must use a stable keyset order",corpusReads.all{it.first.contains("ORDER BY s.id",ignoreCase=true)})
        assertEquals(listOf(256,256),corpusReads.map{(it.second.last() as Number).toInt()})
    }

    @Test fun eg2CodeHitKeepsExactPathAndLineAsCitableEvidence()=runBlocking {
        addProject("code-project");addProject("foreign-code-project")
        val document=addDocument("code-project","answer-code","src/Answer.kt","fun answer() = 42")
        addDocument("foreign-code-project","foreign-answer-code","src/Answer.kt","fun answer() = 999")
        index("code-project","foreign-code-project")

        val bundle=foundation.build(
            request("code-project",setOf(document.id)).copy(query="Where is answer defined?"),
            evidence=emptyList(),
            memoryEnabled=false,
        )
        val source=bundle.included.single{it.kind==com.localai.workspace.context.ContextKind.SOURCE}

        assertEquals("src/Answer.kt",source.provenance.sourceName)
        assertEquals(1,source.provenance.lineStart)
        assertEquals(1,source.provenance.lineEnd)
        val prompt=bundle.conversation().userMessage
        assertTrue(prompt.contains("source-name=\"src/Answer.kt\""))
        assertTrue(prompt.contains("line-start=\"1\" line-end=\"1\""))
        assertTrue(prompt.contains("fun answer() = 42"))
        assertFalse(prompt.contains("fun answer() = 999"))
        val evidence=bundle.sourceEvidence.single()
        assertEquals(document.id,evidence.documentId)
        assertEquals("src/Answer.kt",evidence.documentTitle)
        assertNull(evidence.segmentId)
        assertEquals(source.provenance.segmentId,evidence.semanticSegmentId)
        assertEquals(1,evidence.lineStart)
        assertEquals(1,evidence.lineEnd)
        val validator=com.localai.workspace.domain.rag.CitationValidator()
        val extracted=validator.extractCandidates("See [${evidence.id}]")
        assertEquals(listOf(evidence.id),extracted.map{it.evidenceId})
        assertEquals(listOf(evidence.id),validator.validate(extracted,bundle.sourceEvidence).valid.map{it.evidence.id})
        assertEquals(listOf("SV2-0000000000000000"),validator.validate(
            validator.extractCandidates("[SV2-0000000000000000]"),bundle.sourceEvidence).invalidEvidenceIds)
    }

    @Test fun codeArchiveSearchKeepsRelativePathAndLineWithoutLeakingOtherProjects()=runBlocking {
        addProject("archive-code-project");addProject("archive-foreign-project")
        val archive=addCodeArchive("archive-code-project","source-archive",linkedMapOf(
            ".gitignore" to "build/\n**/vendor/\n",
            "src/.gitignore" to "generated/\n",
            "src/Answer.kt" to "package fixture\n\nfun falconLookup() = \"PROJECT_ARCHIVE_ONLY\"\n",
            "build/Generated.kt" to "fun falconLookup() = \"IGNORED_BUILD_ONLY\"\n",
            "module/vendor/ThirdParty.kt" to "fun thirdParty() = \"IGNORED_VENDOR_ONLY\"\n",
            "src/generated/Generated.kt" to "fun generated() = \"IGNORED_NESTED_ONLY\"\n",
        ))
        addDocument("archive-foreign-project","foreign-answer","src/Answer.kt","fun falconLookup() = \"FOREIGN_PROJECT_ONLY\"")
        index("archive-code-project","archive-foreign-project")

        val bundle=foundation.build(
            request("archive-code-project",setOf(archive.id)).copy(query="Where is falconLookup defined?"),
            evidence=emptyList(),memoryEnabled=false,
        )
        val source=bundle.included.firstOrNull{it.kind==com.localai.workspace.context.ContextKind.SOURCE && it.text.contains("PROJECT_ARCHIVE_ONLY")}
        assertNotNull("the selected code excerpt should reach the final context",source)
        requireNotNull(source)
        assertEquals("src/Answer.kt",source.provenance.sourceName)
        assertEquals(1,source.provenance.lineStart)
        assertEquals(4,source.provenance.lineEnd)
        assertTrue(source.text.contains("PROJECT_ARCHIVE_ONLY"))
        assertFalse(bundle.conversation().userMessage.contains("IGNORED_BUILD_ONLY"))
        assertFalse(bundle.conversation().userMessage.contains("FOREIGN_PROJECT_ONLY"))
    }

    @Test fun codeArchiveParserExcludesGitIgnoredDirectoryContent()=runBlocking {
        val archive=File(context.cacheDir,"ignored-folder-${UUID.randomUUID()}.zip").also{temporaryFiles+=it}
        ZipOutputStream(archive.outputStream()).use { output ->
            listOf(
                ".gitignore" to "build/\n**/vendor/\n",
                "src/.gitignore" to "generated/\n",
                "src/Visible.kt" to "VISIBLE_SOURCE_MARKER",
                "build/Generated.kt" to "IGNORED_BUILD_MARKER",
                "module/vendor/ThirdParty.kt" to "IGNORED_VENDOR_MARKER",
                "src/generated/Generated.kt" to "IGNORED_NESTED_MARKER",
                ".git/objects/secret" to "IGNORED_VCS_MARKER",
            ).forEach { (name,text) -> output.putNextEntry(ZipEntry(name));output.write(text.toByteArray());output.closeEntry() }
        }
        val parsed=DocumentParser(context).parse(archive,"application/zip",archive.name)
        val extracted=parsed.pages.joinToString("\n"){it.text}
        assertTrue(extracted.contains("VISIBLE_SOURCE_MARKER"))
        assertFalse(extracted.contains("IGNORED_BUILD_MARKER"))
        assertFalse(extracted.contains("IGNORED_VENDOR_MARKER"))
        assertFalse(extracted.contains("IGNORED_NESTED_MARKER"))
        assertFalse(extracted.contains("IGNORED_VCS_MARKER"))
    }

    @Test fun lexicalCodeArchiveFallbackKeepsRelativePathAndLineInFinalContext()=runBlocking {
        addProject("archive-lexical")
        val archive=addCodeArchive("archive-lexical","archive-lexical-doc",linkedMapOf(
            "src/Answer.kt" to "package fixture\n\nfun falconLookup() = \"LEXICAL_ARCHIVE_ONLY\"\n",
        ))

        val bundle=foundation.build(
            request("archive-lexical",setOf(archive.id),SourceRetrievalMode.LEGACY_ONLY)
                .copy(query="falconLookup"),
            evidence=emptyList(),memoryEnabled=false,
        )
        val source=bundle.included.firstOrNull{it.kind==com.localai.workspace.context.ContextKind.SOURCE}
        assertNotNull("lexical fallback should include the selected code passage",source)
        requireNotNull(source)
        assertEquals("src/Answer.kt",source.provenance.sourceName)
        assertEquals(1,source.provenance.lineStart)
        assertEquals(4,source.provenance.lineEnd)
        assertTrue(source.text.contains("LEXICAL_ARCHIVE_ONLY"))
        val citation=bundle.sourceEvidence.single()
        assertEquals(archive.id,citation.documentId)
        assertEquals("src/Answer.kt",citation.documentTitle)
        assertEquals(1,citation.lineStart)
        assertEquals(4,citation.lineEnd)
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

    @Test fun eg2Log10CellDependencyReachesFinalContextAndProvenance()=runBlocking {
        addProject("project-a")
        val document=addDocument("project-a","log10-eg2","log10.xlsx",listOf(
            spreadsheetRow("Hoja1",0,"A3","4","LOG10"),
            spreadsheetRow("Hoja1",0,"LOG10","cell-value"),
        ).joinToString("\n"))
        index("project-a")

        val bundle=foundation.build(request("project-a",setOf(document.id)).copy(query="Explica A3"),evidence=emptyList(),memoryEnabled=false)
        val source=bundle.included.single{it.kind==com.localai.workspace.context.ContextKind.SOURCE}
        val prompt=bundle.conversation().userMessage

        assertEquals(listOf("Hoja1!A3","Hoja1!LOG10"),source.provenance.cellReferences)
        assertTrue(prompt.contains("Hoja1!LOG10=cell-value"))
        assertTrue(prompt.contains("cell-references=\"Hoja1!A3,Hoja1!LOG10\""))
        assertEquals(source.text,bundle.sourceEvidence.single().excerpt)
        assertEquals(listOf("Hoja1!A3", "Hoja1!LOG10"), bundle.sourceEvidence.single().cellReferences)
        assertTrue(bundle.sourceEvidenceIssues.isEmpty())
    }

    @Test fun lexicalFallbackReportsMissingQualifiedLog10WithoutUsingHomonym()=runBlocking {
        addProject("project-a")
        val document=addDocument("project-a","log10-missing-lexical","log10-missing.xlsx",listOf(
            spreadsheetRow("Hoja1",0,"LOG10","wrong-sheet"),
            spreadsheetRow("Hoja1",0,"A3","4","'Hoja2'!LOG10"),
        ).joinToString("\n"))
        index("project-a")
        val lexical=graph.retrieval.retrieve("project-a","Explica A3",documentIds=setOf(document.id))
        assertTrue("the fixture must use the production lexical retrieval path",lexical.isNotEmpty())
        driver.failNext=true

        val bundle=foundation.build(request("project-a",setOf(document.id)).copy(query="Explica A3"),evidence=lexical,memoryEnabled=false)
        val source=bundle.included.single{it.kind==com.localai.workspace.context.ContextKind.SOURCE}
        val issue=bundle.sourceEvidenceIssues.single()

        assertTrue(bundle.notice.orEmpty().contains("SOURCE_SEMANTIC_V2_SKIPPED"))
        assertEquals(listOf("Hoja1!A3"),source.provenance.cellReferences)
        assertEquals(listOf("Hoja2!LOG10"),source.provenance.missingCellReferences)
        assertEquals(listOf("Hoja1!A3"), bundle.sourceEvidence.single().cellReferences)
        assertTrue(issue.reasons.contains("SHEET_NOT_FOUND"))
        assertTrue(bundle.requiresUserNotice)
        assertTrue(bundle.conversation().userMessage.contains("missing-cell-references=\"Hoja2!LOG10\""))
        assertFalse(bundle.conversation().userMessage.contains("wrong-sheet"))
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

    @Test fun documentRemovalWithoutCallbackFailsClosedThenLexicalFallbackUsesOnlyCurrentDocuments()=runBlocking {
        addProject("project-a")
        val removed=addDocument("project-a","removed-doc","removed.txt","ORCHID passage that must disappear after removal.")
        val retained=addDocument("project-a","retained-doc","retained.txt","ORCHID passage that remains in the workspace.")
        val oldGeneration=index("project-a").getValue("project-a")
        val repository=WorkspaceRepository(workspace,context.cacheDir)
        val embeddingCallsBefore=driver.calls

        // This repository intentionally has no sidecar callback, reproducing a process-death gap.
        assertEquals(0,repository.deleteDocument("project-a",removed.id))
        try {
            layer.search(SemanticInput.Text("ORCHID"),SemanticScope(ScopeType.PROJECT,"project-a"))
            fail("the active generation still names a removed source and must be rejected")
        } catch(expected:SemanticFailure) { assertEquals(SemanticError.REINDEX_REQUIRED,expected.code) }
        assertEquals("document drift is detected before query embedding",embeddingCallsBefore,driver.calls)
        assertEquals("NEEDS_REINDEX",semanticDb.dao().job(oldGeneration)?.state)

        val fallback=foundation.build(request("project-a"),memoryEnabled=false)
        assertTrue(fallback.notice.orEmpty().contains("SOURCE_SEMANTIC_V2_SKIPPED:REINDEX_REQUIRED"))
        val evidence=fallback.included.filter { it.kind==com.localai.workspace.context.ContextKind.SOURCE }
        assertTrue(evidence.isNotEmpty())
        assertTrue(evidence.all { it.provenance.documentId==retained.id })
        assertTrue(evidence.none { it.text.contains("must disappear") })

        val freshGeneration=layer.indexProject("project-a")
        assertNotEquals(oldGeneration,freshGeneration)
        assertEquals(freshGeneration,semanticDb.dao().active(SemanticScope(ScopeType.PROJECT,"project-a").key)?.indexId)
        val currentHits=layer.search(SemanticInput.Text("ORCHID"),SemanticScope(ScopeType.PROJECT,"project-a"),setOf(SemanticModality.TEXT))
        assertTrue(currentHits.isNotEmpty())
        assertTrue(currentHits.all { it.documentId==retained.id })
        assertTrue(currentHits.none { it.content?.contains("must disappear")==true })
        try { layer.switchIndex(oldGeneration);fail("a generation containing the removed document is not a rollback target") }
        catch(expected:IllegalArgumentException) { assertEquals("NEEDS_REINDEX",semanticDb.dao().job(oldGeneration)?.state) }
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

    @Test fun deletingWorkspacePurgesOnlyItsSemanticGenerationsAndKeepsSharedPayloads()=runBlocking {
        addProject("project-a");addProject("project-b")
        addDocument("project-a","doc-a","notes.txt","ORCHID shared source content.")
        addDocument("project-b","doc-b","notes.txt","ORCHID shared source content.")
        val generations=index("project-a","project-b")
        val dao=semanticDb.dao()
        val scopeA=SemanticScope(ScopeType.PROJECT,"project-a");val scopeB=SemanticScope(ScopeType.PROJECT,"project-b")
        val sourceIdsA=dao.sources(scopeA.key).map{it.id}
        val payloadsBefore=dao.payloadCount()
        assertTrue("fixture proves payload deduplication across scopes",payloadsBefore>0)

        WorkspaceRepository(workspace,onWorkspaceDeleted={layer.deleteProjectScope(it)}).deleteWorkspace("project-a")

        assertNull(dao.active(scopeA.key))
        assertNull(dao.job(generations.getValue("project-a")))
        assertEquals(0,dao.count(generations.getValue("project-a")))
        assertTrue(dao.sources(scopeA.key).isEmpty())
        assertTrue(sourceIdsA.all{dao.segments(it).isEmpty()})
        assertEquals("B's shared payload remains referenced",payloadsBefore,dao.payloadCount())
        assertEquals(generations.getValue("project-b"),dao.active(scopeB.key)?.indexId)
        assertEquals("READY",dao.job(generations.getValue("project-b"))?.state)
        assertTrue(layer.search(SemanticInput.Text("ORCHID"),scopeB,setOf(SemanticModality.TEXT)).any{it.content=="ORCHID shared source content."})
        assertNull("deleted project is no longer a searchable scope",workspace.projectDao().get("project-a"))
    }

    @Test fun deletingStandaloneChatAlsoPurgesItsSemanticScope()=runBlocking {
        val projectId=graph.workspace.createChat()
        addDocument(projectId,"chat-doc","chat.txt","ORCHID private chat document.")
        val oldGeneration=index(projectId).getValue(projectId)
        val conversation=graph.workspace.createConversation(projectId)

        WorkspaceRepository(workspace,onWorkspaceDeleted={layer.deleteProjectScope(it)}).deleteConversation(conversation.id)

        val scope=SemanticScope(ScopeType.PROJECT,projectId)
        assertNull(semanticDb.dao().active(scope.key))
        assertNull(semanticDb.dao().job(oldGeneration))
        assertTrue(semanticDb.dao().sources(scope.key).isEmpty())
        assertNull(workspace.projectDao().get(projectId))
    }

    @Test fun appGraphWorkspaceDeletionPurgesPersistedSemanticV2Rows()=runBlocking {
        context.deleteDatabase("semantic_v2.db")
        val persisted=graph.semanticV2.database
        val dao=persisted.dao()
        val projectId="production-delete-hook";addProject(projectId)
        val scope=SemanticScope(ScopeType.PROJECT,projectId)
        val generation="generation-to-delete";val source="$generation:document";val segment="$source:0"
        dao.job(SemanticIndexJob(generation,scope.key,"test-space","READY",1,1,1,1,1,null))
        dao.activate(SemanticActiveIndex(scope.key,generation,"test-space"))
        dao.source(SemanticSourceRecord(source,scope.key,"DOCUMENT","private.txt","text/plain","private-path",1,1,"a".repeat(64)))
        dao.segment(SemanticSegmentRecord(segment,source,"TEXT","private synthetic evidence","private.txt",null,null,null,null,null,"b".repeat(64)))
        dao.embedding(SemanticEmbeddingRecord(generation,segment,"test-space","test","test-model","c".repeat(64),2,true,"DOCUMENT","b".repeat(64),"RAW",ByteArray(8),1))

        try {
            graph.workspace.deleteWorkspace(projectId)
            assertNull(dao.active(scope.key))
            assertNull(dao.job(generation))
            assertEquals(0,dao.count(generation))
            assertTrue(dao.sources(scope.key).isEmpty())
            assertTrue(dao.segments(source).isEmpty())
            assertEquals(0,dao.payloadCount())
        } finally {
            persisted.close()
            context.deleteDatabase("semantic_v2.db")
        }
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

    @Test fun editDeleteReimportKeepsProjectGenerationsScopedAndNeverServesStaleHits()=runBlocking {
        val projectA="reimport-project-a"
        val projectB="reimport-project-b"
        addProject(projectA);addProject(projectB)
        addDocument(projectA,"reimport-retained-a","retained.txt","ORCHID RETAINEDA stays in project A.")
        addDocument(projectB,"reimport-only-b","other.txt","ORCHID PROJECTBONLY stays in project B.")

        val privateRoot=File(context.cacheDir,"m02-06-reimport-${UUID.randomUUID()}").apply{mkdirs()}
        val importer=DocumentIngestionService(context,workspace,workspace.documentDao(),
            onIndexed={id,_->layer.markNeedsReindex(id);false},outputDirectory=privateRoot)
        suspend fun importText(project:String,name:String,text:String):String {
            val uri=Uri.parse("content://local.fixture/${UUID.randomUUID()}/$name")
            shadowOf(context.contentResolver).registerInputStream(uri,ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
            return importer.ingest(project,uri).getOrThrow()
        }

        val editedDocumentId=importText(projectA,"edited.txt","ORCHID OLDREVISION must never return after replacement.")
        val oldGenerationA=index(projectA).getValue(projectA)
        val generationB=index(projectB).getValue(projectB)
        val scopeA=SemanticScope(ScopeType.PROJECT,projectA)
        val scopeB=SemanticScope(ScopeType.PROJECT,projectB)
        assertTrue(layer.search(SemanticInput.Text("OLDREVISION"),scopeA,setOf(SemanticModality.TEXT))
            .any{it.content?.contains("OLDREVISION")==true})
        assertTrue(layer.search(SemanticInput.Text("PROJECTBONLY"),scopeB,setOf(SemanticModality.TEXT))
            .any{it.documentId=="reimport-only-b"})

        val repository=WorkspaceRepository(workspace,privateRoot,onDocumentDeleted={id,documentId->
            layer.deleteDocumentSources(id,documentId)
        })
        assertEquals(0,repository.deleteDocument(projectA,editedDocumentId))
        assertEquals("NEEDS_REINDEX",semanticDb.dao().job(oldGenerationA)?.state)
        assertNotNull("the stale generation remains auditable",semanticDb.dao().job(oldGenerationA))
        assertEquals("READY",semanticDb.dao().job(generationB)?.state)
        try {
            layer.search(SemanticInput.Text("OLDREVISION"),scopeA,setOf(SemanticModality.TEXT))
            fail("a deleted document's active vectors must not be served")
        } catch(expected:SemanticFailure) {
            assertEquals(SemanticError.REINDEX_REQUIRED,expected.code)
        }

        val replacementId=importText(projectA,"edited.txt","ORCHID NEWREVISION replaces the deleted text.")
        assertNotEquals("a changed file is a new immutable import",editedDocumentId,replacementId)
        assertEquals("reimport does not reactivate the old generation","NEEDS_REINDEX",semanticDb.dao().job(oldGenerationA)?.state)
        assertEquals("another project remains searchable","READY",semanticDb.dao().job(generationB)?.state)

        val fallback=foundation.build(
            ContextRequest("ORCHID NEWREVISION",ScopeAccess(projectId=projectA),includeConversation=false),
            evidence=emptyList(),memoryEnabled=false,
        )
        assertTrue(fallback.notice.orEmpty().contains("REINDEX_REQUIRED"))
        val fallbackSources=fallback.included.filter{it.kind==com.localai.workspace.context.ContextKind.SOURCE}
        assertTrue(fallbackSources.any{it.text.contains("NEWREVISION")})
        assertTrue(fallbackSources.any{it.provenance.documentId=="reimport-retained-a"})
        assertTrue(fallbackSources.none{it.text.contains("OLDREVISION") || it.text.contains("PROJECTBONLY")})
        assertEquals("project B keeps its active generation",generationB,semanticDb.dao().active(scopeB.key)?.indexId)
        assertTrue(layer.search(SemanticInput.Text("PROJECTBONLY"),scopeB,setOf(SemanticModality.TEXT))
            .any{it.content?.contains("PROJECTBONLY")==true})

        val freshGenerationA=layer.indexProject(projectA)
        assertNotEquals(oldGenerationA,freshGenerationA)
        assertEquals("old generation remains stale history","NEEDS_REINDEX",semanticDb.dao().job(oldGenerationA)?.state)
        assertEquals("replacement is published only after indexing","READY",semanticDb.dao().job(freshGenerationA)?.state)
        val currentHits=layer.search(SemanticInput.Text("NEWREVISION"),scopeA,setOf(SemanticModality.TEXT))
        assertTrue(currentHits.any{it.documentId==replacementId && it.content?.contains("NEWREVISION")==true})
        assertTrue(currentHits.none{it.content?.contains("OLDREVISION")==true || it.content?.contains("PROJECTBONLY")==true})
        assertEquals("project B's generation is unchanged",generationB,semanticDb.dao().active(scopeB.key)?.indexId)
        privateRoot.deleteRecursively()
        Unit
    }

    @Test fun appGraphDocumentDeletionPurgesOnlyItsGenerationSourcesAndRetainsSharedVectorPayload()=runBlocking {
        context.deleteDatabase("semantic_v2.db")
        val persisted=graph.semanticV2.database
        val dao=persisted.dao()
        val projectA="document-delete-a";val projectB="document-delete-b"
        addProject(projectA);addProject(projectB)
        val privateRoot=File(context.filesDir,"documents").apply{mkdirs()}
        val sharedText="ORCHID shared document payload"
        val pathA=File(privateRoot,"delete-a.txt").apply{writeText(sharedText)}
        val pathB=File(privateRoot,"keep-b.txt").apply{writeText(sharedText)}
        val sharedHash=fingerprint(sharedText)
        val now=System.currentTimeMillis()
        workspace.documentDao().insert(DocumentEntity("delete-a",projectA,"shared.txt",localPath=pathA.path,mimeType="text/plain",fileHash=sharedHash,byteSize=pathA.length(),extractionStatus="READY",indexingStatus="READY",importedAt=now,updatedAt=now))
        workspace.documentDao().insert(DocumentEntity("keep-b",projectB,"shared.txt",localPath=pathB.path,mimeType="text/plain",fileHash=sharedHash,byteSize=pathB.length(),extractionStatus="READY",indexingStatus="READY",importedAt=now,updatedAt=now))
        workspace.documentDao().insertSegments(listOf(
            DocumentSegmentEntity(documentId="delete-a",segmentIndex=0,text=sharedText,normalizedText=sharedText,contentHash=sharedHash),
            DocumentSegmentEntity(documentId="keep-b",segmentIndex=0,text=sharedText,normalizedText=sharedText,contentHash=sharedHash),
        ))
        val scopeA=SemanticScope(ScopeType.PROJECT,projectA);val scopeB=SemanticScope(ScopeType.PROJECT,projectB)
        val generationA="generation-delete-a";val generationB="generation-keep-b"
        suspend fun seedGeneration(generation:String,scope:SemanticScope,documentId:String,path:File) {
            val source="$generation:$documentId";val segment="$source:0"
            dao.job(SemanticIndexJob(generation,scope.key,"shared-space","READY",1,1,1,now,now,null))
            dao.activate(SemanticActiveIndex(scope.key,generation,"shared-space"))
            dao.source(SemanticSourceRecord(source,scope.key,"DOCUMENT","shared.txt","text/plain",path.path,path.length(),now,sharedHash))
            dao.segment(SemanticSegmentRecord(segment,source,"TEXT",sharedText,"shared.txt",null,null,null,null,null,sharedHash))
            dao.embedding(SemanticEmbeddingRecord(generation,segment,"shared-space","test","test-model","c".repeat(64),2,true,"DOCUMENT",sharedHash,"RAW",ByteArray(8),now))
        }
        seedGeneration(generationA,scopeA,"delete-a",pathA);seedGeneration(generationB,scopeB,"keep-b",pathB)
        assertEquals("both generations share one immutable payload",1,dao.payloadCount())

        try {
            assertEquals(0,graph.workspace.deleteDocument(projectA,"delete-a"))
            assertEquals(generationA,dao.active(scopeA.key)?.indexId)
            assertEquals("NEEDS_REINDEX",dao.job(generationA)?.state)
            assertEquals("READY",dao.job(generationB)?.state)
            assertEquals(0,dao.count(generationA))
            assertEquals(1,dao.count(generationB))
            assertTrue(dao.sources(scopeA.key).isEmpty())
            assertEquals("the other project keeps its source",1,dao.sources(scopeB.key).size)
            assertEquals("the payload remains shared with the surviving generation",1,dao.payloadCount())
            assertFalse(pathA.exists())
            assertTrue(pathB.exists())
        } finally {
            persisted.close()
            context.deleteDatabase("semantic_v2.db")
            pathA.delete();pathB.delete()
        }
    }
}
