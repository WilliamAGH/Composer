/**
 * EmailPipeline: orchestrates extraction and conversion based on Options
 *
 * @author William Callahan
 * @since 2025-09-18
 * @version 0.0.1
 */
package com.composerai.api.service.email;

import com.composerai.api.service.HtmlToText;
import com.composerai.api.util.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MailDateFormat;
import jakarta.mail.internet.MimeMessage;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

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

    private static final DateTimeFormatter DISPLAY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a XXX");

    private EmailPipeline() {}

    public static String process(HtmlToText.Options options) throws Exception {
        String type = options.inputType != null ? options.inputType : inferInputType(options.inputFile);
        if ("eml".equalsIgnoreCase(type)) {
            try (InputStream in = new BufferedInputStream(new FileInputStream(options.inputFile))) {
                Session session = Session.getDefaultInstance(new Properties());
                MimeMessage message = new MimeMessage(session, in);
                String html = EmailExtractor.extractFirstHtml(message).orElse(null);
                String text = html != null
                        ? null
                        : EmailExtractor.extractFirstPlainText(message).orElse("");

                if (options.jsonOutput) {
                    String plain = html != null
                            ? HtmlConverter.convertHtml(
                                    html, HtmlToText.OutputFormat.PLAIN, options.urlsPolicy, options.suppressUtility)
                            : HtmlConverter.cleanupOutput(text, options.suppressUtility);
                    String markdown = html != null
                            ? HtmlConverter.convertHtml(
                                    html, HtmlToText.OutputFormat.MARKDOWN, options.urlsPolicy, options.suppressUtility)
                            : HtmlConverter.cleanupOutput(text, options.suppressUtility);
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("messageId", StringUtils.safe(message.getMessageID()));
                    meta.put("subject", StringUtils.safe(message.getSubject()));
                    meta.put("from", StringUtils.safe(EmailExtractor.formatAddresses(message.getFrom())));
                    meta.put(
                            "to",
                            StringUtils.safe(EmailExtractor.formatAddresses(
                                    message.getRecipients(jakarta.mail.Message.RecipientType.TO))));
                    meta.put(
                            "cc",
                            StringUtils.safe(EmailExtractor.formatAddresses(
                                    message.getRecipients(jakarta.mail.Message.RecipientType.CC))));

                    // Extract and format date for user-friendly display
                    DateMetadata dateMetadata = extractDateMetadata(message);
                    meta.put("date", StringUtils.safe(dateMetadata.displayLabel()));
                    meta.put("dateIso", StringUtils.safe(dateMetadata.isoTimestamp()));
                    meta.put("dateHeader", dateMetadata.originalHeader());
                    if (dateMetadata.source() != null) {
                        meta.put("dateSource", dateMetadata.source());
                    }

                    meta.put("source", "eml-file");
                    meta.put("path", Path.of(options.inputFile).getFileName().toString());

                    Map<String, Object> policies = Map.of(
                            "flattenTables", Boolean.TRUE,
                            "stripScripts", Boolean.TRUE,
                            "urlsPolicy", options.urlsPolicy.name().toLowerCase(Locale.ROOT),
                            "metadataIncluded", options.includeMetadata,
                            "suppressUtility", options.suppressUtility);

                    String id = meta.get("messageId") != null
                                    && !meta.get("messageId").toString().isBlank()
                            ? meta.get("messageId").toString()
                            : com.composerai.api.service.HtmlToText.normalizeBaseName(options.inputFile);
                    Map<String, Object> doc =
                            EmailDocumentBuilder.buildDocument(id, meta, plain, markdown, html, policies);
                    return new ObjectMapper().writeValueAsString(doc);
                } else {
                    String metaHeader =
                            options.includeMetadata ? EmailExtractor.buildMetadataHeader(message, options.format) : "";
                    if (html != null) {
                        String body = HtmlConverter.convertHtml(
                                html, options.format, options.urlsPolicy, options.suppressUtility);
                        if (!options.suppressUtility
                                && options.format == HtmlToText.OutputFormat.PLAIN
                                && (body == null || body.isBlank())) {
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
                String plain = HtmlConverter.convertHtml(
                        html, HtmlToText.OutputFormat.PLAIN, options.urlsPolicy, options.suppressUtility);
                String markdown = HtmlConverter.convertHtml(
                        html, HtmlToText.OutputFormat.MARKDOWN, options.urlsPolicy, options.suppressUtility);
                Map<String, Object> meta = new HashMap<>();
                meta.put("source", "html-file");
                meta.put("path", options.inputFile);
                Map<String, Object> policies = Map.of(
                        "flattenTables", Boolean.TRUE,
                        "stripScripts", Boolean.TRUE,
                        "urlsPolicy", options.urlsPolicy.name().toLowerCase(Locale.ROOT),
                        "suppressUtility", options.suppressUtility);
                String id = com.composerai.api.service.HtmlToText.normalizeBaseName(options.inputFile);
                Map<String, Object> doc = EmailDocumentBuilder.buildDocument(id, meta, plain, markdown, html, policies);
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

    private static OffsetDateTime parseDateHeader(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(headerValue.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                java.util.Date parsed = new MailDateFormat().parse(headerValue);
                if (parsed == null) {
                    return null;
                }
                return OffsetDateTime.ofInstant(parsed.toInstant(), ZoneOffset.UTC);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private static DateMetadata extractDateMetadata(MimeMessage message) throws MessagingException {
        String originalDateHeader = StringUtils.safe(message.getHeader("Date", null));
        OffsetDateTime headerDate = parseDateHeader(originalDateHeader);
        if (headerDate != null) {
            return new DateMetadata(
                    headerDate,
                    DISPLAY_DATE_FORMAT.format(headerDate),
                    headerDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    originalDateHeader,
                    "Date");
        }

        OffsetDateTime receivedDate = parseReceivedDate(message);
        if (receivedDate != null) {
            return new DateMetadata(
                    receivedDate,
                    DISPLAY_DATE_FORMAT.format(receivedDate),
                    receivedDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    originalDateHeader,
                    "Received");
        }

        java.util.Date sentDate = message.getSentDate();
        if (sentDate != null) {
            OffsetDateTime utcFallback = OffsetDateTime.ofInstant(sentDate.toInstant(), ZoneOffset.UTC);
            return new DateMetadata(
                    utcFallback,
                    DISPLAY_DATE_FORMAT.format(utcFallback),
                    utcFallback.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    originalDateHeader,
                    "SentDate");
        }

        return new DateMetadata(null, null, null, originalDateHeader, null);
    }

    private static OffsetDateTime parseReceivedDate(MimeMessage message) throws MessagingException {
        String[] receivedHeaders = message.getHeader("Received");
        if (receivedHeaders == null || receivedHeaders.length == 0) {
            return null;
        }
        // Iterate from most recent (bottom-most) to earliest
        for (int i = receivedHeaders.length - 1; i >= 0; i--) {
            String header = receivedHeaders[i];
            if (header == null || header.isBlank()) {
                continue;
            }
            int semicolonIndex = header.lastIndexOf(';');
            if (semicolonIndex < 0 || semicolonIndex == header.length() - 1) {
                continue;
            }
            String datePortion = header.substring(semicolonIndex + 1).trim();
            OffsetDateTime parsed = parseDateHeader(datePortion);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private record DateMetadata(
            OffsetDateTime timestamp, String displayLabel, String isoTimestamp, String originalHeader, String source) {
        DateMetadata {
            if (timestamp != null && (displayLabel == null || displayLabel.isBlank())) {
                displayLabel = DISPLAY_DATE_FORMAT.format(timestamp);
            }
            if (timestamp != null && (isoTimestamp == null || isoTimestamp.isBlank())) {
                isoTimestamp = timestamp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            }
        }
    }
}
