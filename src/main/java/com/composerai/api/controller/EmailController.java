package com.composerai.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
// (no extra java.util.* imports needed)
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

// Import your existing parser classes
import com.composerai.api.service.HtmlToText;
// (No direct parsing here; delegated to HtmlToText/EmailPipeline)
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * REST Controller for handling email file uploads and parsing operations.
 * Provides endpoints for processing .eml files and extracting readable content.
 */
@RestController
@CrossOrigin(origins = "*") // Allow frontend to call this API
@RequestMapping("/api")
public class EmailController {

    // Thin controller: delegates parsing to HtmlToText/EmailPipeline

    /**
     * Parse uploaded .eml email file and return extracted text content.
     * 
     * @param file The uploaded .eml file
     * @return JSON response containing parsed text or error message
     */
    @PostMapping(value = "/parse-email", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> parseEmail(@RequestParam("file") MultipartFile file) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Validate file upload
            if (file == null || file.isEmpty()) {
                response.put("error", "No file provided. Please upload a valid .eml file.");
                response.put("status", "error");
                response.put("code", 400);
                return ResponseEntity.badRequest().body(response);
            }

            // Validate file type
            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.toLowerCase().endsWith(".eml") && 
                                   !filename.toLowerCase().endsWith(".msg") && 
                                   !filename.toLowerCase().endsWith(".txt"))) {
                response.put("error", "Invalid file type. Please upload a .eml, .msg, or .txt file.");
                response.put("status", "error");
                response.put("code", 400);
                return ResponseEntity.badRequest().body(response);
            }

            // Validate file size (e.g., max 10MB)
            if (file.getSize() > 10 * 1024 * 1024) {
                response.put("error", "File too large. Maximum file size is 10MB.");
                response.put("status", "error");
                response.put("code", 400);
                return ResponseEntity.badRequest().body(response);
            }

            // Determine extension and preempt unsupported formats
            String lower = filename.toLowerCase();
            if (lower.endsWith(".msg")) {
                response.put("error", ".msg (Outlook) files are not supported yet.");
                response.put("status", "error");
                response.put("code", 415);
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
            }

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
                jsonPayload = HtmlToText.convert(options);
            } finally {
                try { Files.deleteIfExists(tempFile); } catch (IOException ignore) { }
            }

            Map<String, Object> parsedDocument;
            try {
                parsedDocument = new ObjectMapper().readValue(jsonPayload, new TypeReference<>() {});
            } catch (IOException e) {
                response.put("error", "Failed to parse email output: " + e.getMessage());
                response.put("status", "error");
                response.put("code", 500);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> content = parsedDocument.containsKey("content")
                ? (Map<String, Object>) parsedDocument.get("content")
                : Collections.emptyMap();

            String plainText = content.getOrDefault("plainText", "").toString();
            String markdown = content.getOrDefault("markdown", "").toString();

            // Extract email metadata from parsed document
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = parsedDocument.containsKey("metadata")
                ? (Map<String, Object>) parsedDocument.get("metadata")
                : Collections.emptyMap();
            
            String subject = metadata.getOrDefault("subject", "No subject").toString();
            String from = metadata.getOrDefault("from", "Unknown sender").toString();
            String date = metadata.getOrDefault("date", "Unknown date").toString();

            // Build successful response
            response.put("parsedText", plainText); // plain, strictly cleaned text
            response.put("parsedPlain", plainText);
            response.put("parsedMarkdown", markdown);
            response.put("document", parsedDocument);
            response.put("status", "success");
            response.put("filename", filename);
            response.put("fileSize", file.getSize());
            response.put("timestamp", System.currentTimeMillis());
            
            // Add email metadata for chat interface
            response.put("subject", subject);
            response.put("from", from);
            response.put("date", date);
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            // Handle file reading errors
            response.put("error", "Failed to read uploaded file: " + e.getMessage());
            response.put("status", "error");
            response.put("code", 500);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            
        } catch (Exception e) {
            // Handle parsing errors
            response.put("error", "Failed to parse email: " + e.getMessage());
            response.put("status", "error");
            response.put("code", 500);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Intentionally no health/info endpoints here; SystemController exposes /api/health
}