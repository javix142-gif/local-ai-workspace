package com.localai.workspace.ui

import org.junit.Assert.*
import org.junit.Test

class BasicMarkdownTest {
    @Test fun paragraphsListsAndHeadings() {
        val blocks = BasicMarkdown.blocks("# Title\n\nFirst paragraph.\nSecond line.\n\n- One\n2. Two")
        assertEquals(listOf(MarkdownBlock.Heading(1, "Title"), MarkdownBlock.Paragraph("First paragraph.\nSecond line."),
            MarkdownBlock.ListItem("•", "One"), MarkdownBlock.ListItem("2.", "Two")), blocks)
    }
    @Test fun headingLevelsArePreservedForPresentation() {
        assertEquals(
            listOf(
                MarkdownBlock.Heading(1, "Document"),
                MarkdownBlock.Heading(3, "Section"),
                MarkdownBlock.Heading(6, "Detail"),
            ),
            BasicMarkdown.blocks("# Document\n### Section\n###### Detail"),
        )
    }
    @Test fun fencedCodePreservesIndentationAndDoesNotParseMarkdownInside() {
        assertEquals(listOf(MarkdownBlock.Code("python", "  print('**literal**')\n")),
            BasicMarkdown.blocks("```python\n  print('**literal**')\n\n```"))
    }
    @Test fun streamingUnclosedFenceRemainsReadable() {
        assertEquals(listOf(MarkdownBlock.Code("kotlin", "val answer = 42")), BasicMarkdown.blocks("```kotlin\nval answer = 42"))
    }
    @Test fun boldAndInlineCodeAreSeparate() {
        assertEquals(listOf(InlineRun("Use "), InlineRun("bold", bold = true), InlineRun(" and "), InlineRun("x*y", code = true), InlineRun(".")),
            BasicMarkdown.inline("Use **bold** and `x*y`."))
    }
    @Test fun malformedAndEscapedMarkupKeepsVisibleText() {
        assertEquals(listOf(InlineRun("**unfinished `code")), BasicMarkdown.inline("**unfinished `code"))
        assertEquals(listOf(InlineRun("**literal**")), BasicMarkdown.inline("\\*\\*literal\\*\\*"))
    }
    @Test fun htmlAndRemoteImageStayLiteral() {
        val source = "<script>alert(1)</script> ![private](https://example.org/a.png)"
        assertEquals(listOf(MarkdownBlock.Paragraph(source)), BasicMarkdown.blocks(source))
    }
    @Test fun linksAndTableSyntaxRemainLiteralBecauseTheyAreNotRenderedFormats() {
        val source = "[Source](https://example.org)\n| A | B |\n|---|---|\n| 1 | 2 |"
        assertEquals(listOf(MarkdownBlock.Paragraph(source)), BasicMarkdown.blocks(source))
        assertEquals(listOf(InlineRun(source)), BasicMarkdown.inline(source))
    }
    @Test fun unknownFenceLanguageAndTildeFenceAreSafe() {
        assertEquals(listOf(MarkdownBlock.Code(null, "code")), BasicMarkdown.blocks("~~~not a language\ncode\n~~~"))
    }
}
