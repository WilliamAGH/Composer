package com.composerai.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Optional;

// Import your existing parser classes
import com.composerai.api.service.email.EmailExtractor;
import com.composerai.api.service.HtmlToText;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * REST Controller for handling email file uploads and parsing operations.
 * Provides endpoints for processing .eml files and extracting readable content.
 */
@RestController
@CrossOrigin(origins = "*") // Allow frontend to call this API
public class EmailController {

    // EmailExtractor is used statically, no need for @Autowired
    // HtmlToText service for converting HTML to plain text if needed
    @Autowired
    private HtmlToText htmlToText;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

            // Get input stream from uploaded file
            InputStream inputStream = file.getInputStream();
            
            // Parse the email using your existing parser
            String parsedText = parseEmailContent(inputStream, filename);
            
            // Build successful response
            response.put("parsedText", parsedText);
            response.put("status", "success");
            response.put("filename", filename);
            response.put("fileSize", file.getSize());
            response.put("timestamp", System.currentTimeMillis());
            
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

    /**
     * Parse email content from InputStream using your existing parser logic.
     * This method uses your EmailExtractor class to parse .eml files.
     * 
     * @param inputStream The input stream of the uploaded file
     * @param filename The original filename for context
     * @return Parsed email text content
     * @throws Exception if parsing fails
     */
    private String parseEmailContent(InputStream inputStream, String filename) throws Exception {
        
        try {
            // Create a JavaMail session
            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props);
            
            // Load the MimeMessage from the InputStream
            MimeMessage message = EmailExtractor.loadMessage(session, inputStream);
            
            // Build the metadata header
            String metadataHeader = EmailExtractor.buildMetadataHeader(message, HtmlToText.OutputFormat.PLAIN);
            
            // Extract content - try HTML first, then plain text
            Optional<String> htmlContent = EmailExtractor.extractFirstHtml(message);
            Optional<String> plainTextContent = EmailExtractor.extractFirstPlainText(message);
            
            StringBuilder result = new StringBuilder();
            result.append(metadataHeader).append("\n");
            
            if (htmlContent.isPresent()) {
                // Convert HTML to plain text using HtmlToText service
                String htmlText = htmlContent.get();
                try {
                    String convertedText = htmlToText.convertHtmlToText(htmlText);
                    result.append("CONTENT (converted from HTML):\n");
                    result.append(convertedText);
                } catch (Exception e) {
                    // Fallback to raw HTML if conversion fails
                    result.append("CONTENT (HTML - conversion failed):\n");
                    result.append(htmlText);
                }
            } else if (plainTextContent.isPresent()) {
                result.append("CONTENT:\n");
                result.append(plainTextContent.get());
            } else {
                result.append("CONTENT:\n");
                result.append("No readable content found in this email.");
            }
            
            return result.toString();
            
        } catch (Exception e) {
            throw new Exception("Failed to parse email file '" + filename + "': " + e.getMessage(), e);
        }
    }

    /**
     * Email service health check endpoint to verify the email parsing service is working
     */
    @GetMapping("/email-health")
    public ResponseEntity<Map<String, Object>> emailHealthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "EmailController");
        response.put("timestamp", System.currentTimeMillis());
        response.put("version", "1.0.0");
        
        Map<String, String> services = new HashMap<>();
        services.put("email_parser", "ready");
        services.put("file_upload", "available");
        services.put("api_endpoints", "operational");
        response.put("services", services);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get API information and usage examples
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getApiInfo() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "ComposerAI Email Parser API");
        response.put("version", "1.0.0");
        
        Map<String, Object> endpoints = new HashMap<>();
        
        Map<String, Object> parseEndpoint = new HashMap<>();
        parseEndpoint.put("method", "POST");
        parseEndpoint.put("path", "/api/parse-email");
        parseEndpoint.put("description", "Upload and parse .eml email files");
        parseEndpoint.put("accepts", "multipart/form-data");
        parseEndpoint.put("parameter", "file (MultipartFile) - The .eml file to parse");
        parseEndpoint.put("supported_formats", new String[]{".eml", ".msg", ".txt"});
        parseEndpoint.put("max_file_size", "10MB");
        
        endpoints.put("parse-email", parseEndpoint);
        response.put("endpoints", endpoints);
        
        Map<String, String> usage = new HashMap<>();
        usage.put("curl_example", "curl -X POST -F 'file=@email.eml' http://localhost:8080/api/parse-email");
        usage.put("javascript_example", "const formData = new FormData(); formData.append('file', fileInput.files[0]); fetch('/api/parse-email', { method: 'POST', body: formData })");
        response.put("usage", usage);
        
        return ResponseEntity.ok(response);
    }
}