package com.localai.workspace.artifacts

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile
import com.localai.workspace.documents.StructuredDocuments

data class ArtifactRevision(val id:String,val version:Int,val name:String,val format:ArtifactFormat,val bytes:Long,val sha256:String,val createdAt:Long,val specSha256:String) {
    fun json()=JSONObject().put("id",id).put("version",version).put("name",name).put("format",format.name).put("bytes",bytes).put("sha256",sha256).put("createdAt",createdAt).put("specSha256",specSha256)
}

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
    }.sortedByDescending{it.createdAt}
    fun history(projectId:String,id:String):List<ArtifactRevision> = item(projectId,id).listFiles().orEmpty().filter{it.isDirectory&&Regex("v[1-9][0-9]*").matches(it.name)}.map{folder->
        val meta=File(folder,"revision.json");require(meta.length() in 1..4096){"ARTIFACT_METADATA_INVALID"};val j=JSONObject(meta.readText())
        ArtifactRevision(j.getString("id"),j.getInt("version"),j.getString("name"),ArtifactFormat.valueOf(j.getString("format")),j.getLong("bytes"),j.getString("sha256"),j.getLong("createdAt"),j.getString("specSha256")).also{
            require(it.id==id&&folder.name=="v${it.version}"&&it.bytes in 1..20L*1024*1024&&Regex("[0-9a-f]{64}").matches(it.sha256)&&Regex("[0-9a-f]{64}").matches(it.specSha256)){"ARTIFACT_METADATA_INVALID"}
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
    fun create(projectId:String,spec:ArtifactSpec)=commit(projectId,UUID.randomUUID().toString(),0,spec)
    fun revise(projectId:String,id:String,expectedVersion:Int,spec:ArtifactSpec)=commit(projectId,id,expectedVersion,spec)
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
    companion object {
        fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        fun fileHash(file:File):String {val digest=MessageDigest.getInstance("SHA-256");file.inputStream().use{input->val buffer=ByteArray(65536);while(true){if(Thread.currentThread().isInterrupted)throw InterruptedException();val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}};return digest.digest().joinToString(""){"%02x".format(it)}}
    }
}
