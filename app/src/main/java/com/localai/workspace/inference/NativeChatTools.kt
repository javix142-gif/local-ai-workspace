package com.localai.workspace.inference

import androidx.room.withTransaction
import android.content.Context
import android.os.Bundle
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import com.localai.workspace.data.*
import com.localai.workspace.domain.tools.*
import com.localai.workspace.security.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Uses SDK automatic tool calling; results are returned through OpenApiTool's official contract. */
internal class NativeChatTools(private val context: Context) : AutoCloseable {
    private val database = lazy { WorkspaceDatabase.create(context) }
    private var validationDatabase: WorkspaceDatabase? = null
    private val db get() = if(request.getBoolean("validationScope")) validationDatabase ?: WorkspaceDatabase.createValidation(context).also { validationDatabase=it } else database.value
    private val directory = File(context.filesDir, "tool-workspaces")
    private fun registryFor(db: WorkspaceDatabase) = ToolRegistry(listOf(CalculatorTool(), ProjectFileReadTool(db, File(context.filesDir,"documents")), ProjectFileListTool(db), com.localai.workspace.python.PythonTool(context)))
    private val normalRegistry by lazy { registryFor(database.value) }
    private var validationRegistry: ToolRegistry? = null
    private val registry get() = if(request.getBoolean("validationScope")) validationRegistry ?: registryFor(db).also { validationRegistry=it } else normalRegistry
    private var state = ToolRegistry.ToolLoopState()
    private var request = Bundle()
    var notify: ((String, String, String) -> Unit)? = null
    fun begin(data: Bundle) {
        require(!data.getBoolean("validationScope") || data.getString("projectId")?.startsWith("self-test-")==true) { "Invalid self-test scope" }
        state = ToolRegistry.ToolLoopState(); request = Bundle(data)
    }
    fun providers(data: Bundle): List<ToolProvider> = (data.getStringArrayList("enabledTools") ?: emptyList()).map { id ->
        require(id in ChatToolSchemas.available) { "Tool unavailable" }
        tool(object : OpenApiTool {
            override fun getToolDescriptionJsonString() = ChatToolSchemas.schema(id)
            override fun execute(paramsJsonString: String): String = runBlocking(Dispatchers.IO) {
                val project = requireNotNull(request.getString("projectId"))
                val conversation = requireNotNull(request.getString("conversationId"))
                val owner = db.conversationDao().get(conversation)
                require(owner?.projectId == project) { "Tool scope mismatch" }
                val callId = UUID.randomUUID().toString(); val start = System.currentTimeMillis()
                notify?.invoke(id, "RUNNING", callId)
                db.withTransaction {
                    require(db.conversationDao().get(conversation)?.projectId == project) { "Tool scope no longer exists" }
                    db.toolCallDao().insert(ToolCallEntity(callId, conversation, request.getString("messageId"), id, paramsJsonString.take(12000), "READ_LOCAL", status = "RUNNING", startedAt = start))
                }
                val result = registry.execute(ToolRequest(id, paramsJsonString, TrustOrigin.MODEL),
                    request.getStringArrayList("enabledTools").orEmpty().toSet(), ToolContext(project, conversation, directory), state)
                val status: String; val response = JSONObject()
                when(result) {
                    is ToolExecutionResult.Success -> { status = "SUCCESS"; response.put("result", try { JSONObject(result.output) } catch (_: org.json.JSONException) { JSONObject().put("value", result.output) }) }
                    is ToolExecutionResult.Failed -> { status = result.code; response.put("error", result.message) }
                    is ToolExecutionResult.Rejected -> { status = "REJECTED"; response.put("error", result.reason) }
                    is ToolExecutionResult.NeedsConfirmation -> { status = "CONFIRMATION_REQUIRED"; response.put("error", "Not executed") }
                }
                response.put("status", status)
                // Private Room data for explicit Details, never logcat/benchmark.
                db.withTransaction {
                    if (db.conversationDao().get(conversation)?.projectId == project) db.toolCallDao().insert(ToolCallEntity(callId, conversation, request.getString("messageId"), id,
                        paramsJsonString.take(12000), "READ_LOCAL", status = status, startedAt = start, finishedAt = System.currentTimeMillis(), errorCode = if(status == "SUCCESS") null else status, resultJson = response.toString().take(16000)))
                }
                notify?.invoke(id, status, callId)
                response.toString()
            }
        })
    }
    fun endValidationScope() { validationRegistry=null; validationDatabase?.close();validationDatabase=null }
    override fun close() { if (database.isInitialized()) database.value.close(); endValidationScope() }
}
