package com.localai.workspace.artifacts

import android.app.Application
import android.os.Bundle
import com.localai.workspace.data.*
import com.localai.workspace.inference.NativeChatTools
import com.google.ai.edge.litertlm.ToolManager
import com.google.gson.JsonParser
import org.json.JSONObject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID
import java.io.File

/** Actual production SDK adapter/Room scope/ToolRegistry/file bytes, not LLM inference. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=Application::class)
class NativeArtifactToolTest {
    @Test fun sdkAdapterCreatesActualDraftRecordsWritePermissionAndRejectsScopeMismatch()=runBlocking {
        val context=RuntimeEnvironment.getApplication<Application>();val db=WorkspaceDatabase.createValidation(context)
        val project="self-test-native-artifact-${UUID.randomUUID()}";val chat=UUID.randomUUID().toString()
        db.projectDao().upsert(ProjectEntity(project,"Synthetic tool validation",1,1))
        db.conversationDao().upsert(ConversationEntity(chat,project,"Synthetic",1,1))
        val adapter=NativeChatTools(context)
        val data=Bundle().apply{putBoolean("validationScope",true);putString("projectId",project);putString("conversationId",chat);putStringArrayList("enabledTools",arrayListOf("artifacts.create","artifacts.read"))}
        try {
            adapter.begin(data)
            val tools=ToolManager(adapter.providers(data))
            val args="""{"spec":{"format":"CODE","name":"answer.kt","body":"fun answer() = 42\n"}}"""
            val result=JSONObject(tools.execute("artifacts.create",JsonParser.parseString(args).asJsonObject).asString)
            assertEquals("SUCCESS",result.getString("status"));assertEquals("PRIVATE_DRAFT_VERIFIED",result.getJSONObject("result").getString("state"))
            val store=ArtifactStore(context);val revision=store.list(project).single();assertEquals("fun answer() = 42\n",store.verifiedFile(project,revision).readText())
            assertEquals("WRITE_LOCAL",db.toolCallDao().observeForConversation(chat).first().single().permissionDecision)
            assertEquals("SUCCESS",db.toolCallDao().observeForConversation(chat).first().single().status)
            val read=JSONObject(tools.execute("artifacts.read",JsonParser.parseString(JSONObject().put("id",revision.id).toString()).asJsonObject).asString)
            assertEquals("fun answer() = 42\n",read.getJSONObject("result").getJSONObject("spec").getString("body"))
            adapter.begin(Bundle(data).apply{putString("conversationId",UUID.randomUUID().toString())})
            try{tools.execute("artifacts.create",JsonParser.parseString(args).asJsonObject);fail("owner required")}catch(e:Exception){assertTrue(e.toString().contains("scope")||e.cause!=null)}
            assertEquals(1,store.list(project).size)
        }finally {
            adapter.close();db.toolCallDao().deleteForConversation(chat);db.conversationDao().delete(chat);db.projectDao().delete(project);db.close()
            File(context.filesDir,"tool-workspaces/artifacts-${ArtifactStore.hash(project.toByteArray())}").deleteRecursively()
        };Unit
    }
}
