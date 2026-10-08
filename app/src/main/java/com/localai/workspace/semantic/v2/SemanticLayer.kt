package com.localai.workspace.semantic.v2

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.ai.edge.litertlm.Capabilities
import com.google.gson.Gson
import com.localai.workspace.data.WorkspaceDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class SemanticLayer(private val context: Context, private val workspace: WorkspaceDatabase, val resourceGate: Mutex, databaseOverride: SemanticDatabase? = null, preferencesName: String = "semantic_v2", managerOverride: EmbeddingEngineManager? = null) {
    val database = databaseOverride ?: SemanticDatabase.create(context)
    val manager = managerOverride ?: EmbeddingEngineManager(context, resourceGate)
    private val operations = Mutex()
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    val selection = SemanticModelSelection(context,preferencesName)
    val configurationRevision=MutableStateFlow(0L)
    suspend fun <T> exclusive(block:suspend()->T):T = operations.withLock { manager.exclusive(block) }
    suspend fun chooseProvider(choice:SemanticProviderChoice)=operations.withLock { manager.unload();selection.choose(choice);configurationRevision.value++ }
    val notice = MutableStateFlow("EG1 remains the chat provider · EG2 experimental")
    val models get() = database.dao().observeModels()
    val jobs get() = database.dao().observeJobs()
    var dimension: Int get() = prefs.getInt("dimension", 768); private set(value) { check(prefs.edit().putInt("dimension", value).commit()) }
    suspend fun setDimension(value: Int) = operations.withLock { require(value in setOf(768,256)); manager.configureDimension(value); dimension = value; configurationRevision.value++; notice.value = "Configuration changed · engine loads on first use · existing indexes retained" }
    suspend fun activate(id: String, profile: EmbeddingRuntimeProfile = EmbeddingRuntimeProfile()) = operations.withLock {
        val record = database.dao().model(id) ?: throw SemanticFailure(SemanticError.MODEL_FILE_INVALID)
        manager.select(record, profile)
        check(prefs.edit().putString("selected", id).putString("provider","EG2").commit());manager.configureDimension(dimension);configurationRevision.value++
        notice.value = "EG2 selected for Semantic V2 · EG1 chat unchanged"
    }
    suspend fun recover() {
        if(!operations.tryLock())return
        try { database.dao().recoverInterruptedJobs();File(context.filesDir,"semantic-models-v2").listFiles()?.filter { it.extension=="part" }?.forEach { it.delete() } } finally { operations.unlock() }
    }
    suspend fun restore() { if(manager.selected==null)selection.find(database.dao().observeModels().first())?.let { manager.select(it);manager.configureDimension(dimension);configurationRevision.value++ } }
    suspend fun remove(id: String) = operations.withLock {
        if (database.dao().references(id) != 0) error("Model has retained indexes. Remove is blocked to preserve retrieval.")
        val model = database.dao().model(id) ?: return@withLock
        if (manager.selected?.id == id) { manager.deselect(); prefs.edit().remove("selected").commit() }
        database.dao().removeModel(id)
        File(model.privatePath).takeIf { it.canonicalFile.parentFile == File(context.filesDir,"semantic-models-v2").canonicalFile }?.delete()
    }
    suspend fun import(uri: Uri, observer:((String,Long?,String?)->Unit)?=null): SemanticModelRecord = withContext(Dispatchers.IO) { operations.withLock {
        val dir = File(context.filesDir, "semantic-models-v2").apply { mkdirs() }
        val temp = File(dir,"${UUID.randomUUID()}.part")
        val previous = manager.selected; val oldProfile = manager.profile
        var registered:SemanticModelRecord?=null
        try {
            notice.value = "COPY_PRIVATE";observer?.invoke("COPY_PRIVATE",null,null)
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { output ->
                val buffer = ByteArray(64*1024); var size = 0L
                while (true) { ensureActive(); val count = input.read(buffer); if(count < 0) break; size += count
                    if (size > 1_000_000_000L) throw SemanticFailure(SemanticError.MODEL_FILE_INVALID)
                    digest.update(buffer,0,count); output.write(buffer,0,count)
                }
            } } ?: throw SemanticFailure(SemanticError.MODEL_FILE_INVALID)
            if (temp.length() < 32 || java.io.DataInputStream(temp.inputStream()).use { stream -> String(ByteArray(8).also { stream.readFully(it) },Charsets.US_ASCII) } != "LITERTLM") throw SemanticFailure(SemanticError.MODEL_FILE_INVALID)
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            observer?.invoke("HASH_VERIFY",temp.length(),hash)
            notice.value = "MODEL_METADATA · embedding signature verified by native initialization/probe"
            // Capabilities exposes modality flags, NOT model family or signatures in SDK 0.17.1.
            val manifest = context.assets.open("semantic-v2/eg2-artifact.json").bufferedReader().use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
            val expectedHash = manifest.getAsJsonObject("file").getAsJsonObject("lfs").get("sha256").asString
            if (hash != expectedHash) throw SemanticFailure(SemanticError.MODEL_METADATA_UNSUPPORTED)
            observer?.invoke("MODEL_METADATA",temp.length(),hash)
            val modalities = Capabilities(temp.path).use { c -> val m = c.inputModalities(); buildList {
                if(m.text) add("TEXT"); if(m.text) add("CODE"); if(m.vision) add("IMAGE"); if(m.audio) add("AUDIO"); if(m.video) add("VIDEO")
            }.joinToString(",") }
            if ("TEXT" !in modalities.split(',')) throw SemanticFailure(SemanticError.MODEL_METADATA_UNSUPPORTED)
            // Exact generic artifact is identified by pinned official manifest AND locally calculated hash; SDK does not expose family.
            val proposed = SemanticModelRecord("eg2:$hash", "EmbeddingGemma 2 740M", "EMBEDDING_GEMMA_2", "embeddinggemma-2-740m.litertlm", temp.path, temp.length(), hash, "LITERTLM", "litert-lm", 768,"768,512,256,128","TEXT,CODE,IMAGE,AUDIO,VIDEO","TEXT","LiteRT-LM 0.17.1",System.currentTimeMillis(),"TEXT_PROBE_PASSED",modelVersion=manifest.get("revision").asString,runtimeModalities=modalities)
            manager.select(proposed)
            observer?.invoke("EMBEDDING_PROBE_768",temp.length(),hash)
            manager.embed(SemanticInput.Text("semantic import validation"),EmbeddingTask.SEARCH,768)
            observer?.invoke("EMBEDDING_PROBE_256",temp.length(),hash)
            manager.embed(SemanticInput.Text("semantic import validation"),EmbeddingTask.SEARCH,256)
            manager.unload(); ensureActive()
            val final = File(dir,"$hash.litertlm")
            val oldRecord=database.dao().model(proposed.id)
            val fileExisted=final.exists()
            check(temp.renameTo(final)) { "Atomic model rename failed" }
            val committed = proposed.copy(privatePath=final.path)
            observer?.invoke("CONFIG_COMMIT",committed.fileSize,hash)
            try {
                database.dao().model(committed)
                selection.select(committed)
            } catch(error:Throwable) {
                withContext(NonCancellable) { if(oldRecord!=null)database.dao().model(oldRecord)else{database.dao().removeModel(committed.id);if(!fileExisted)final.delete()} }
                throw error
            }
            registered=committed;configurationRevision.value++
            notice.value = "Ready · engine loads on first use"
            committed
        } catch(cancel: CancellationException) { throw cancel }
        catch(error: Throwable) { notice.value = (error as? SemanticFailure)?.code?.name ?: "MODEL_LOAD_FAILED"; throw error }
        finally { withContext(NonCancellable) { temp.delete(); manager.deselect(); if(registered!=null) { manager.select(registered!!);manager.configureDimension(dimension) } else if (previous != null) manager.select(previous,oldProfile) } }
    } }
    suspend fun validate(id: String) = operations.withLock {
        val old = manager.selected; val profile=manager.profile
        try { val model=database.dao().model(id) ?: error("Missing model");manager.select(model)
            manager.embed(SemanticInput.Text("validation"),EmbeddingTask.SEARCH,768); manager.embed(SemanticInput.Text("validation"),EmbeddingTask.SEARCH,256)
            database.dao().model(model.copy(validationStatus="TEXT_PROBE_PASSED"));notice.value="Ready · text probe passed"
        } finally { withContext(NonCancellable) { manager.unload();if(old!=null)manager.select(old,profile) } }
    }
    /** New generations of source/segment IDs preserve old index provenance during blue/green builds. */
    suspend fun indexProject(projectId: String): String = withContext(Dispatchers.IO) { operations.withLock {
        restore(); val model=manager.selected ?: throw SemanticFailure(SemanticError.MODEL_FILE_INVALID)
        val dim=dimension;val space=EmbeddingEngineManager.space(model,dim); val scope=SemanticScope(ScopeType.PROJECT,projectId)
        val id=UUID.randomUUID().toString(); val start=System.currentTimeMillis(); val dao=database.dao()
        var job=SemanticIndexJob(id,scope.key,space.id,"INDEXING",0,0,0,start,start,null);dao.job(job)
        try {
            val active=dao.active(scope.key)
            if(active!=null) {
                val oldCorpus=dao.corpus(active.indexId,scope.key,active.spaceKey,10_000)
                val mediaSources=dao.sources(scope.key).filter { it.sourceType in setOf("IMAGE","AUDIO","VIDEO") }.associateBy { it.id }
                val preserved=oldCorpus.filter { it.segment.sourceId in mediaSources }
                for(old in preserved) {
                    ensureActive();val source=mediaSources.getValue(old.segment.sourceId);val segment=old.segment
                    val input=when(source.sourceType) {
                        "IMAGE"->SemanticInput.Image(source.reference)
                        "AUDIO"->SemanticInput.Audio(source.reference,segment.startMs ?: 0,segment.endMs)
                        "VIDEO"->SemanticInput.Image(File(File(source.reference).parentFile,"frame-${segment.startMs}.jpg").path)
                        else->throw SemanticFailure(SemanticError.MODALITY_UNSUPPORTED)
                    }
                    val cached=dao.reusable(segment.contentHash,space.id,"DOCUMENT")
                    val vector=if(cached!=null)cached.vector else SemanticVectors.encode(manager.embed(input,EmbeddingTask.DOCUMENT,dim).vector)
                    dao.embedding(SemanticEmbeddingRecord(id,segment.id,space.id,model.provider,model.id,model.sha256,dim,true,"DOCUMENT",segment.contentHash,SemanticVectors.FORMAT,vector,System.currentTimeMillis()))
                    job=job.copy(segments=job.segments+1,embeddings=job.embeddings+1)
                }
                job=job.copy(sources=preserved.map { it.segment.sourceId }.distinct().size);dao.job(job)
            }
            val documents=workspace.documentDao().forProject(projectId).filter { it.extractionStatus=="READY" }
            for(document in documents) {
                ensureActive()
                val sourceId="$id:${document.id}"
                dao.source(SemanticSourceRecord(sourceId,scope.key,"DOCUMENT",document.displayName,document.mimeType,document.localPath,document.byteSize,document.updatedAt,document.fileHash))
                val code=document.displayName.substringAfterLast('.',"").lowercase() in setOf("kt","java","py","js","ts","c","cpp","rs","go","swift","sh")
                val parsed = workspace.documentDao().segmentsForDocument(document.id)
                val codeChunks = if(code) CodeSegments.chunk(com.localai.workspace.documents.StructuredDocuments.text(com.localai.workspace.documents.StructuredDocuments.readFile(File(document.localPath))).first) else emptyList()
                val codeLines = mutableMapOf<Long, Pair<Int,Int>>()
                val chunks = if(code) codeChunks.mapIndexed { i,c -> codeLines[i.toLong()]=c.lineStart to c.lineEnd;com.localai.workspace.data.DocumentSegmentEntity(id=i.toLong(),documentId=document.id,segmentIndex=i,text=c.text,normalizedText=c.text,contentHash=fingerprint(c.text)) } else parsed
                for (chunk in chunks) {
                    ensureActive()
                    val segmentId="$sourceId:${chunk.id}"; val task=if(code) EmbeddingTask.CODE_DOCUMENT else EmbeddingTask.DOCUMENT
                    val effective=TaskPromptProfile.format(task,chunk.text,document.displayName);val hash=fingerprint(effective)
                    val reusable=dao.reusable(hash,space.id,task.name)
                    val result=if(reusable!=null) EmbeddingResult(space,SemanticVectors.decode(reusable.vector,dim),0) else manager.embed(if(code) SemanticInput.Code(chunk.text,document.displayName) else SemanticInput.Text(chunk.text,document.displayName),task,dim)
                    if(result.space != space)throw SemanticFailure(SemanticError.SPACE_MISMATCH)
                    dao.segment(SemanticSegmentRecord(segmentId,sourceId,if(code)"CODE" else "TEXT",chunk.text,document.displayName,chunk.pageStart,codeLines[chunk.id]?.first,codeLines[chunk.id]?.second,null,null,hash,metadataJson=if(code)Gson().toJson(mapOf("language" to document.displayName.substringAfterLast('.'),"relativePath" to document.displayName)) else "{}",legacySegmentId=if(code)null else chunk.id))
                    dao.embedding(SemanticEmbeddingRecord(id,segmentId,space.id,model.provider,model.id,model.sha256,dim,true,task.name,hash,SemanticVectors.FORMAT,SemanticVectors.encode(result.vector),System.currentTimeMillis()))
                    job=job.copy(segments=job.segments+1,embeddings=job.embeddings+1,updatedAt=System.currentTimeMillis());dao.job(job)
                }
                job=job.copy(sources=job.sources+1);dao.job(job)
            }
            if (job.embeddings==0 || dao.count(id)!=job.segments) throw SemanticFailure(SemanticError.REINDEX_REQUIRED)
            ensureActive()
            database.withTransaction { dao.job(job.copy(state="READY",updatedAt=System.currentTimeMillis()));dao.activate(SemanticActiveIndex(scope.key,id,space.id)) }
            notice.value="Indexed · ${job.sources} sources · ${job.embeddings} vectors"; id
        } catch(cancel:CancellationException) { withContext(NonCancellable) { dao.job(job.copy(state="CANCELLED",errorCode="INDEX_JOB_CANCELLED",updatedAt=System.currentTimeMillis())) };throw cancel }
        catch(error:Throwable) { dao.job(job.copy(state="FAILED",errorCode=(error as? SemanticFailure)?.code?.name ?: "INDEX_FAILED",updatedAt=System.currentTimeMillis()));throw error }
    } }
    suspend fun indexMedia(uri: Uri, scope: SemanticScope, modality: SemanticModality): String = withContext(Dispatchers.IO) { operations.withLock {
        restore(); val model=manager.selected ?: throw SemanticFailure(SemanticError.MODEL_FILE_INVALID)
        val prepared=when(modality) { SemanticModality.IMAGE -> SemanticMedia(context).image(uri); SemanticModality.AUDIO -> SemanticMedia(context).audio(uri); SemanticModality.VIDEO -> SemanticMedia(context).video(uri); else -> throw SemanticFailure(SemanticError.MODALITY_UNSUPPORTED) }
        val id=UUID.randomUUID().toString();val space=EmbeddingEngineManager.space(model,dimension);val dao=database.dao();val now=System.currentTimeMillis()
        val name=context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst())it.getString(0)else null } ?: if(uri.scheme=="file")File(uri.path.orEmpty()).name else modality.name
        val hash=prepared.reference.inputStream().use { input -> val md=MessageDigest.getInstance("SHA-256");val b=ByteArray(65536);while(true){ensureActive();val n=input.read(b);if(n<0)break;md.update(b,0,n)};md.digest().joinToString(""){"%02x".format(it)} }
        val properties=mutableMapOf<String,Any?>("embeddingMethod" to prepared.method,"preparedBytes" to prepared.reference.length())
        if(modality==SemanticModality.IMAGE) { val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true};android.graphics.BitmapFactory.decodeFile(prepared.reference.path,bounds);properties["width"]=bounds.outWidth;properties["height"]=bounds.outHeight;properties["preparedMimeType"]="image/jpeg" }
        val metadata=Gson().toJson(properties)
        var job=SemanticIndexJob(id,scope.key,space.id,"INDEXING",1,0,0,now,now,null);dao.job(job)
        var committed=false
        try {
            // Append into a new green generation; retain every existing source in this scope.
            val active=dao.active(scope.key)
            if(active!=null) {
                if(active.spaceKey!=space.id)throw SemanticFailure(SemanticError.SPACE_MISMATCH)
                dao.corpus(active.indexId,scope.key,space.id,10_000).forEach { c ->
                    dao.embedding(SemanticEmbeddingRecord(id,c.segment.id,space.id,model.provider,model.id,model.sha256,dimension,true,if(c.segment.modality=="CODE")"CODE_DOCUMENT"else"DOCUMENT",c.segment.contentHash,SemanticVectors.FORMAT,c.vector,now))
                    job=job.copy(segments=job.segments+1,embeddings=job.embeddings+1)
                }
            }
            dao.source(SemanticSourceRecord(id,scope.key,modality.name,name,context.contentResolver.getType(uri) ?: "application/octet-stream",prepared.reference.path,prepared.reference.length(),now,hash,metadata))
            prepared.segments.forEachIndexed { i, pair ->
                ensureActive()
                val inputFile=File(when(val input=pair.first) { is SemanticInput.Image -> input.privateReference;is SemanticInput.Audio -> input.privateReference;else -> throw SemanticFailure(SemanticError.MODALITY_UNSUPPORTED) })
                val inputHash=inputFile.inputStream().use { stream -> val digest=MessageDigest.getInstance("SHA-256");val bytes=ByteArray(65536);while(true){ensureActive();val n=stream.read(bytes);if(n<0)break;digest.update(bytes,0,n)};digest.digest().joinToString(""){"%02x".format(it)} }
                val cached=dao.reusable(inputHash,space.id,"DOCUMENT")
                val result=if(cached==null)manager.embed(pair.first,EmbeddingTask.DOCUMENT,dimension) else EmbeddingResult(space,SemanticVectors.decode(cached.vector,dimension),0)
                val segment="$id:$i";if(result.space!=space)throw SemanticFailure(SemanticError.SPACE_MISMATCH)
                dao.segment(SemanticSegmentRecord(segment,id,modality.name,null,name,null,null,null,pair.second.first,pair.second.second,inputHash,metadata))
                dao.embedding(SemanticEmbeddingRecord(id,segment,space.id,model.provider,model.id,model.sha256,dimension,true,"DOCUMENT",inputHash,SemanticVectors.FORMAT,SemanticVectors.encode(result.vector),System.currentTimeMillis()))
                job=job.copy(segments=job.segments+1,embeddings=job.embeddings+1,updatedAt=System.currentTimeMillis());dao.job(job)
            }
            ensureActive();database.withTransaction { dao.job(job.copy(state="READY"));dao.activate(SemanticActiveIndex(scope.key,id,space.id));dao.model(model.copy(verifiedModalities=(model.verifiedModalities.split(',') + if(modality==SemanticModality.VIDEO)"VIDEO_FRAME_SEQUENCE" else modality.name).distinct().joinToString(","))) };committed=true;id
        } catch(cancel:CancellationException) { withContext(NonCancellable){dao.job(job.copy(state="CANCELLED",errorCode="INDEX_JOB_CANCELLED"))};throw cancel }
        catch(error:Throwable) { dao.job(job.copy(state="FAILED",errorCode=(error as? SemanticFailure)?.code?.name ?: "MEDIA_INDEX_FAILED"));throw error }
        finally { if(!committed) { if(prepared.method.startsWith("FRAME_"))prepared.reference.parentFile?.deleteRecursively() else prepared.reference.delete() } }
    } }
    suspend fun markNeedsReindex(projectId:String) = operations.withLock {
        val dao=database.dao();val active=dao.active(SemanticScope(ScopeType.PROJECT,projectId).key) ?: return@withLock
        val job=dao.job(active.indexId) ?: return@withLock
        if(job.state=="READY")dao.job(job.copy(state="NEEDS_REINDEX",updatedAt=System.currentTimeMillis()))
    }
    suspend fun switchIndex(id: String) = operations.withLock {
        val dao=database.dao();val job=dao.job(id) ?: error("Missing index")
        require(job.state=="READY" && job.embeddings>0 && job.segments==job.embeddings && dao.count(id)==job.embeddings)
        dao.activate(SemanticActiveIndex(job.scopeKey,id,job.spaceKey))
    }
    private suspend fun requireReadyGeneration(scope:SemanticScope,active:SemanticActiveIndex):SemanticIndexJob {
        val dao=database.dao();val job=dao.job(active.indexId)
        if(job==null || job.scopeKey!=scope.key || job.spaceKey!=active.spaceKey || job.state!="READY" || job.embeddings<=0 || job.segments!=job.embeddings || dao.count(active.indexId)!=job.embeddings)
            throw SemanticFailure(SemanticError.REINDEX_REQUIRED)
        return job
    }
    suspend fun search(query: SemanticInput, scope: SemanticScope, modalities: Set<SemanticModality> = SemanticModality.entries.toSet(), limit: Int = 5, documentIds:Set<String> = emptySet()): List<RetrievalHit> = withContext(Dispatchers.IO) {
        require(limit in 1..100); if(scope.type==ScopeType.PROJECT && workspace.projectDao().get(scope.id)==null)throw SemanticFailure(SemanticError.REINDEX_REQUIRED); restore();val model=manager.selected ?: throw SemanticFailure(SemanticError.MODEL_FILE_INVALID)
        val dao=database.dao();val active=dao.active(scope.key) ?: throw SemanticFailure(SemanticError.REINDEX_REQUIRED)
        val space=EmbeddingEngineManager.space(model,dimension)
        if(active.spaceKey!=space.id) throw SemanticFailure(SemanticError.SPACE_MISMATCH)
        val generation=requireReadyGeneration(scope,active)
        if(generation.embeddings>10_000)throw SemanticFailure(SemanticError.REINDEX_REQUIRED)
        val projectDocuments=if(scope.type==ScopeType.PROJECT)workspace.documentDao().forProject(scope.id).filter{it.extractionStatus=="READY" && it.indexingStatus=="READY"}else emptyList()
        val documentIdByPath=projectDocuments.associate{it.localPath to it.id}
        val validSelectedIds=if(documentIds.isEmpty())emptySet()else projectDocuments.asSequence().map{it.id}.filter{it in documentIds}.toSet()
        if(documentIds.isNotEmpty()&&validSelectedIds.isEmpty())return@withContext emptyList()
        val sources=if(scope.type==ScopeType.PROJECT)dao.sources(scope.key).filter{it.sourceType=="DOCUMENT"}else emptyList()
        val sourceDocumentIds=sources.mapNotNull { source->documentIdByPath[source.reference]?.let{source.id to it} }.toMap()
        val allowedSourceIds=if(documentIds.isEmpty())null else sourceDocumentIds.filterValues{it in validSelectedIds}.keys
        if(allowedSourceIds!=null&&allowedSourceIds.isEmpty())return@withContext emptyList()
        val corpus=dao.corpus(active.indexId,scope.key,space.id,10_000).filter {
            SemanticModality.valueOf(it.segment.modality) in modalities && (allowedSourceIds==null || it.segment.sourceId in allowedSourceIds)
        }
        if(corpus.isEmpty())return@withContext emptyList()
        val queryVector=manager.embed(query,TaskPromptProfile.queryTask(query,modalities),dimension)
        val ranked=corpus.map { c -> c to SemanticVectors.cosine(queryVector,EmbeddingResult(space,SemanticVectors.decode(c.vector,c.dimension),0)) }.sortedByDescending { it.second }.take(30)
        // Existing FTS remains the lexical source of truth. Fuse ranks, never raw scores from different spaces.
        val lexical=if(scope.type==ScopeType.PROJECT && query is SemanticInput.Text) {
            val terms=Regex("[\\p{L}\\p{N}_]{2,}").findAll(query.content).map { "\"${it.value}\"" }.take(18).joinToString(" OR ")
            if(terms.isBlank()) emptyList() else if(validSelectedIds.isEmpty())workspace.documentDao().searchLexical(scope.id,terms,30)else workspace.documentDao().searchLexicalDocuments(scope.id,validSelectedIds.toList().sorted(),terms,30)
        } else emptyList()
        val lexicalIds=lexical.map { it.id }
        val candidates=(ranked.map { it.first } + corpus.filter { c -> lexicalIds.any { c.segment.legacySegmentId==it } }).distinctBy { it.segment.id }
        val hits=candidates.map { c ->
            val semanticRank=ranked.indexOfFirst { it.first.segment.id==c.segment.id };val lexicalRank=lexicalIds.indexOfFirst { c.segment.legacySegmentId==it }
            val fused=(if(semanticRank>=0)1.0/(60+semanticRank+1)else 0.0)+(if(lexicalRank>=0)1.0/(60+lexicalRank+1)else 0.0)
            val s=c.segment
            RetrievalHit(s.sourceId,s.id,c.sourceName,SemanticModality.valueOf(s.modality),s.textContent,s.page,s.lineStart,s.lineEnd,s.startMs,s.endMs,ranked.firstOrNull { it.first.segment.id==s.id }?.second,if(lexicalRank>=0)1.0/(lexicalRank+1)else null,fused,space.id,sourceDocumentIds[s.sourceId],s.legacySegmentId)
        }.sortedByDescending { it.fusedScore }.take(limit)
        val currentActive=dao.active(scope.key);val currentJob=dao.job(active.indexId)
        if(currentActive!=active || currentJob?.state!="READY" || currentJob.updatedAt!=generation.updatedAt || currentJob.embeddings!=generation.embeddings)
            throw SemanticFailure(SemanticError.REINDEX_REQUIRED)
        hits
    }
}
