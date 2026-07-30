package com.composerai.api.adapters.out.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.composerai.api.service.email.HtmlConverter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MarkdownStreamAssemblerTest {

    @Test
    void markdownRenderingEscapesHtml() {
        String html = HtmlConverter.markdownToSafeHtml("Hello <script>alert('x')</script> world");
        assertNotNull(html);
        assertFalse(html.contains("<script"));
        assertTrue(html.contains("alert"));
    }

    @Test
    void assemblerFlushesOnDoubleNewlineOutsideCodeFence() {
        MarkdownStreamAssembler assembler = new MarkdownStreamAssembler(false);

        List<String> firstChunk = assembler.onDelta("First paragraph.\n\nSecond paragraph start");
        assertEquals(1, firstChunk.size());
        assertTrue(firstChunk.getFirst().contains("First paragraph."));
        assertTrue(assembler.onDelta(" continues.").isEmpty());

        Optional<String> remainder = assembler.flushRemainder();
        assertTrue(remainder.isPresent());
        assertTrue(remainder.orElseThrow().contains("Second paragraph start continues."));
    }

    @Test
    void assemblerDefersFlushInsideCodeFence() {
        MarkdownStreamAssembler assembler = new MarkdownStreamAssembler(false);

        assertTrue(assembler.onDelta("```java\nSystem.out.println(\"hi\");\n").isEmpty());
        List<String> flushedChunks = assembler.onDelta("```\n\n");

        assertEquals(1, flushedChunks.size());
        assertTrue(flushedChunks.getFirst().contains("<pre><code"));
        assertTrue(assembler.flushRemainder().isEmpty());
    }

    @Test
    void assemblerRendersMarkdownTablesToHtml() {
        MarkdownStreamAssembler assembler = new MarkdownStreamAssembler(false);

        List<String> chunks = assembler.onDelta("| Col A | Col B |\n| --- | --- |\n| 1 | 2 |\n\n");

        assertEquals(1, chunks.size());
        assertTrue(chunks.getFirst().contains("<table"));
        assertTrue(chunks.getFirst().contains("<td"));
    }
}
