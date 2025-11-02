package com.composerai.api.controller;

import com.composerai.api.config.AppProperties;
import com.composerai.api.service.CompanyLogoProvider;
import com.composerai.api.service.ContextBuilder;
import com.composerai.api.service.EmailParsingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.composerai.api.model.EmailMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EmailFileParseControllerTest {

    private static final String SAMPLE_EMAIL = "From: sender@example.com\nSubject: Test";

    @Test
    void parseEmail_withNullMetadata_usesFallbackValues() throws Exception {
        ContextBuilder.EmailContextRegistry registry = new ContextBuilder.EmailContextRegistry();
        EmailFileParseController controller = controllerWithPayload(registry, "{\n" +
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
        assertNotNull(body.get("emailMessage"));
        assertEquals("No subject", body.get("subject"));
        assertEquals("Unknown sender", body.get("from"));
        assertEquals("Unknown date", body.get("date"));
        assertNotNull(body.get("contextId"));
        String contextId = body.get("contextId").toString();
        assertTrue(registry.contextForAi(contextId).isPresent(), "context should be cached");
        Map<?, ?> emailMessageJson = new ObjectMapper().convertValue(body.get("emailMessage"), Map.class);
        assertFalse(emailMessageJson.containsKey("emailBodyRaw"), "emailBodyRaw should not be serialized");
    }

    @Test
    void parseEmail_withDateMetadataPreservesIso() throws Exception {
        ContextBuilder.EmailContextRegistry registry = new ContextBuilder.EmailContextRegistry();
        EmailFileParseController controller = controllerWithPayload(registry, "{\n" +
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
    void parseEmail_sanitizesContentOutputs() throws Exception {
        ContextBuilder.EmailContextRegistry registry = new ContextBuilder.EmailContextRegistry();
        EmailFileParseController controller = controllerWithPayload(registry, "{\n" +
            "  \"content\": {\"plainText\": \"Hello <script>alert('x')</script> body\", \"markdown\": \"**Hello** <script>alert(1)</script>\"},\n" +
            "  \"metadata\": {\"subject\": \"Status\", \"from\": \"Ops\", \"date\": \"Oct 01, 2025 at 4:30 PM -07:00\"}\n" +
            "}");

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "message.eml",
            "message/rfc822",
            SAMPLE_EMAIL.getBytes(StandardCharsets.UTF_8)
        );

        ResponseEntity<Map<String, Object>> response = controller.parseEmail(file);

        Map<String, Object> body = response.getBody();
        EmailMessage emailMessage = (EmailMessage) body.get("emailMessage");
        assertNotNull(emailMessage);
        String text = emailMessage.emailBodyTransformedText();
        assertNotNull(text);
        assertFalse(text.contains("<script"));

        String markdown = emailMessage.emailBodyTransformedMarkdown();
        if (markdown != null) {
            assertFalse(markdown.contains("<script"));
        }

        String parsedPlain = (String) body.get("parsedPlain");
        if (parsedPlain != null) {
            assertFalse(parsedPlain.contains("<script"));
        }

        String parsedMarkdown = (String) body.get("parsedMarkdown");
        if (parsedMarkdown != null) {
            assertFalse(parsedMarkdown.contains("<script"));
        }
    }

    @Test
    void parseEmail_withUnsupportedExtension_throwsException() {
        ContextBuilder.EmailContextRegistry registry = new ContextBuilder.EmailContextRegistry();
        EmailFileParseController controller = new EmailFileParseController(
            new EmailParsingService(registry, new ObjectMapper(), new CompanyLogoProvider(), new AppProperties()));
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
        ContextBuilder.EmailContextRegistry registry = new ContextBuilder.EmailContextRegistry();
        EmailFileParseController controller = new EmailFileParseController(
            new EmailParsingService(registry, new ObjectMapper(), new CompanyLogoProvider(), new AppProperties()));
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

    @Test
    void parseEmail_htmlModeUsesOriginalHtml() throws Exception {
        ContextBuilder.EmailContextRegistry registry = new ContextBuilder.EmailContextRegistry();
        AppProperties props = new AppProperties();
        props.getEmailRendering().setMode(AppProperties.EmailRenderMode.HTML);
        String payload = """
            {
              "content": {
                "plainText": "Plain body",
                "markdown": "*Plain* body",
                "originalHtml": "<div id='original'>Original</div>"
              },
              "metadata": {
                "subject": "Demo",
                "from": "Ops",
                "date": "Oct 01, 2025 at 4:30 PM -07:00"
              }
            }
            """;

        EmailFileParseController controller = controllerWithPayload(registry, payload, props);
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "message.eml",
            "message/rfc822",
            SAMPLE_EMAIL.getBytes(StandardCharsets.UTF_8)
        );

        ResponseEntity<Map<String, Object>> response = controller.parseEmail(file);
        Map<String, Object> body = response.getBody();
        EmailMessage emailMessage = (EmailMessage) body.get("emailMessage");

        assertNotNull(emailMessage);
        assertEquals("<div id='original'>Original</div>", emailMessage.emailBodyHtml());
        assertEquals("<div id='original'>Original</div>", body.get("parsedHtml"));
    }

    @Test
    void parseEmail_markdownModeUsesSanitizedHtml() throws Exception {
        ContextBuilder.EmailContextRegistry registry = new ContextBuilder.EmailContextRegistry();
        AppProperties props = new AppProperties();
        props.getEmailRendering().setMode(AppProperties.EmailRenderMode.MARKDOWN);
        String payload = """
            {
              "content": {
                "plainText": "Plain body",
                "markdown": "*Plain* body",
                "originalHtml": "<div id='original'>Original</div>"
              },
              "metadata": {
                "subject": "Demo",
                "from": "Ops",
                "date": "Oct 01, 2025 at 4:30 PM -07:00"
              }
            }
            """;

        EmailFileParseController controller = controllerWithPayload(registry, payload, props);
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "message.eml",
            "message/rfc822",
            SAMPLE_EMAIL.getBytes(StandardCharsets.UTF_8)
        );

        ResponseEntity<Map<String, Object>> response = controller.parseEmail(file);
        Map<String, Object> body = response.getBody();
        EmailMessage emailMessage = (EmailMessage) body.get("emailMessage");

        assertNotNull(emailMessage);
        assertNotNull(emailMessage.emailBodyHtml());
        assertNotEquals("<div id='original'>Original</div>", emailMessage.emailBodyHtml());
        assertTrue(emailMessage.emailBodyHtml().contains("<em>Plain</em>"));
        assertEquals(emailMessage.emailBodyHtml(), body.get("parsedHtml"));
    }

    @Test
    void parseEmail_plaintextModeSuppressesHtml() throws Exception {
        ContextBuilder.EmailContextRegistry registry = new ContextBuilder.EmailContextRegistry();
        AppProperties props = new AppProperties();
        props.getEmailRendering().setMode(AppProperties.EmailRenderMode.PLAINTEXT);
        String payload = """
            {
              "content": {
                "plainText": "Plain body",
                "markdown": "*Plain* body",
                "originalHtml": "<div id='original'>Original</div>"
              },
              "metadata": {
                "subject": "Demo",
                "from": "Ops",
                "date": "Oct 01, 2025 at 4:30 PM -07:00"
              }
            }
            """;

        EmailFileParseController controller = controllerWithPayload(registry, payload, props);
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "message.eml",
            "message/rfc822",
            SAMPLE_EMAIL.getBytes(StandardCharsets.UTF_8)
        );

        ResponseEntity<Map<String, Object>> response = controller.parseEmail(file);
        Map<String, Object> body = response.getBody();
        EmailMessage emailMessage = (EmailMessage) body.get("emailMessage");

        assertNotNull(emailMessage);
        assertNull(emailMessage.emailBodyHtml());
        assertEquals("Plain body", emailMessage.emailBodyTransformedText());
        assertNull(body.get("parsedHtml"));
    }

    @Test
    void parseEmail_longMessageIdProducesDeterministicBoundedContextId() throws Exception {
        ContextBuilder.EmailContextRegistry registry = new ContextBuilder.EmailContextRegistry();
        String longMessageId = "<" + "alpha-" + "x".repeat(220) + "@example-domain.test>";
        String payload = """
            {
              "content": {"plainText": "Body", "markdown": "Body"},
              "metadata": {
                "subject": "Status",
                "from": "Ops",
                "date": "Oct 01, 2025 at 4:30 PM -07:00",
                "messageId": "%s"
              }
            }
            """.formatted(longMessageId.replace("\"", "\\\""));

        EmailFileParseController controller = controllerWithPayload(registry, payload);
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "message.eml",
            "message/rfc822",
            SAMPLE_EMAIL.getBytes(StandardCharsets.UTF_8)
        );

        ResponseEntity<Map<String, Object>> first = controller.parseEmail(file);
        ResponseEntity<Map<String, Object>> second = controller.parseEmail(file);

        String contextId1 = first.getBody().get("contextId").toString();
        String contextId2 = second.getBody().get("contextId").toString();

        assertEquals(contextId1, contextId2, "Context IDs should be deterministic for identical inputs");
        assertTrue(contextId1.length() <= 200, "Context ID should not exceed DTO limit");
        assertTrue(contextId1.matches("[A-Za-z0-9._:-]+"), "Context ID should contain only safe characters");
    }

    private EmailFileParseController controllerWithPayload(ContextBuilder.EmailContextRegistry registry, String payload) {
        return controllerWithPayload(registry, payload, new AppProperties());
    }

    private EmailFileParseController controllerWithPayload(ContextBuilder.EmailContextRegistry registry, String payload, AppProperties appProperties) {
        EmailParsingService emailParsingService = new EmailParsingService(registry, new ObjectMapper(), new CompanyLogoProvider(), appProperties) {
            @Override
            protected String convertEmail(com.composerai.api.service.HtmlToText.Options options) {
                return payload;
            }
        };
        return new EmailFileParseController(emailParsingService);
    }
}
