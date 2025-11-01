package com.composerai.api.service;

import com.composerai.api.model.EmailMessage;
import com.composerai.api.model.EmailMessageContextFormatter;
import com.composerai.api.service.CompanyLogoProvider;
import com.composerai.api.service.ContextBuilder.EmailContextRegistry;
import com.composerai.api.service.HtmlToText;
import com.composerai.api.service.email.HtmlConverter;
import com.composerai.api.util.IdGenerator;
import com.composerai.api.util.StringUtils;
import com.composerai.api.util.TemporalUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailParsingService {

    private static final DateTimeFormatter DATE_WITH_OFFSET_FORMATTER =
        DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a xxx", Locale.US);

    private final EmailContextRegistry emailContextRegistry;
    private final ObjectMapper objectMapper;
    private final CompanyLogoProvider companyLogoProvider;

    public Map<String, Object> parseEmailFile(MultipartFile file) {
        validateFile(file);

        String filename = file.getOriginalFilename();
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);

        if (lower.endsWith(".msg")) {
            throw new UnsupportedOperationException(".msg (Outlook) files are not supported yet.");
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("upload-", lower.endsWith(".eml") ? ".eml" : ".html");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            ParsedEmail parsed = parseEmail(tempFile, filename, lower.endsWith(".eml") ? "eml" : "html");
            Map<String, Object> response = buildResponseMap(parsed, filename, file.getSize());
            response.put("timestamp", System.currentTimeMillis());
            return response;
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            String detailedMessage = "Failed to process email file";
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                detailedMessage += ": " + e.getMessage();
            }
            log.error("Email parsing failed for file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException(detailedMessage, e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignore) {
                    // no-op
                }
            }
        }
    }

    public ParsedEmail parseEmail(Path path, String originalFilename) {
        String lower = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        String inputType = lower.endsWith(".eml") ? "eml" : lower.endsWith(".html") ? "html" : "eml";
        return parseEmail(path, originalFilename, inputType);
    }

    private ParsedEmail parseEmail(Path path, String originalFilename, String inputType) {
        try {
            HtmlToText.Options options = new HtmlToText.Options();
            options.inputFile = path.toString();
            options.inputType = inputType;
            options.format = HtmlToText.OutputFormat.PLAIN;
            options.urlsPolicy = HtmlToText.UrlPolicy.CLEAN_ONLY;
            options.includeMetadata = true;
            options.jsonOutput = true;
            options.suppressUtility = true;

            String jsonPayload = convertEmail(options);

            Map<String, Object> parsedDocument = objectMapper.readValue(
                jsonPayload, new TypeReference<Map<String, Object>>() {});

            Map<String, Object> content = parsedDocument.containsKey("content")
                && parsedDocument.get("content") instanceof Map
                ? objectMapper.convertValue(parsedDocument.get("content"), new TypeReference<Map<String, Object>>() {})
                : Collections.emptyMap();

            Map<String, Object> metadata = parsedDocument.containsKey("metadata")
                && parsedDocument.get("metadata") instanceof Map
                ? objectMapper.convertValue(parsedDocument.get("metadata"), new TypeReference<Map<String, Object>>() {})
                : Collections.emptyMap();

            String plainText = String.valueOf(content.getOrDefault("plainText", ""));
            String markdown = String.valueOf(content.getOrDefault("markdown", ""));
            String cleanedPlainText = HtmlConverter.cleanupOutput(plainText, true);
            String cleanedMarkdown = HtmlConverter.cleanupOutput(markdown, true);
            String sanitizedMarkdown = cleanedMarkdown.isBlank() ? null : cleanedMarkdown;
            String emailBody = sanitizedMarkdown != null ? sanitizedMarkdown : cleanedPlainText;

            String subject = firstNonBlank(metadata, "subject");
            if (subject == null) {
                subject = "No subject";
            }
            StructuredParticipant sender = extractSender(metadata);
            StructuredParticipant recipient = extractRecipient(metadata);
            String date = firstNonBlank(metadata, "date", "dateHeader", "dateIso");
            if (date == null) {
                date = "Unknown date";
            }
            String dateIso = firstNonBlank(metadata, "dateIso", "date");
            String dateWithRelativeTime = enrichDateWithOffset(date, dateIso);

            String contextId = resolveContextId(metadata);
            String messageId = resolveMessageId(metadata);

            String companyLogoUrl = deriveCompanyLogoUrl(sender.email());

            String markdownForHtml = sanitizedMarkdown != null ? sanitizedMarkdown : cleanedPlainText;
            String renderedHtml = HtmlConverter.markdownToSafeHtml(markdownForHtml);

            parsedDocument.put("contextId", contextId);

            ParsedEmail parsedEmail = ParsedEmail.newBuilder()
                .id(messageId)
                .contextId(contextId)
                .senderName(sender.name())
                .senderEmail(sender.email())
                .recipientName(recipient.name())
                .recipientEmail(recipient.email())
                .subject(subject)
                .emailBodyRaw(cleanedPlainText)
                .emailBodyTransformedText(cleanedPlainText)
                .emailBodyTransformedMarkdown(sanitizedMarkdown)
                .emailBodyHtml(renderedHtml)
                .receivedTimestampDisplay(dateWithRelativeTime)
                .receivedTimestampIso(dateIso != null ? dateIso.trim() : null)
                .companyLogoUrl(companyLogoUrl)
                .avatarUrl(deriveSenderAvatar(metadata, companyLogoUrl))
                .preview(emailBody)
                .parsedDocument(parsedDocument)
                .parsedPlain(cleanedPlainText)
                .parsedMarkdown(sanitizedMarkdown)
                .parsedHtml(renderedHtml)
                .metadata(metadata)
                .originalFilename(originalFilename)
                .build();

            String contextForAI = EmailMessageContextFormatter.buildContext(parsedEmail);
            parsedEmail = parsedEmail.toParsedBuilder().contextForAi(contextForAI).build();

            emailContextRegistry.store(contextId, contextForAI);

            return parsedEmail;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse email", e);
        }
    }

    private Map<String, Object> buildResponseMap(ParsedEmail parsedEmail, String filename, long fileSize) {
        Map<String, Object> response = new HashMap<>();
        EmailMessage emailMessage = parsedEmail.toEmailMessage();
        response.put("status", "success");
        response.put("emailMessage", emailMessage);
        response.put("contextId", emailMessage.contextId());
        response.put("contextForAI", parsedEmail.contextForAi());
        response.put("parsedPlain", parsedEmail.parsedPlain());
        response.put("parsedMarkdown", parsedEmail.parsedMarkdown());
        response.put("parsedHtml", parsedEmail.parsedHtml());
        response.put("document", parsedEmail.parsedDocument());
        response.put("filename", filename);
        response.put("fileSize", fileSize);
        response.put("subject", emailMessage.subject());
        response.put("from", emailMessage.senderName());
        response.put("date", emailMessage.receivedTimestampDisplay());
        if (emailMessage.receivedTimestampIso() != null) {
            response.put("dateIso", emailMessage.receivedTimestampIso());
        }
        return response;
    }

    protected String convertEmail(HtmlToText.Options options) throws Exception {
        return HtmlToText.convert(options);
    }

    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided. Please upload a valid .eml file.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase(Locale.ROOT).endsWith(".eml")
            && !filename.toLowerCase(Locale.ROOT).endsWith(".msg")
            && !filename.toLowerCase(Locale.ROOT).endsWith(".txt"))) {
            throw new IllegalArgumentException("Invalid file type. Please upload a .eml, .msg, or .txt file.");
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("File too large. Maximum file size is 10MB.");
        }
    }

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

    private static String resolveContextId(Map<String, Object> metadata) {
        String messageId = firstNonBlank(metadata, "messageId", "id");
        if (messageId != null) {
            String normalized = messageId.replaceAll("[^A-Za-z0-9._:-]", "");
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return IdGenerator.uuidV7();
    }

    private static String resolveMessageId(Map<String, Object> metadata) {
        String id = firstNonBlank(metadata, "messageId", "id");
        if (id != null && !id.isBlank()) {
            return id.trim();
        }
        return IdGenerator.uuidV7();
    }

    private static StructuredParticipant extractSender(Map<String, Object> metadata) {
        String name = firstNonBlank(metadata, "fromName");
        String email = firstNonBlank(metadata, "fromEmail");
        if (name == null && email == null) {
            String composite = firstNonBlank(metadata, "from");
            StructuredParticipant extracted = parseCompositeParticipant(composite);
            name = extracted.name();
            email = extracted.email();
        }
        return new StructuredParticipant(defaultIfBlank(name, "Unknown sender"), email);
    }

    private static StructuredParticipant extractRecipient(Map<String, Object> metadata) {
        String name = firstNonBlank(metadata, "toName");
        String email = firstNonBlank(metadata, "toEmail");
        if (name == null && email == null) {
            String composite = firstNonBlank(metadata, "to");
            StructuredParticipant extracted = parseCompositeParticipant(composite);
            name = extracted.name();
            email = extracted.email();
        }
        return new StructuredParticipant(name, email);
    }

    private static StructuredParticipant parseCompositeParticipant(String value) {
        if (value == null || value.isBlank()) {
            return new StructuredParticipant(null, null);
        }
        String trimmed = value.trim();
        if (trimmed.contains("<") && trimmed.contains(">")) {
            int start = trimmed.indexOf('<');
            int end = trimmed.indexOf('>', start);
            if (start >= 0 && end > start) {
                String name = trimmed.substring(0, start).trim();
                String email = trimmed.substring(start + 1, end).trim();
                return new StructuredParticipant(defaultIfBlank(name, null), defaultIfBlank(email, null));
            }
        }
        if (trimmed.contains("@")) {
            return new StructuredParticipant(null, trimmed);
        }
        return new StructuredParticipant(trimmed, null);
    }

    private static String defaultIfBlank(String candidate, String fallback) {
        return candidate == null || candidate.isBlank() ? fallback : candidate;
    }

    private String deriveCompanyLogoUrl(String senderEmail) {
        if (StringUtils.isBlank(senderEmail)) {
            return null;
        }

        String trimmed = senderEmail.trim();
        int atIndex = trimmed.lastIndexOf('@');
        if (atIndex < 0 || atIndex == trimmed.length() - 1) {
            return null;
        }
        String domain = trimmed.substring(atIndex + 1).trim();
        if (domain.isEmpty()) {
            return null;
        }
        return companyLogoProvider.logoUrlForDomain(domain).orElse(null);
    }

    private String deriveSenderAvatar(Map<String, Object> metadata, String companyLogoUrl) {
        String provided = firstNonBlank(metadata, "avatarUrl", "avatar");
        if (!StringUtils.isBlank(provided)) {
            return provided.trim();
        }
        if (!StringUtils.isBlank(companyLogoUrl)) {
            return companyLogoUrl;
        }
        return companyLogoProvider.fallbackAvatarUrl();
    }

    private record StructuredParticipant(String name, String email) {}

    public static final class ParsedEmail extends EmailMessage {
        private final Map<String, Object> parsedDocument;
        private final String parsedPlain;
        private final String parsedMarkdown;
        private final String parsedHtml;
        private final String contextForAi;
        private final Map<String, Object> metadata;
        private final String originalFilename;

        private ParsedEmail(Builder builder) {
            super(builder);
            this.parsedDocument = copyMap(builder.parsedDocument);
            this.parsedPlain = builder.parsedPlain;
            this.parsedMarkdown = builder.parsedMarkdown;
            this.parsedHtml = builder.parsedHtml;
            this.contextForAi = builder.contextForAi;
            this.metadata = copyMap(builder.metadata);
            this.originalFilename = builder.originalFilename;
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public Builder toParsedBuilder() {
            return new Builder(this);
        }

        public Map<String, Object> parsedDocument() {
            return parsedDocument;
        }

        public String parsedPlain() {
            return parsedPlain;
        }

        public String parsedMarkdown() {
            return parsedMarkdown;
        }

        public String parsedHtml() {
            return parsedHtml;
        }

        public String contextForAi() {
            return contextForAi;
        }

        public Map<String, Object> metadata() {
            return metadata;
        }

        public String originalFilename() {
            return originalFilename;
        }

        public EmailMessage toEmailMessage() {
            return this.copy();
        }

        private static Map<String, Object> copyMap(Map<String, Object> source) {
            if (source == null || source.isEmpty()) {
                return Map.of();
            }
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    sanitized.put(entry.getKey(), entry.getValue());
                }
            }
            return sanitized.isEmpty() ? Map.of() : Map.copyOf(sanitized);
        }

        public static final class Builder extends EmailMessage.BuilderBase<Builder> {
            private Map<String, Object> parsedDocument;
            private String parsedPlain;
            private String parsedMarkdown;
            private String parsedHtml;
            private String contextForAi;
            private Map<String, Object> metadata;
            private String originalFilename;

            private Builder() {
            }

            private Builder(ParsedEmail source) {
                super(source);
                this.parsedDocument = source.parsedDocument;
                this.parsedPlain = source.parsedPlain;
                this.parsedMarkdown = source.parsedMarkdown;
                this.parsedHtml = source.parsedHtml;
                this.contextForAi = source.contextForAi;
                this.metadata = source.metadata;
                this.originalFilename = source.originalFilename;
            }

            @Override
            protected Builder self() {
                return this;
            }

            public Builder parsedDocument(Map<String, Object> parsedDocument) {
                this.parsedDocument = parsedDocument;
                return this;
            }

            public Builder parsedPlain(String parsedPlain) {
                this.parsedPlain = parsedPlain;
                return this;
            }

            public Builder parsedMarkdown(String parsedMarkdown) {
                this.parsedMarkdown = parsedMarkdown;
                return this;
            }

            public Builder parsedHtml(String parsedHtml) {
                this.parsedHtml = parsedHtml;
                return this;
            }

            public Builder contextForAi(String contextForAi) {
                this.contextForAi = contextForAi;
                return this;
            }

            public Builder metadata(Map<String, Object> metadata) {
                this.metadata = metadata;
                return this;
            }

            public Builder originalFilename(String originalFilename) {
                this.originalFilename = originalFilename;
                return this;
            }

            public ParsedEmail build() {
                return new ParsedEmail(this);
            }
        }
    }
}
