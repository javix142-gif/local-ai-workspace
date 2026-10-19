package com.localai.workspace.artifacts

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.Locale
import java.util.zip.ZipFile
import com.localai.workspace.documents.StructuredDocuments

data class ArtifactRevision(val id:String,val version:Int,val name:String,val format:ArtifactFormat,val bytes:Long,val sha256:String,val createdAt:Long,val specSha256:String) {
    fun json()=JSONObject().put("id",id).put("version",version).put("name",name).put("format",format.name).put("bytes",bytes).put("sha256",sha256).put("createdAt",createdAt).put("specSha256",specSha256)
}

/** One explicit cell update. Formula edits are stored without a cached result or local evaluation. */
data class XlsxCellEdit(
    val sheet:String,
    val address:String,
    val value:String?=null,
    val formula:String?=null,
    val type:String?=null,
    val style:String?=null
)

/** App-private, immutable revisions. Commit is a same-filesystem directory rename after
 * validation/hash/fsync. Incomplete directories never become visible revisions. No Room change.
 * Cross-process file lock serializes writes from the app and the LiteRT tool service.
 */
class ArtifactStore(context:Context,private val root:File=File(context.filesDir,"tool-workspaces")) {
    private val renderer=ArtifactRenderer(context)
    private fun project(projectId:String):File {
        require(projectId.isNotBlank()&&projectId.length<=128){"PROJECT_ID_INVALID"}
        val parent=root.canonicalFile.apply{check(mkdirs()||isDirectory){"ARTIFACT_STORAGE_UNAVAILABLE"}}
        return File(parent,"artifacts-${hash(projectId.toByteArray())}").also{f->require(f.canonicalFile.parentFile==parent){"ARTIFACT_PATH_ESCAPE"};check(f.mkdirs()||f.isDirectory)}
    }
    private fun item(projectId:String,id:String):File {
        require(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}").matches(id)){"ARTIFACT_ID_INVALID"}
        val parent=project(projectId);return File(parent,id).also{require(it.canonicalFile.parentFile==parent.canonicalFile){"ARTIFACT_PATH_ESCAPE"}}
    }
    fun list(projectId:String):List<ArtifactRevision> = project(projectId).listFiles().orEmpty().filter{it.isDirectory&&!it.name.startsWith('.')}.mapNotNull { folder->
        history(projectId,folder.name).maxByOrNull{it.version}
    }.sortedWith(compareByDescending<ArtifactRevision>{it.createdAt}.thenBy{it.id})
    fun history(projectId:String,id:String):List<ArtifactRevision> = item(projectId,id).listFiles().orEmpty().filter{it.isDirectory&&Regex("v[1-9][0-9]*").matches(it.name)}.map{folder->
        val meta=File(folder,"revision.json");require(meta.length() in 1..4096){"ARTIFACT_METADATA_INVALID"};val j=JSONObject(meta.readText())
        ArtifactRevision(j.getString("id"),j.getInt("version"),j.getString("name"),ArtifactFormat.valueOf(j.getString("format")),j.getLong("bytes"),j.getString("sha256"),j.getLong("createdAt"),j.getString("specSha256")).also{
            require(it.id==id&&folder.name=="v${it.version}"&&it.bytes in (if(it.format==ArtifactFormat.CODE)0L else 1L)..20L*1024*1024&&Regex("[0-9a-f]{64}").matches(it.sha256)&&Regex("[0-9a-f]{64}").matches(it.specSha256)){"ARTIFACT_METADATA_INVALID"}
        }
    }.sortedByDescending{it.version}
    fun spec(projectId:String,revision:ArtifactRevision):ArtifactSpec {
        val folder=File(item(projectId,revision.id),"v${revision.version}");val f=File(folder,"spec.json")
        require(f.length() in 1..ArtifactSpec.MAX_SPEC_BYTES.toLong()){"ARTIFACT_SPEC_INVALID"}
        require(fileHash(f)==revision.specSha256){"ARTIFACT_SPEC_CHANGED"}
        return ArtifactSpec.parse(JSONObject(f.readText())).also{require(it.name==revision.name&&it.format==revision.format){"ARTIFACT_SPEC_MISMATCH"}}
    }
    /** Used for previews, export and tools. Verify bytes against the immutable committed hash. */
    fun verifiedFile(projectId:String,revision:ArtifactRevision):File {
        val folder=File(item(projectId,revision.id),"v${revision.version}").canonicalFile
        val file=File(folder,revision.name)
        require(file.canonicalFile.parentFile==folder&&file.isFile&&file.length()==revision.bytes&&fileHash(file)==revision.sha256){"ARTIFACT_BYTES_CHANGED"}
        return file
    }
    /** Stage a review-only patch for one exact current CODE revision; no artifact bytes change here. */
    fun stageCodePatch(projectId:String,id:String,expectedVersion:Int,expectedSha256:String,edits:List<CodePatchEdit>):CodePatchPreview {
        require(expectedVersion in 1..1000) { "CODE_PATCH_VERSION_INVALID" }
        val history = history(projectId,id)
        val revision = history.singleOrNull { it.version == expectedVersion } ?: error("ARTIFACT_VERSION_NOT_FOUND")
        require(history.firstOrNull()?.version == expectedVersion) { "CODE_PATCH_BASE_NOT_CURRENT" }
        require(revision.format == ArtifactFormat.CODE) { "CODE_PATCH_CODE_ARTIFACT_REQUIRED" }
        val file = verifiedFile(projectId,revision)
        require(fileHash(file) == expectedSha256) { "CODE_PATCH_BASE_HASH_MISMATCH" }
        val base = spec(projectId,revision)
        val before = base.json().getString("body")
        require(CodePatchPlanner.hash(before) == revision.sha256) { "CODE_PATCH_BASE_HASH_MISMATCH" }
        val after = CodePatchPlanner.apply(before,expectedSha256,edits)
        val proposed = base.json().put("body",after)
        ArtifactSpec.parse(proposed)
        val preview = CodePatchPreview(CodePatchPlanner.newPreviewId(),id,revision.name,revision.version,
            revision.sha256,CodePatchPlanner.hash(after),System.currentTimeMillis(),before,after,edits.toList())
        CodePatchPlanner.validatePreview(preview)
        val folder = patchFolder(projectId)
        require(folder.listFiles().orEmpty().count { it.isFile && it.name.endsWith(".json") } < MAX_PENDING_PATCHES) { "CODE_PATCH_PREVIEW_LIMIT" }
        val payload = preview.json().toString()
        require(payload.toByteArray(Charsets.UTF_8).size <= MAX_PATCH_BYTES) { "CODE_PATCH_PREVIEW_TOO_LARGE" }
        val target = patchFile(projectId,preview.id)
        val temp = File(folder,".${preview.id}.pending")
        try {
            syncText(temp,payload)
            check(!target.exists() && temp.renameTo(target)) { "CODE_PATCH_PREVIEW_COMMIT_FAILED" }
        } finally { if(temp.exists()) temp.delete() }
        return preview
    }
    /** Pending previews are project-scoped and validated again whenever they are read. */
    fun codePatchPreviews(projectId:String):List<CodePatchPreview> = patchFolder(projectId).listFiles().orEmpty()
        .filter { it.isFile && it.name.endsWith(".json") }.map { file ->
            require(file.length() in 1..MAX_PATCH_BYTES.toLong()) { "CODE_PATCH_PREVIEW_INVALID" }
            CodePatchPreview.parse(JSONObject(file.readText(Charsets.UTF_8))).also { preview ->
                require(file.name == "${preview.id}.json") { "CODE_PATCH_PREVIEW_INVALID" }
            }
        }.sortedWith(compareByDescending<CodePatchPreview>{it.createdAt}.thenBy{it.id})
    fun codePatchPreview(projectId:String,previewId:String):CodePatchPreview {
        val file=patchFile(projectId,previewId)
        require(file.isFile && file.length() in 1..MAX_PATCH_BYTES.toLong()) { "CODE_PATCH_PREVIEW_NOT_FOUND" }
        return CodePatchPreview.parse(JSONObject(file.readText(Charsets.UTF_8))).also { require(file.name == "${it.id}.json") { "CODE_PATCH_PREVIEW_INVALID" } }
    }
    /** Applying creates a new immutable local draft revision. Export to an external folder remains a separate SAF action. */
    fun applyCodePatchPreview(projectId:String,previewId:String):ArtifactRevision {
        val preview=codePatchPreview(projectId,previewId)
        val current=history(projectId,preview.artifactId).firstOrNull()
        // Recover safely if the process died after the immutable revision commit but before preview cleanup.
        if(current?.version == preview.baseVersion+1 && current.sha256 == preview.proposedSha256) {
            patchFile(projectId,previewId).delete()
            return current
        }
        require(current?.version == preview.baseVersion && current.sha256 == preview.baseSha256) { "CODE_PATCH_BASE_STALE" }
        val original=verifiedFile(projectId,current)
        require(fileHash(original)==preview.baseSha256 && spec(projectId,current).json().getString("body")==preview.before) { "CODE_PATCH_BASE_STALE" }
        val updated=ArtifactSpec.parse(spec(projectId,current).json().put("body",preview.after))
        val revision=revise(projectId,preview.artifactId,preview.baseVersion,updated)
        check(revision.sha256==preview.proposedSha256) { "CODE_PATCH_RESULT_HASH_MISMATCH" }
        patchFile(projectId,previewId).delete() // A leftover is stale and harmless; the next apply is idempotent.
        return revision
    }
    /** Discard removes only this app-private, unapplied preview. */
    fun discardCodePatchPreview(projectId:String,previewId:String) {
        val file=patchFile(projectId,previewId)
        require(file.isFile) { "CODE_PATCH_PREVIEW_NOT_FOUND" }
        val preview=CodePatchPreview.parse(JSONObject(file.readText(Charsets.UTF_8)))
        require(preview.id==previewId) { "CODE_PATCH_PREVIEW_INVALID" }
        check(file.delete()) { "CODE_PATCH_PREVIEW_CLEANUP_FAILED" }
    }
    fun create(projectId:String,spec:ArtifactSpec)=commit(projectId,UUID.randomUUID().toString(),0,spec)
    fun revise(projectId:String,id:String,expectedVersion:Int,spec:ArtifactSpec)=commit(projectId,id,expectedVersion,spec)
    /** Apply at most 64 exact cell edits to the current private XLSX revision.
     * Formula edits remove the previous cached result; this app never evaluates formulas.
     * Every successful edit creates a new immutable revision; untouched cells remain intact.
     */
    fun applyXlsxCellEdits(projectId:String,id:String,expectedVersion:Int,edits:List<XlsxCellEdit>):ArtifactRevision {
        require(expectedVersion in 1..1000) { "XLSX_REVISION_INVALID" }
        require(edits.size in 1..64) { "XLSX_CELL_EDIT_LIMIT_64" }
        val versions=history(projectId,id)
        val revision=versions.singleOrNull{it.version==expectedVersion} ?: error("ARTIFACT_VERSION_NOT_FOUND")
        require(versions.firstOrNull()?.version==expectedVersion) { "ARTIFACT_REVISION_NOT_CURRENT" }
        require(revision.format==ArtifactFormat.XLSX) { "XLSX_ARTIFACT_REQUIRED" }
        val updated=spec(projectId,revision).json()
        val sheets=updated.getJSONArray("sheets")
        val seen=mutableSetOf<String>()
        edits.forEach { edit ->
            require((edit.value!=null) xor (edit.formula!=null)) { "XLSX_CELL_EDIT_VALUE_OR_FORMULA_REQUIRED" }
            require(edit.sheet.length in 1..31) { "XLSX_SHEET_NAME_INVALID" }
            ArtifactSpec.address(edit.address)
            val sheetKey=edit.sheet.lowercase(Locale.ROOT)
            require(seen.add("$sheetKey!${edit.address}")) { "XLSX_CELL_EDIT_DUPLICATE" }
            var target:JSONObject?=null
            for(index in 0 until sheets.length()) {
                val candidate=sheets.getJSONObject(index)
                if(candidate.getString("name").lowercase(Locale.ROOT)==sheetKey) { target=candidate;break }
            }
            val sheet=requireNotNull(target) { "XLSX_SHEET_NOT_FOUND" }
            val cells=sheet.getJSONArray("cells")
            var existing:JSONObject?=null
            for(index in 0 until cells.length()) {
                val cell=cells.getJSONObject(index)
                if(cell.getString("address")==edit.address) { existing=cell;break }
            }
            val existingCell=existing
            if(existingCell!=null) {
                if(edit.formula!=null) {
                    existingCell.put("formula",edit.formula)
                    existingCell.remove("value") // A prior cached value is stale after formula replacement.
                } else {
                    require(!existingCell.has("formula")) { "XLSX_FORMULA_CELL_REQUIRES_EXPLICIT_REVISION" }
                    existingCell.put("value",requireNotNull(edit.value))
                }
                edit.type?.let{existingCell.put("type",it)}
                edit.style?.let{existingCell.put("style",it)}
            } else {
                val cell=JSONObject().put("address",edit.address)
                edit.value?.let{cell.put("value",it)}
                edit.formula?.let{cell.put("formula",it)}
                if(edit.value!=null)cell.put("type",edit.type ?: "string") else edit.type?.let{cell.put("type",it)}
                edit.style?.let{cell.put("style",it)}
                cells.put(cell)
            }
        }
        return revise(projectId,id,expectedVersion,ArtifactSpec.parse(updated))
    }
    private fun commit(projectId:String,id:String,expectedVersion:Int,spec:ArtifactSpec):ArtifactRevision {
        val owner=project(projectId)
        RandomAccessFile(File(owner,".write.lock"),"rw").channel.use { channel->
            val lock=try{channel.tryLock()}catch(_:java.nio.channels.OverlappingFileLockException){null}
            requireNotNull(lock){"ARTIFACT_WRITE_BUSY_RETRY"}
            lock.use {
                val folder=item(projectId,id).apply{check(mkdirs()||isDirectory)}
                val old=history(projectId,id).firstOrNull();require((old?.version ?: 0)==expectedVersion){"ARTIFACT_VERSION_CONFLICT"}
                require(old==null||old.name==spec.name&&old.format==spec.format){"ARTIFACT_RENAME_OR_FORMAT_CHANGE_UNSUPPORTED"}
                val version=expectedVersion+1;require(version<=1000){"ARTIFACT_REVISION_LIMIT"}
                val pending=File(folder,".pending-${UUID.randomUUID()}").apply{check(mkdir())}
                try {
                    val bytes=File(pending,spec.name);renderer.render(spec,bytes)
                    // Fully read ZIP entries for CRC and bounded safe package validation.
                    if(spec.format in setOf(ArtifactFormat.DOCX,ArtifactFormat.XLSX,ArtifactFormat.PPTX))ZipFile(bytes).use{zip->StructuredDocuments.inspect(zip);zip.entries().asSequence().forEach{e->zip.getInputStream(e).use{StructuredDocuments.bounded(it,8*1024*1024)}}}
                    val specPayload=spec.json().toString()
                    val revision=ArtifactRevision(id,version,spec.name,spec.format,bytes.length(),fileHash(bytes),System.currentTimeMillis(),hash(specPayload.toByteArray(Charsets.UTF_8)))
                    FileOutputStream(bytes,true).use{it.fd.sync()}
                    syncText(File(pending,"spec.json"),specPayload)
                    syncText(File(pending,"revision.json"),revision.json().toString())
                    check(!File(folder,"v$version").exists()&&pending.renameTo(File(folder,"v$version"))){"ARTIFACT_ATOMIC_COMMIT_FAILED"}
                    verifiedFile(projectId,revision);return revision
                }finally {if(pending.exists())pending.deleteRecursively() /* only this uncommitted app-owned attempt */}
            }
        }
    }
    private fun syncText(file:File,value:String){FileOutputStream(file).use{it.write(value.toByteArray(Charsets.UTF_8));it.fd.sync()}}
    private fun patchFolder(projectId:String):File {
        val owner=project(projectId)
        return File(owner,".patch-previews").also { require(it.canonicalFile.parentFile==owner.canonicalFile) { "ARTIFACT_PATH_ESCAPE" };check(it.mkdirs()||it.isDirectory) { "ARTIFACT_STORAGE_UNAVAILABLE" } }
    }
    private fun patchFile(projectId:String,previewId:String):File {
        require(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}").matches(previewId)) { "CODE_PATCH_ID_INVALID" }
        val parent=patchFolder(projectId)
        return File(parent,"$previewId.json").also { require(it.canonicalFile.parentFile==parent.canonicalFile) { "ARTIFACT_PATH_ESCAPE" } }
    }
    companion object {
        private const val MAX_PENDING_PATCHES=20
        private const val MAX_PATCH_BYTES=600*1024
        fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        fun fileHash(file:File):String {val digest=MessageDigest.getInstance("SHA-256");file.inputStream().use{input->val buffer=ByteArray(65536);while(true){if(Thread.currentThread().isInterrupted)throw InterruptedException();val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}};return digest.digest().joinToString(""){"%02x".format(it)}}
    }
}
