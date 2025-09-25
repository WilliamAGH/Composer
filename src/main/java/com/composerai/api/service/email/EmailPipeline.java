/**
 * EmailPipeline: orchestrates extraction and conversion based on Options
 * 
 * @author William Callahan
 * @since 2025-09-18
 * @version 0.0.1
 */

package com.composerai.api.service.email;

import com.composerai.api.service.HtmlToText;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * EmailPipeline is a thin orchestrator
 *
 * Responsibilities:
 * - Interpret Options and detect input type
 * - Extract HTML or plain text from MIME/EML
 * - Convert to requested format via HtmlConverter with URL policy
 * - Prepend metadata when enabled
 * - Return the final string result
 */
public final class EmailPipeline {

    private EmailPipeline() {}

    public static String process(HtmlToText.Options options) throws Exception {
        String type = options.inputType != null ? options.inputType : inferInputType(options.inputFile);
        if ("eml".equalsIgnoreCase(type)) {
            try (InputStream in = new BufferedInputStream(new FileInputStream(options.inputFile))) {
                Session session = Session.getDefaultInstance(new Properties());
                MimeMessage message = new MimeMessage(session, in);
                String html = EmailExtractor.extractFirstHtml(message).orElse(null);
                String text = html != null ? null : EmailExtractor.extractFirstPlainText(message).orElse("");

                if (options.jsonOutput) {
                    String plain = html != null ? HtmlConverter.convertHtml(html, HtmlToText.OutputFormat.PLAIN, options.urlsPolicy, options.suppressUtility) : HtmlConverter.cleanupOutput(text, options.suppressUtility);
                    String markdown = html != null ? HtmlConverter.convertHtml(html, HtmlToText.OutputFormat.MARKDOWN, options.urlsPolicy, options.suppressUtility) : HtmlConverter.cleanupOutput(text, options.suppressUtility);
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("messageId", safe(message.getMessageID()));
                    meta.put("subject", safe(message.getSubject()));
                    meta.put("from", safe(EmailExtractor.formatAddresses(message.getFrom())));
                    meta.put("to", safe(EmailExtractor.formatAddresses(message.getRecipients(jakarta.mail.Message.RecipientType.TO))));
                    meta.put("cc", safe(EmailExtractor.formatAddresses(message.getRecipients(jakarta.mail.Message.RecipientType.CC))));
                    meta.put("source", "eml-file");
                    meta.put("path", Path.of(options.inputFile).getFileName().toString());

                    Map<String, Object> policies = Map.of(
                        "flattenTables", Boolean.TRUE,
                        "stripScripts", Boolean.TRUE,
                        "urlsPolicy", options.urlsPolicy.name().toLowerCase(Locale.ROOT),
                        "metadataIncluded", options.includeMetadata,
                        "suppressUtility", options.suppressUtility
                    );

                    String id = meta.get("messageId") != null && !meta.get("messageId").toString().isBlank()
                        ? meta.get("messageId").toString() : com.composerai.api.service.HtmlToText.normalizeBaseName(options.inputFile);
                    Map<String, Object> doc = EmailDocumentBuilder.buildDocument(id, meta, plain, markdown, policies);
                    return new ObjectMapper().writeValueAsString(doc);
                } else {
                    String metaHeader = options.includeMetadata ? EmailExtractor.buildMetadataHeader(message, options.format) : "";
                    if (html != null) {
                        String body = HtmlConverter.convertHtml(html, options.format, options.urlsPolicy, options.suppressUtility);
                        if (!options.suppressUtility && options.format == HtmlToText.OutputFormat.PLAIN && (body == null || body.isBlank())) {
                            body = HtmlConverter.convertHtml(html, options.format, HtmlToText.UrlPolicy.KEEP, false);
                        }
                        return metaHeader + body;
                    }
                    return metaHeader + HtmlConverter.cleanupOutput(text, options.suppressUtility);
                }
            }
        } else if ("html".equalsIgnoreCase(type) || "htm".equalsIgnoreCase(type)) {
            Charset cs = options.charset != null ? options.charset : StandardCharsets.UTF_8;
            String html = Files.readString(Path.of(options.inputFile), cs);
            if (options.jsonOutput) {
                String plain = HtmlConverter.convertHtml(html, HtmlToText.OutputFormat.PLAIN, options.urlsPolicy, options.suppressUtility);
                String markdown = HtmlConverter.convertHtml(html, HtmlToText.OutputFormat.MARKDOWN, options.urlsPolicy, options.suppressUtility);
                Map<String, Object> meta = new HashMap<>();
                meta.put("source", "html-file");
                meta.put("path", options.inputFile);
                Map<String, Object> policies = Map.of(
                    "flattenTables", Boolean.TRUE,
                    "stripScripts", Boolean.TRUE,
                    "urlsPolicy", options.urlsPolicy.name().toLowerCase(Locale.ROOT),
                    "suppressUtility", options.suppressUtility
                );
                String id = com.composerai.api.service.HtmlToText.normalizeBaseName(options.inputFile);
                Map<String, Object> doc = EmailDocumentBuilder.buildDocument(id, meta, plain, markdown, policies);
                return new ObjectMapper().writeValueAsString(doc);
            }
            return HtmlConverter.convertHtml(html, options.format, options.urlsPolicy, options.suppressUtility);
        }
        throw new IllegalArgumentException("Unsupported input type: " + type);
    }

    private static String inferInputType(String inputFile) {
        int idx = inputFile.lastIndexOf('.');
        if (idx < 0) return "";
        return inputFile.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private static String safe(String s) { return s == null ? "" : s; }
}


