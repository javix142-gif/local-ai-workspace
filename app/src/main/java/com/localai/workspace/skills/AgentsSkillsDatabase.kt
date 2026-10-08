package com.localai.workspace.skills

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="skills") data class SkillRecord(@PrimaryKey val id:String,val definitionJson:String)
@Entity(tableName="agents") data class AgentRecord(@PrimaryKey val id:String,val definitionJson:String)
@Entity(tableName="project_agent_preferences") data class ProjectAgentPreference(@PrimaryKey val projectId:String,val preferredAgentId:String?)
@Dao interface AgentsSkillsDao {
    @Query("SELECT * FROM skills ORDER BY id") fun skills():Flow<List<SkillRecord>>
    @Query("SELECT * FROM skills ORDER BY id") suspend fun skillList():List<SkillRecord>
    @Query("SELECT * FROM skills WHERE id=:id") suspend fun skill(id:String):SkillRecord?
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun seedSkill(record:SkillRecord)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putSkill(record:SkillRecord)
    @Query("DELETE FROM skills WHERE id=:id") suspend fun deleteSkill(id:String)
    @Query("SELECT * FROM agents ORDER BY id") fun agents():Flow<List<AgentRecord>>
    @Query("SELECT * FROM agents ORDER BY id") suspend fun agentList():List<AgentRecord>
    @Query("SELECT * FROM agents WHERE id=:id") suspend fun agent(id:String):AgentRecord?
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun seedAgent(record:AgentRecord)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putAgent(record:AgentRecord)
    @Query("DELETE FROM agents WHERE id=:id") suspend fun deleteAgent(id:String)
    @Query("SELECT preferredAgentId FROM project_agent_preferences WHERE projectId=:projectId") suspend fun preferred(projectId:String):String?
    @Query("SELECT * FROM project_agent_preferences WHERE projectId=:projectId") fun observePreference(projectId:String):Flow<ProjectAgentPreference?>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun preference(record:ProjectAgentPreference)
}
@Database(entities=[SkillRecord::class,AgentRecord::class,ProjectAgentPreference::class],version=1,exportSchema=true)
abstract class AgentsSkillsDatabase:RoomDatabase() {
    abstract fun dao():AgentsSkillsDao
    companion object {fun create(context:Context,name:String="agents_skills.db")=Room.databaseBuilder(context,AgentsSkillsDatabase::class.java,name).build()}
}
