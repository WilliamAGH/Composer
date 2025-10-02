package com.composerai.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailControllerTest {

    private static final String SAMPLE_EMAIL = "From: sender@example.com\nSubject: Test";

    @Test
    void parseEmail_withNullMetadata_usesFallbackValues() throws Exception {
        EmailController controller = controllerWithPayload("{\n" +
            "  \"content\": {\"plainText\": \"Body\", \"markdown\": \"**Body**\"},\n" +
            "  \"metadata\": {\"subject\": null, \"from\": null, \"date\": null}\n" +
            "}");
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "message.eml",
            "message/rfc822",
            SAMPLE_EMAIL.getBytes(StandardCharsets.UTF_8)
        );

        ResponseEntity<Map<String, Object>> response = controller.parseEmail(file);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertEquals("No subject", body.get("subject"));
        assertEquals("Unknown sender", body.get("from"));
        assertEquals("Unknown date", body.get("date"));
    }

    @Test
    void parseEmail_withDateMetadataPreservesIso() throws Exception {
        EmailController controller = controllerWithPayload("{\n" +
            "  \"content\": {\"plainText\": \"Body\", \"markdown\": \"**Body**\"},\n" +
            "  \"metadata\": {\"subject\": \"Status\", \"from\": \"Ops\", \"date\": \"Oct 01, 2025 at 4:30 PM -07:00\", \"dateIso\": \"2025-10-01T23:30:00Z\"}\n" +
            "}");
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "message.eml",
            "message/rfc822",
            SAMPLE_EMAIL.getBytes(StandardCharsets.UTF_8)
        );

        ResponseEntity<Map<String, Object>> response = controller.parseEmail(file);

        Map<String, Object> body = response.getBody();
        assertEquals("Oct 01, 2025 at 4:30 PM -07:00", body.get("date"));
        assertEquals("2025-10-01T23:30:00Z", body.get("dateIso"));
    }

    @Test
    void parseEmail_withUnsupportedExtension_throwsException() {
        EmailController controller = new EmailController();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "document.pdf",
            "application/pdf",
            new byte[10]
        );

        assertThrows(IllegalArgumentException.class, () -> controller.parseEmail(file));
    }

    @Test
    void parseEmail_withReceivedHeaderUsesReceivedDate() throws Exception {
        EmailController controller = new EmailController();
        String eml = String.join("\r\n",
            "Received: from mail.example.net by inbound.example.net; Wed, 01 Oct 2025 18:45:00 +0530",
            "Subject: Received fallback",
            "From: Example <sender@example.com>",
            "Message-ID: <abc123@example.com>",
            "Content-Type: text/plain; charset=\"UTF-8\"",
            "",
            "This is a received-header test email.");

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "received.eml",
            "message/rfc822",
            eml.getBytes(StandardCharsets.UTF_8)
        );

        ResponseEntity<Map<String, Object>> response = controller.parseEmail(file);
        Map<String, Object> body = response.getBody();

        assertEquals("2025-10-01T18:45:00+05:30", body.get("dateIso"));
        String display = body.get("date").toString();
        assertTrue(display.contains("+05:30"), "display date should retain offset");

        Map<?, ?> document = (Map<?, ?>) body.get("document");
        Map<?, ?> metadata = (Map<?, ?>) document.get("metadata");
        assertEquals("Received", metadata.get("dateSource"));
    }

    private EmailController controllerWithPayload(String payload) {
        return new EmailController() {
            @Override
            protected String convertEmail(com.composerai.api.service.HtmlToText.Options options) {
                return payload;
            }
        };
    }
}
