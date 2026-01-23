package com.composerai.api.service;

import com.composerai.api.service.email.HtmlConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Assembler for streaming markdown content.
 * Handles incremental parsing of markdown tokens, ensuring safe HTML rendering
 * while preserving code fence integrity during streaming.
 */
final class MarkdownStreamAssembler {
    private static final Logger logger = LoggerFactory.getLogger(MarkdownStreamAssembler.class);
    
    private final boolean debugEnabled;
    private final StringBuilder buffer = new StringBuilder();
    private final StringBuilder lineBuffer = new StringBuilder();
    private boolean insideCodeFence = false;
    private char fenceDelimiter = '`';
    private static final int CHUNK_THRESHOLD = 1536;

    MarkdownStreamAssembler(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    List<String> onDelta(String delta) {
        if (delta == null || delta.isEmpty()) return Collections.emptyList();

        List<String> flushed = null;
        for (int i = 0; i < delta.length(); i++) {
            char ch = delta.charAt(i);
            if (ch == '\r') continue;

            buffer.append(ch);
            if (ch == '\n') {
                String trimmedLine = lineBuffer.toString().trim();
                updateFenceState(trimmedLine);
                if (!insideCodeFence) {
                    if (trimmedLine.isEmpty()) {
                        flushed = addChunk(flushed, flushBuffer());
                    } else {
                        flushed = addChunk(flushed, flushToParagraphBreakIfNeeded());
                    }
                }
                lineBuffer.setLength(0);
            } else {
                lineBuffer.append(ch);
                if (!insideCodeFence) {
                    flushed = addChunk(flushed, flushToParagraphBreakIfNeeded());
                }
            }
        }
        if (debugEnabled && logger.isDebugEnabled() && flushed != null) {
            for (String chunk : flushed) {
                logger.debug("MarkdownAssembler flush chunk ({} chars): {}", chunk != null ? chunk.length() : 0, preview(chunk));
            }
        }
        return flushed == null ? Collections.emptyList() : flushed;
    }

    private List<String> addChunk(List<String> list, String chunk) {
        if (chunk == null) return list;
        if (list == null) list = new ArrayList<>();
        list.add(chunk);
        return list;
    }

    Optional<String> flushRemainder() {
        if (buffer.isEmpty()) return Optional.empty();
        String markdown = buffer.toString();
        buffer.setLength(0);
        resetLineBuffer();
        String chunk = renderMarkdown(markdown);
        insideCodeFence = false;
        if (debugEnabled && logger.isDebugEnabled()) {
            logger.debug("MarkdownAssembler flush remainder ({} chars): {}", chunk != null ? chunk.length() : 0, preview(chunk));
        }
        return chunk == null || chunk.isBlank() ? Optional.empty() : Optional.of(chunk);
    }

    private void updateFenceState(String trimmedLine) {
        if (trimmedLine.isEmpty()) return;
        if (trimmedLine.startsWith("```") || trimmedLine.startsWith("~~~")) {
            char delimiter = trimmedLine.charAt(0);
            if (!insideCodeFence) {
                insideCodeFence = true;
                fenceDelimiter = delimiter;
            } else if (fenceDelimiter == delimiter) {
                insideCodeFence = false;
            }
        }
    }

    private String flushBuffer() {
        if (buffer.isEmpty()) return null;
        String markdown = buffer.toString();
        buffer.setLength(0);
        String chunk = renderMarkdown(markdown);
        resetLineBuffer();
        return chunk;
    }

    private String flushToParagraphBreakIfNeeded() {
        if (buffer.length() < CHUNK_THRESHOLD) return null;
        int boundary = lastParagraphBoundary();
        if (boundary < 0) return null;
        String markdown = buffer.substring(0, boundary);
        buffer.delete(0, boundary);
        resetLineBuffer();
        return renderMarkdown(markdown);
    }

    private int lastParagraphBoundary() {
        int idx = buffer.lastIndexOf("\n\n");
        if (idx < 0) return -1;
        // include blank line in the flushed segment
        return idx + 2;
    }

    private String renderMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) return null;
        String rendered = HtmlConverter.markdownToSafeHtml(markdown);
        return rendered == null || rendered.isBlank() ? null : rendered;
    }

    private void resetLineBuffer() {
        lineBuffer.setLength(0);
        if (!buffer.isEmpty()) {
            int lastNewline = buffer.lastIndexOf("\n");
            if (lastNewline == -1) {
                lineBuffer.append(buffer);
            } else if (lastNewline + 1 < buffer.length()) {
                lineBuffer.append(buffer.substring(lastNewline + 1));
            }
        }
    }

    static String preview(String value) {
        if (value == null) return "<null>";
        String trimmed = value.replace("\n", "\\n");
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 117) + "...";
    }
}
