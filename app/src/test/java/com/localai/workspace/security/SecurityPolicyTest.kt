package com.localai.workspace.security

import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityPolicyTest {
    private val registry = ToolPolicy(
        mapOf(
            "files.read" to ToolDescriptor("files.read", setOf(ToolPermission.READ_LOCAL), ConfirmationPolicy.NEVER),
            "calendar.create" to ToolDescriptor("calendar.create", setOf(ToolPermission.EXTERNAL_SIDE_EFFECT), ConfirmationPolicy.REQUIRED_FOR_WRITE),
        ),
    )

    @Test
    fun untrustedContentCannotAuthorizeEvenAnEnabledTool() {
        val decision = registry.decide(
            ToolRequest("files.read", "{}", TrustOrigin.UNTRUSTED_EXTERNAL),
            setOf("files.read"),
        )
        assertTrue(decision is ToolDecision.Deny)
    }

    @Test
    fun writeToolRequiresNativeConfirmationBoundary() {
        val decision = registry.decide(
            ToolRequest("calendar.create", "{}", TrustOrigin.MODEL),
            setOf("calendar.create"),
        )
        assertTrue(decision is ToolDecision.NeedsConfirmation)
    }

    @Test
    fun pathAndUrlAllowListsRejectEscapesAndDangerousSchemes() {
        assertTrue(InputSafety.isSafeRelativePath("reports/result.csv"))
        assertTrue(!InputSafety.isSafeRelativePath("../secrets.txt"))
        assertTrue(!InputSafety.isSafeRelativePath("/absolute/path"))
        assertTrue(InputSafety.isSafeHttpUrl("https://example.com/a"))
        assertTrue(!InputSafety.isSafeHttpUrl("file:///etc/passwd"))
        assertTrue(!InputSafety.isSafeHttpUrl("intent://settings"))
    }
}
