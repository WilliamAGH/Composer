package com.composerai.api.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
// (no extra java.util.* imports needed)
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

// Import your existing parser classes
import com.composerai.api.service.ContextBuilder.EmailContextRegistry;
import com.composerai.api.service.HtmlToText;
import com.composerai.api.service.email.HtmlConverter;
import com.composerai.api.util.IdGenerator;
import com.composerai.api.util.TemporalUtils;
// (No direct parsing here; delegated to HtmlToText/EmailPipeline)
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller for handling email file uploads and parsing operations.
 * Provides endpoints for processing .eml files and extracting readable content.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EmailController {

    private static final DateTimeFormatter DATE_WITH_OFFSET_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a xxx", Locale.US);

    // Thin controller: delegates parsing to HtmlToText/EmailPipeline
    private final EmailContextRegistry emailContextRegistry;

    /**
     * Parse uploaded .eml email file and return extracted text content.
     * 
     * @param file The uploaded .eml file
     * @return JSON response containing parsed text or error message
     */
    @PostMapping(value = "/parse-email", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> parseEmail(@RequestParam("file") MultipartFile file) {

        // Validate file upload
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided. Please upload a valid .eml file.");
        }

        // Validate file type
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".eml") &&
                               !filename.toLowerCase().endsWith(".msg") &&
                               !filename.toLowerCase().endsWith(".txt"))) {
            throw new IllegalArgumentException("Invalid file type. Please upload a .eml, .msg, or .txt file.");
        }

        // Validate file size (e.g., max 10MB)
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("File too large. Maximum file size is 10MB.");
        }

        // Determine extension and preempt unsupported formats
        String lower = filename.toLowerCase();
        if (lower.endsWith(".msg")) {
            throw new UnsupportedOperationException(".msg (Outlook) files are not supported yet.");
        }

        try {
            Map<String, Object> response = new HashMap<>();

            // Persist upload to a temp file and delegate to HtmlToText/EmailPipeline for DRY parsing
            Path tempFile = Files.createTempFile("upload-", lower.endsWith(".eml") ? ".eml" : ".html");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            HtmlToText.Options options = new HtmlToText.Options();
            options.inputFile = tempFile.toString();
            options.inputType = lower.endsWith(".eml") ? "eml" : "html"; // treat .txt as html-ish for pipeline
            options.format = HtmlToText.OutputFormat.PLAIN;
            options.urlsPolicy = HtmlToText.UrlPolicy.CLEAN_ONLY;
            options.includeMetadata = true;
            options.jsonOutput = true; // request structured output with plain & markdown
            options.suppressUtility = true;

            String jsonPayload;
            try {
                jsonPayload = convertEmail(options);
            } finally {
                try { Files.deleteIfExists(tempFile); } catch (IOException ignore) { }
            }

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> parsedDocument = mapper.readValue(jsonPayload, new TypeReference<Map<String, Object>>() {});

            Map<String, Object> content = parsedDocument.containsKey("content")
                && parsedDocument.get("content") instanceof Map
                ? mapper.convertValue(parsedDocument.get("content"), new TypeReference<Map<String, Object>>() {})
                : Collections.emptyMap();

            String plainText = content.getOrDefault("plainText", "").toString();
            String markdown = content.getOrDefault("markdown", "").toString();

            // Extract email metadata from parsed document
            Map<String, Object> metadata = parsedDocument.containsKey("metadata")
                && parsedDocument.get("metadata") instanceof Map
                ? mapper.convertValue(parsedDocument.get("metadata"), new TypeReference<Map<String, Object>>() {})
                : Collections.emptyMap();
            
            String subject = firstNonBlank(metadata, "subject");
            if (subject == null) {
                subject = "No subject";
            }
            String from = firstNonBlank(metadata, "from");
            if (from == null) {
                from = "Unknown sender";
            }
            String date = firstNonBlank(metadata, "date", "dateHeader", "dateIso");
            if (date == null) {
                date = "Unknown date";
            }
            String dateIso = firstNonBlank(metadata, "dateIso", "date");
            String dateWithRelativeTime = enrichDateWithOffset(date, dateIso);

            // Build successful response (DRY: use parsedPlain as canonical plain text field)
            response.put("parsedPlain", plainText);
            response.put("parsedMarkdown", markdown);
            response.put("parsedHtml", HtmlConverter.markdownToSafeHtml(markdown.isBlank() ? plainText : markdown));

            // Backend determines best context format for AI (prefer markdown > plain)
            // Prepend email metadata with temporal context for AI awareness
            String emailBody = (!markdown.isBlank()) ? markdown : plainText;
            String contextForAI = buildEmailContextForAI(subject, from, dateWithRelativeTime, emailBody);
            String contextId = resolveContextId(metadata);
            emailContextRegistry.store(contextId, plainText, markdown);
            parsedDocument.put("contextId", contextId);
            response.put("contextForAI", contextForAI);
            response.put("contextId", contextId);

            response.put("document", parsedDocument);
            response.put("status", "success");
            response.put("filename", filename);
            response.put("fileSize", file.getSize());
            response.put("timestamp", System.currentTimeMillis());

            // Add email metadata for chat interface
            response.put("subject", subject);
            response.put("from", from);
            response.put("date", dateWithRelativeTime);
            if (dateIso != null) {
                response.put("dateIso", dateIso.trim());
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            // Rethrow validation errors as-is for proper error response
            throw e;
        } catch (UnsupportedOperationException e) {
            // Rethrow unsupported operations as-is
            throw e;
        } catch (Exception e) {
            // Provide more specific error message with cause
            String detailedMessage = "Failed to process email file";
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                detailedMessage += ": " + e.getMessage();
            }
            log.error("Email parsing failed for file: {}", filename, e);
            throw new RuntimeException(detailedMessage, e);
        }
    }
    // Intentionally no health/info endpoints here; SystemController exposes /api/health

    private static String enrichDateWithOffset(String currentDisplay, String isoCandidate) {
        String display = currentDisplay == null ? "Unknown date" : currentDisplay;
        if (displayHasOffset(display)) {
            return display;
        }

        if (isoCandidate == null || isoCandidate.isBlank()) {
            return display;
        }

        String trimmedIso = isoCandidate.trim();
        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(trimmedIso);
            String formattedDate = offsetDateTime.format(DATE_WITH_OFFSET_FORMATTER);

            // Enrich with relative time for better temporal awareness
            String relativeTime = TemporalUtils.getRelativeTime(offsetDateTime);
            if (relativeTime != null && !relativeTime.isBlank()) {
                return formattedDate + " (" + relativeTime + ")";
            }
            return formattedDate;
        } catch (DateTimeParseException ignored) {
            return display;
        }
    }

    private static boolean displayHasOffset(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.matches(".*[+-]\\d{2}:?\\d{2}.*");
    }

    private static String firstNonBlank(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            String text = value.toString();
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    /**
     * Builds email context for AI with metadata header including temporal awareness.
     * Prepends subject, sender, and timestamp (with relative time) to email body.
     *
     * @param subject email subject line
     * @param from sender address
     * @param dateWithRelativeTime formatted date with relative time (e.g., "Jan 15, 2024 at 3:45 PM PST (2 hours ago)")
     * @param emailBody the email content (markdown or plain text)
     * @return formatted context string with metadata header + body
     */
    private static String buildEmailContextForAI(String subject, String from, String dateWithRelativeTime, String emailBody) {
        StringBuilder context = new StringBuilder();

        // Metadata header with temporal context
        context.append("=== Email Metadata ===\n");
        context.append("Subject: ").append(subject != null ? subject : "No subject").append("\n");
        context.append("From: ").append(from != null ? from : "Unknown sender").append("\n");
        context.append("Date: ").append(dateWithRelativeTime != null ? dateWithRelativeTime : "Unknown date").append("\n");
        context.append("\n=== Email Body ===\n");

        // Email content
        if (emailBody != null && !emailBody.isBlank()) {
            context.append(emailBody);
        } else {
            context.append("(Email body is empty)");
        }

        return context.toString();
    }

    protected String convertEmail(HtmlToText.Options options) throws Exception {
        return HtmlToText.convert(options);
    }

    private String resolveContextId(Map<String, Object> metadata) {
        String messageId = firstNonBlank(metadata, "messageId", "id");
        if (messageId != null) {
            String normalized = messageId.replaceAll("[^A-Za-z0-9._:-]", "");
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return IdGenerator.uuidV7();
    }
}
