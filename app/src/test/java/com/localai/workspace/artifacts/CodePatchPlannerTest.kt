package com.localai.workspace.artifacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodePatchPlannerTest {
    @Test fun exactMultiEditPreservesUnicodeAndLineEndings() {
        val source="// España 界\r\nfun first() = 1\r\nfun second() = 2\r\n"
        val result=CodePatchPlanner.apply(source,CodePatchPlanner.hash(source),listOf(
            CodePatchEdit("fun first() = 1","fun first() = 10"),
            CodePatchEdit("fun second() = 2","fun second() = 20"),
        ))
        assertEquals("// España 界\r\nfun first() = 10\r\nfun second() = 20\r\n",result)
    }

    @Test fun staleMissingAmbiguousEmptyAndNoOpEditsAreRejected() {
        val source="one\ntwo\ntwo\n"
        val hash=CodePatchPlanner.hash(source)
        assertRejected("CODE_PATCH_BASE_HASH_MISMATCH") { CodePatchPlanner.apply(source,"0".repeat(64),listOf(CodePatchEdit("one","ONE"))) }
        assertRejected("CODE_PATCH_MATCH_NOT_FOUND") { CodePatchPlanner.apply(source,hash,listOf(CodePatchEdit("absent","value"))) }
        assertRejected("CODE_PATCH_MATCH_AMBIGUOUS") { CodePatchPlanner.apply(source,hash,listOf(CodePatchEdit("two","TWO"))) }
        assertRejected("CODE_PATCH_EMPTY_MATCH") { CodePatchPlanner.apply(source,hash,listOf(CodePatchEdit("","insert"))) }
        assertRejected("CODE_PATCH_NO_CHANGE") { CodePatchPlanner.apply(source,hash,listOf(CodePatchEdit("one","one"))) }
    }

    @Test fun editAndBodyBoundsAreEnforced() {
        val source="x".repeat(CodePatchPlanner.MAX_BODY_BYTES+1)
        assertRejected("CODE_PATCH_BODY_TOO_LARGE") { CodePatchPlanner.apply(source,CodePatchPlanner.hash(source),listOf(CodePatchEdit("x","y"))) }
        assertRejected("CODE_PATCH_EDIT_COUNT_INVALID") { CodePatchPlanner.apply("x",CodePatchPlanner.hash("x"),emptyList()) }
        assertRejected("CODE_PATCH_EDIT_COUNT_INVALID") { CodePatchPlanner.validateEdits(List(CodePatchPlanner.MAX_EDITS+1){CodePatchEdit("x","y")}) }
        assertRejected("CODE_PATCH_SNIPPET_TOO_LARGE") { CodePatchPlanner.validateEdits(listOf(CodePatchEdit("x".repeat(CodePatchPlanner.MAX_SNIPPET_BYTES+1),"y"))) }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",CodePatchPlanner.hash("abc"))
        assertTrue(CodePatchPlanner.hash("é").matches(Regex("[0-9a-f]{64}")))
    }

    private fun assertRejected(message:String,block:()->Unit) {
        try { block(); throw AssertionError("Expected $message") }
        catch(error:IllegalArgumentException) { assertEquals(message,error.message) }
    }
}
