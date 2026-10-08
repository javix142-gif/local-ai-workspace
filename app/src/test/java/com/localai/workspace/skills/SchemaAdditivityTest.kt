package com.localai.workspace.skills

import android.app.Application
import com.google.gson.JsonParser
import com.localai.workspace.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=Application::class)
class SchemaAdditivityTest {
    @Test fun schemasExportActualVersionsAndExistingWorkspaceDataSurvivesSidecar()=runBlocking {
        for((type,version) in listOf("data.WorkspaceDatabase" to 8,"context.MemoryContextDatabase" to 1,"semantic.v2.SemanticDatabase" to 1,"skills.AgentsSkillsDatabase" to 1)) {
            val schema=JsonParser.parseReader(File("schemas/com.localai.workspace.$type/$version.json").reader()).asJsonObject["database"].asJsonObject
            assertEquals(version,schema["version"].asInt);assertTrue(schema["entities"].asJsonArray.size()>0)
        }
        val ctx=RuntimeEnvironment.getApplication();ctx.deleteDatabase("workspace.db")
        val before=WorkspaceDatabase.create(ctx)
        before.projectDao().upsert(ProjectEntity("p","Preserved",1,1));before.conversationDao().upsert(ConversationEntity("c","p","Preserved chat",1,1));before.messageDao().insert(MessageEntity("m","c","USER","Original",1))
        before.modelDao().insert(ModelEntity("model","Model","/private/model",fileHash="hash",fileSize=1,format="LITERT_LM",runtimeId="litert-lm-android",compatibilityStatus="COMPATIBLE",importedAt=1))
        before.semanticVectorDao().put(SemanticVectorEntity("D:1","legacy",sourceHash="hash",dimensions=768,version=1,vector=ByteArray(3072),updatedAt=1))
        val oldIdentity=before.openHelper.readableDatabase.query("SELECT identity_hash FROM room_master_table WHERE id=42").use{it.moveToFirst();it.getString(0)};before.close()
        val sidecar=AgentsSkillsDatabase.create(ctx,"additive-skills.db");SkillRegistry(sidecar).initialize();sidecar.close()
        val after=WorkspaceDatabase.create(ctx)
        try {
            assertEquals("Preserved",after.projectDao().get("p")!!.name);assertEquals("Preserved chat",after.conversationDao().get("c")!!.title)
            assertEquals("Original",after.messageDao().recent("c",10).single().content);assertNotNull(after.modelDao().get("model"));assertNotNull(after.semanticVectorDao().get("D:1","legacy"))
            val current=after.openHelper.readableDatabase.query("SELECT identity_hash FROM room_master_table WHERE id=42").use{it.moveToFirst();it.getString(0)}
            assertEquals(oldIdentity,current)
        }finally{after.close();ctx.deleteDatabase("workspace.db");ctx.deleteDatabase("additive-skills.db")}
    }
}
