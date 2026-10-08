package com.localai.workspace.context

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="memories",indices=[Index(value=["scopeType","scopeId"]),Index("status"),Index("contentHash")])
data class MemoryRecord(@PrimaryKey val id:String,val kind:String,val scopeType:String,val scopeId:String,val title:String?,val text:String,val status:String="ACTIVE",val confidence:Double=1.0,val importance:Double=.5,val pinned:Boolean=false,val createdAt:Long,val updatedAt:Long,val lastAccessedAt:Long=0,val expiresAt:Long?=null,val sourceType:String="USER_EXPLICIT",val sourceId:String?=null,val sourceSegmentId:String?=null,val sourceMessageId:String?=null,val contentHash:String,val version:Int=1,val supersedesId:String?=null,val indexState:String="INDEX_PENDING",val structuredPayloadJson:String?=null,val subject:String?=null,val predicate:String?=null,val objectValue:String?=null)
@Entity(tableName="context_vectors",primaryKeys=["originId","spaceKey"])
data class ContextVector(val originId:String,val spaceKey:String,val contentHash:String,val dimension:Int,val bytes:ByteArray,val indexedAt:Long)
@Entity(tableName="conversation_sources",indices=[Index(value=["scopeType","scopeId"])])
data class ConversationSource(@PrimaryKey val messageId:String,val conversationId:String,val scopeType:String,val scopeId:String,val role:String,val text:String,val createdAt:Long,val contentHash:String,val indexState:String="INDEX_PENDING")
@Entity(tableName="project_briefs")
data class ProjectBrief(@PrimaryKey val projectId:String,val projectName:String,val goal:String="",val currentState:String="",val constraints:String="",val keyDecisions:String="",val activeTasks:String="",val architecture:String="",val updatedAt:Long=0) {
    fun markdown()="# $projectName\nGoal: $goal\nState: $currentState\nConstraints: $constraints\nDecisions: $keyDecisions\nTasks: $activeTasks\nArchitecture: $architecture"
}
@Dao interface MemoryContextDao {
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC") fun observe():Flow<List<MemoryRecord>>
    @Query("SELECT COUNT(*) FROM memories WHERE sourceType='APPROVED_LEGACY' AND sourceId=:id") suspend fun importedLegacy(id:String):Int
    @Query("SELECT * FROM memories WHERE id=:id") suspend fun get(id:String):MemoryRecord?
    @Query("SELECT * FROM memories WHERE status='ACTIVE' AND (expiresAt IS NULL OR expiresAt>:now) AND (scopeType||':'||scopeId) IN (:scopes) LIMIT 2000") suspend fun active(scopes:List<String>,now:Long):List<MemoryRecord>
    @Query("SELECT * FROM memories WHERE status='ACTIVE' AND indexState='INDEX_PENDING' LIMIT 100") suspend fun pending():List<MemoryRecord>
    @Query("UPDATE memories SET status='EXPIRED',indexState='NOT_INDEXED' WHERE status='ACTIVE' AND expiresAt IS NOT NULL AND expiresAt<=:now") suspend fun expire(now:Long)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(value:MemoryRecord)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun vector(value:ContextVector)
    @Query("SELECT * FROM context_vectors WHERE originId=:id AND spaceKey=:space AND contentHash=:hash") suspend fun vector(id:String,space:String,hash:String):ContextVector?
    @Query("DELETE FROM context_vectors WHERE originId=:id") suspend fun deleteVectors(id:String)
    @Query("DELETE FROM context_vectors WHERE originId IN (SELECT id FROM memories WHERE status!='ACTIVE')") suspend fun cleanupVectors()
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun conversation(value:ConversationSource)
    @Query("SELECT * FROM conversation_sources WHERE (scopeType||':'||scopeId) IN (:scopes) ORDER BY createdAt DESC LIMIT 2000") suspend fun conversations(scopes:List<String>):List<ConversationSource>
    @Query("DELETE FROM conversation_sources WHERE conversationId=:id") suspend fun deleteConversation(id:String)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun brief(value:ProjectBrief)
    @Query("SELECT * FROM project_briefs WHERE projectId=:id") suspend fun brief(id:String):ProjectBrief?
}
@Database(entities=[MemoryRecord::class,ContextVector::class,ConversationSource::class,ProjectBrief::class],version=1,exportSchema=true)
abstract class MemoryContextDatabase:RoomDatabase(){abstract fun dao():MemoryContextDao
    companion object{fun create(context:Context,name:String="memory_context.db")=Room.databaseBuilder(context,MemoryContextDatabase::class.java,name).build()}
}
