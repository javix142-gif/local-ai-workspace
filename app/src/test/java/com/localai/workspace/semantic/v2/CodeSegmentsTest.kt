package com.localai.workspace.semantic.v2
import org.junit.Assert.*
import org.junit.Test
class CodeSegmentsTest {
 @Test fun preservesLinesAndRelativePositions(){val chunks=CodeSegments.chunk("fun one() {}\nfun two() {}\nfun three() {}",25);assertEquals(1,chunks.first().lineStart);assertEquals(3,chunks.last().lineEnd);assertEquals("fun one() {}\nfun two() {}",chunks.first().text)}
 @Test fun retainsLeadingBlankLineProvenance(){val c=CodeSegments.chunk("\nfun one() {}",100).single();assertEquals(1,c.lineStart);assertEquals(2,c.lineEnd);assertTrue(c.text.startsWith("\n"))}
 @Test fun oversizedSingleLineIsExplicitFailure(){assertThrows(IllegalArgumentException::class.java){CodeSegments.chunk("x".repeat(100),10)}}
}
