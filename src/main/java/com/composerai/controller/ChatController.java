package com.composerai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> request) {
        String message = (String) request.get("message");
        String conversationId = (String) request.get("conversationId");
        
        // Simple response for now - this would connect to William's actual chat service
        Map<String, Object> response = new HashMap<>();
        response.put("response", "Hello! I'm ComposerAI. I received your message: \"" + message + "\". The full chat functionality will be connected to the backend services soon.");
        response.put("conversationId", conversationId);
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("intent", "greeting");
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/parse-email")
    public ResponseEntity<Map<String, Object>> parseEmail(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Please select a file to upload");
                return ResponseEntity.badRequest().body(error);
            }

            // Read file content
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            
            // Simple email parsing - extract basic info
            Map<String, Object> response = new HashMap<>();
            response.put("filename", file.getOriginalFilename());
            response.put("size", file.getSize());
            response.put("contentType", file.getContentType());
            
            // Extract subject and sender (basic parsing)
            String subject = extractEmailField(content, "Subject:");
            String from = extractEmailField(content, "From:");
            String to = extractEmailField(content, "To:");
            String date = extractEmailField(content, "Date:");
            
            Map<String, Object> emailData = new HashMap<>();
            emailData.put("subject", subject != null ? subject : "No Subject");
            emailData.put("from", from != null ? from : "Unknown Sender");
            emailData.put("to", to != null ? to : "Unknown Recipient");
            emailData.put("date", date != null ? date : "Unknown Date");
            emailData.put("content", content);
            emailData.put("snippet", content.length() > 200 ? content.substring(0, 200) + "..." : content);
            
            response.put("emailData", emailData);
            response.put("status", "success");
            response.put("message", "Email parsed successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to parse email: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    private String extractEmailField(String content, String fieldName) {
        try {
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.toLowerCase().startsWith(fieldName.toLowerCase())) {
                    return line.substring(fieldName.length()).trim();
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "ComposerAI Chat API");
        return ResponseEntity.ok(response);
    }
}