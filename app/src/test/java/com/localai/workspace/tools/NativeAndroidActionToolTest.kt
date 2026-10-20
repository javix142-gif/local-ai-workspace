package com.localai.workspace.tools

import android.app.Application
import android.os.Bundle
import com.google.ai.edge.litertlm.ToolManager
import com.google.gson.JsonParser
import com.localai.workspace.data.ConversationEntity
import com.localai.workspace.data.ProjectEntity
import com.localai.workspace.data.WorkspaceDatabase
import com.localai.workspace.data.ToolCallEntity
import com.localai.workspace.inference.NativeChatTools
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class NativeAndroidActionToolTest {
    @Test fun productionSdkAdapterPersistsAUserReviewProposalWithoutOpeningAnIntent() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = WorkspaceDatabase.createValidation(context)
        val project = "self-test-android-action-${UUID.randomUUID()}"
        val conversation = UUID.randomUUID().toString()
        db.projectDao().upsert(ProjectEntity(project, "Synthetic action scope", 1, 1))
        db.conversationDao().upsert(ConversationEntity(conversation, project, "Synthetic", 1, 1))
        val adapter = NativeChatTools(context)
        val data = Bundle().apply {
            putBoolean("validationScope", true)
            putString("projectId", project)
            putString("conversationId", conversation)
            putStringArrayList("enabledTools", arrayListOf("android.action.prepare"))
        }
        try {
            adapter.begin(data)
            val manager = ToolManager(adapter.providers(data))
            val args = """{"action":"sms_draft","phoneNumber":"+12025550100","body":"Draft only"}"""
            val response = JSONObject(manager.execute("android.action.prepare", JsonParser.parseString(args).asJsonObject).asString)

            assertEquals("CONFIRMATION_REQUIRED", response.getString("status"))
            assertTrue(response.getBoolean("confirmationRequired"))
            assertFalse(response.getBoolean("executed"))
            assertTrue(response.getString("message").contains("no Android action has been opened"))

            val call: ToolCallEntity = db.toolCallDao().observeForConversation(conversation).first().single()
            assertEquals("android.action.prepare", call.toolId)
            assertEquals("EXTERNAL_SEND_REQUIRES_CONFIRMATION", call.permissionDecision)
            assertEquals("CONFIRMATION_REQUIRED", call.status)
            assertEquals("+12025550100", JSONObject(call.validatedArgumentsJson).getString("phoneNumber"))
        } finally {
            adapter.close()
            db.toolCallDao().deleteForConversation(conversation)
            db.conversationDao().delete(conversation)
            db.projectDao().delete(project)
            db.close()
        }
    }
}
