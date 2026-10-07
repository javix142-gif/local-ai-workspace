package com.localai.workspace.tools

import com.localai.workspace.domain.tools.LocalTool
import com.localai.workspace.domain.tools.SafeArithmeticEvaluator
import com.localai.workspace.domain.tools.ToolContext
import com.localai.workspace.domain.tools.ToolExecutionResult
import com.localai.workspace.domain.tools.ToolRegistry
import com.localai.workspace.security.ConfirmationPolicy
import com.localai.workspace.security.ToolDescriptor
import com.localai.workspace.security.ToolPermission
import com.localai.workspace.security.ToolRequest
import com.localai.workspace.security.TrustOrigin
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ToolFrameworkTest {
    @Test
    fun calculatorUsesAParserAndNeverEvaluatesArbitraryCode() = runBlocking {
        val registry = ToolRegistry(listOf(EchoTool))
        val result = registry.execute(
            request = ToolRequest("test.echo", "{}", TrustOrigin.MODEL),
            enabledToolIds = setOf("test.echo"),
            context = ToolContext("project", "conversation", File("/tmp")),
            state = ToolRegistry.ToolLoopState(),
        )
        assertTrue(result is ToolExecutionResult.Success)
        assertEquals("ok", (result as ToolExecutionResult.Success).output)
    }

    @Test
    fun malformedCalculatorInputIsRejectedBeforeExecution() = runBlocking {
        val registry = ToolRegistry(listOf(EchoTool))
        val result = registry.execute(
            ToolRequest("test.echo", "{not json", TrustOrigin.MODEL),
            setOf("test.echo"),
            ToolContext("project", "conversation", File("/tmp")),
            ToolRegistry.ToolLoopState(),
        )
        assertTrue(result is ToolExecutionResult.Rejected)
    }

    @Test
    fun arithmeticParserIsBoundedAndDeterministic() {
        assertEquals(14.0, SafeArithmeticEvaluator.evaluate("2 * (3 + 4)"), 0.0)
    }

    private object EchoTool : LocalTool {
        override val descriptor = ToolDescriptor("test.echo", setOf(ToolPermission.READ_LOCAL), ConfirmationPolicy.NEVER)
        override fun validate(arguments: JSONObject): String? = null
        override suspend fun execute(arguments: JSONObject, context: ToolContext): String = "ok"
    }
}
