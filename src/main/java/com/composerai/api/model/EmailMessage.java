package com.composerai.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmailMessage {

    private static final String FALLBACK_RECIPIENT_EMAIL = "user@example.com";
    private static final String FALLBACK_RECIPIENT_NAME = "InboxAI User";

    private final String id;
    private final String contextId;
    private final String senderName;
    private final String senderEmail;
    private final String recipientName;
    private final String recipientEmail;
    private final String subject;
    @JsonIgnore
    private final String emailBodyRaw;
    private final String emailBodyTransformedText;
    private final String emailBodyTransformedMarkdown;
    private final String emailBodyHtml;
    private final String llmSummary;
    @JsonProperty("receivedTimestampIso")
    private final String receivedTimestampIso;
    @JsonProperty("receivedTimestampDisplay")
    private final String receivedTimestampDisplay;
    private final List<String> labels;
    private final String companyLogoUrl;
    private final String avatarUrl;
    private final boolean starred;
    private final boolean read;
    private final String preview;

    protected EmailMessage(BuilderBase<?> builder) {
        this.id = builder.id;
        this.contextId = builder.contextId;
        String normalizedSenderEmail = normalize(builder.senderEmail);
        this.senderEmail = normalizedSenderEmail;
        this.senderName = defaultIfBlank(builder.senderName, this.senderEmail);

        String normalizedRecipientEmail = normalize(builder.recipientEmail);
        String effectiveRecipientName = defaultIfBlank(builder.recipientName, normalizedRecipientEmail);
        if (isFallbackRecipient(effectiveRecipientName, normalizedRecipientEmail)) {
            normalizedRecipientEmail = null;
            effectiveRecipientName = null;
        }
        this.recipientEmail = normalizedRecipientEmail;
        this.recipientName = effectiveRecipientName;
        this.subject = defaultIfBlank(builder.subject, "No subject");
        String rawBody = defaultIfBlank(builder.emailBodyRaw, "");
        this.emailBodyRaw = rawBody;
        this.emailBodyTransformedText = defaultIfBlank(builder.emailBodyTransformedText, rawBody);
        this.emailBodyTransformedMarkdown = normalize(builder.emailBodyTransformedMarkdown);
        this.emailBodyHtml = normalizeHtml(builder.emailBodyHtml);
        this.llmSummary = normalize(builder.llmSummary);
        this.receivedTimestampIso = normalize(builder.receivedTimestampIso);
        this.receivedTimestampDisplay = normalize(builder.receivedTimestampDisplay);
        this.labels = builder.labels == null ? List.of() : List.copyOf(builder.labels);
        this.companyLogoUrl = normalizeUrl(builder.companyLogoUrl);
        this.avatarUrl = normalizeUrl(builder.avatarUrl);
        this.starred = builder.starred;
        this.read = builder.read;
        this.preview = defaultIfBlank(builder.preview, derivePreview(this.emailBodyTransformedText));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public EmailMessage withSummary(String summary) {
        return this.toBuilder().llmSummary(summary).build();
    }

    public EmailMessage markRead(boolean readValue) {
        return this.toBuilder().read(readValue).build();
    }

    public EmailMessage markStarred(boolean starredValue) {
        return this.toBuilder().starred(starredValue).build();
    }

    public OffsetDateTime receivedAt() {
        if (receivedTimestampIso == null || receivedTimestampIso.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(receivedTimestampIso.trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    public String id() {
        return id;
    }

    public String contextId() {
        return contextId;
    }

    public String senderName() {
        return senderName;
    }

    public String senderEmail() {
        return senderEmail;
    }

    public String recipientName() {
        return recipientName;
    }

    public String recipientEmail() {
        return recipientEmail;
    }

    public String subject() {
        return subject;
    }

    public String emailBodyRaw() {
        return emailBodyRaw;
    }

    public String emailBodyTransformedText() {
        return emailBodyTransformedText;
    }

    public String emailBodyTransformedMarkdown() {
        return emailBodyTransformedMarkdown;
    }

    public String emailBodyHtml() {
        return emailBodyHtml;
    }

    public String llmSummary() {
        return llmSummary;
    }

    public String receivedTimestampIso() {
        return receivedTimestampIso;
    }

    public String receivedTimestampDisplay() {
        return receivedTimestampDisplay;
    }

    public List<String> labels() {
        return labels;
    }

    public String companyLogoUrl() {
        return companyLogoUrl;
    }

    public String avatarUrl() {
        return avatarUrl;
    }

    public boolean starred() {
        return starred;
    }

    public boolean read() {
        return read;
    }

    public String preview() {
        return preview;
    }

    public EmailMessage copy() {
        return new Builder(this).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmailMessage that)) return false;
        return starred == that.starred
            && read == that.read
            && Objects.equals(id, that.id)
            && Objects.equals(contextId, that.contextId)
            && Objects.equals(senderName, that.senderName)
            && Objects.equals(senderEmail, that.senderEmail)
            && Objects.equals(recipientName, that.recipientName)
            && Objects.equals(recipientEmail, that.recipientEmail)
            && Objects.equals(subject, that.subject)
            && Objects.equals(emailBodyRaw, that.emailBodyRaw)
            && Objects.equals(emailBodyTransformedText, that.emailBodyTransformedText)
            && Objects.equals(emailBodyTransformedMarkdown, that.emailBodyTransformedMarkdown)
            && Objects.equals(emailBodyHtml, that.emailBodyHtml)
            && Objects.equals(llmSummary, that.llmSummary)
            && Objects.equals(receivedTimestampIso, that.receivedTimestampIso)
            && Objects.equals(receivedTimestampDisplay, that.receivedTimestampDisplay)
            && Objects.equals(labels, that.labels)
            && Objects.equals(companyLogoUrl, that.companyLogoUrl)
            && Objects.equals(avatarUrl, that.avatarUrl)
            && Objects.equals(preview, that.preview);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            contextId,
            senderName,
            senderEmail,
            recipientName,
            recipientEmail,
            subject,
            emailBodyRaw,
            emailBodyTransformedText,
            emailBodyTransformedMarkdown,
            emailBodyHtml,
            llmSummary,
            receivedTimestampIso,
            receivedTimestampDisplay,
            labels,
            companyLogoUrl,
            avatarUrl,
            starred,
            read,
            preview
        );
    }

    @Override
    public String toString() {
        return "EmailMessage{" +
            "id='" + id + '\'' +
            ", contextId='" + contextId + '\'' +
            ", senderName='" + senderName + '\'' +
            ", senderEmail='" + senderEmail + '\'' +
            ", recipientName='" + recipientName + '\'' +
            ", recipientEmail='" + recipientEmail + '\'' +
            ", subject='" + subject + '\'' +
            ", read=" + read +
            ", starred=" + starred +
            '}';
    }

    public static class Builder extends BuilderBase<Builder> {
        protected Builder() {
        }

        protected Builder(EmailMessage source) {
            super(source);
        }

        @Override
        protected Builder self() {
            return this;
        }

        public EmailMessage build() {
            return new EmailMessage(this);
        }
    }

    protected static abstract class BuilderBase<T extends BuilderBase<T>> {
        private String id;
        private String contextId;
        private String senderName;
        private String senderEmail;
        private String recipientName;
        private String recipientEmail;
        private String subject;
        private String emailBodyRaw;
        private String emailBodyTransformedText;
        private String emailBodyTransformedMarkdown;
        private String emailBodyHtml;
        private String llmSummary;
        private String receivedTimestampIso;
        private String receivedTimestampDisplay;
        private List<String> labels;
        private String companyLogoUrl;
        private String avatarUrl;
        private boolean starred;
        private boolean read;
        private String preview;

        protected BuilderBase() {
        }

        protected BuilderBase(EmailMessage source) {
            this.id = source.id;
            this.contextId = source.contextId;
            this.senderName = source.senderName;
            this.senderEmail = source.senderEmail;
            this.recipientName = source.recipientName;
            this.recipientEmail = source.recipientEmail;
            this.subject = source.subject;
            this.emailBodyRaw = source.emailBodyRaw;
            this.emailBodyTransformedText = source.emailBodyTransformedText;
            this.emailBodyTransformedMarkdown = source.emailBodyTransformedMarkdown;
            this.emailBodyHtml = source.emailBodyHtml;
            this.llmSummary = source.llmSummary;
            this.receivedTimestampIso = source.receivedTimestampIso;
            this.receivedTimestampDisplay = source.receivedTimestampDisplay;
            this.labels = new ArrayList<>(source.labels);
            this.companyLogoUrl = source.companyLogoUrl;
            this.avatarUrl = source.avatarUrl;
            this.starred = source.starred;
            this.read = source.read;
            this.preview = source.preview;
        }

        protected abstract T self();

        public T id(String id) {
            this.id = id;
            return self();
        }

        public T contextId(String contextId) {
            this.contextId = contextId;
            return self();
        }

        public T senderName(String senderName) {
            this.senderName = senderName;
            return self();
        }

        public T senderEmail(String senderEmail) {
            this.senderEmail = senderEmail;
            return self();
        }

        public T recipientName(String recipientName) {
            this.recipientName = recipientName;
            return self();
        }

        public T recipientEmail(String recipientEmail) {
            this.recipientEmail = recipientEmail;
            return self();
        }

        public T subject(String subject) {
            this.subject = subject;
            return self();
        }

        public T emailBodyRaw(String emailBodyRaw) {
            this.emailBodyRaw = emailBodyRaw;
            return self();
        }

        public T emailBodyTransformedText(String emailBodyTransformedText) {
            this.emailBodyTransformedText = emailBodyTransformedText;
            return self();
        }

        public T emailBodyTransformedMarkdown(String emailBodyTransformedMarkdown) {
            this.emailBodyTransformedMarkdown = emailBodyTransformedMarkdown;
            return self();
        }

        public T emailBodyHtml(String emailBodyHtml) {
            this.emailBodyHtml = emailBodyHtml;
            return self();
        }

        public T llmSummary(String llmSummary) {
            this.llmSummary = llmSummary;
            return self();
        }

        public T receivedTimestampIso(String receivedTimestampIso) {
            this.receivedTimestampIso = receivedTimestampIso;
            return self();
        }

        public T receivedTimestampDisplay(String receivedTimestampDisplay) {
            this.receivedTimestampDisplay = receivedTimestampDisplay;
            return self();
        }

        public T labels(List<String> labels) {
            if (labels == null || labels.isEmpty()) {
                this.labels = null;
            } else {
                this.labels = new ArrayList<>(labels);
            }
            return self();
        }

        public T addLabel(String label) {
            if (label == null || label.isBlank()) {
                return self();
            }
            if (this.labels == null) {
                this.labels = new ArrayList<>();
            }
            this.labels.add(label);
            return self();
        }

        public T companyLogoUrl(String companyLogoUrl) {
            this.companyLogoUrl = companyLogoUrl;
            return self();
        }

        public T avatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
            return self();
        }

        public T starred(boolean starred) {
            this.starred = starred;
            return self();
        }

        public T read(boolean read) {
            this.read = read;
            return self();
        }

        public T preview(String preview) {
            this.preview = preview;
            return self();
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String derivePreview(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.strip();
        if (normalized.length() <= 180) {
            return normalized;
        }
        return normalized.substring(0, Math.min(normalized.length(), 177)) + "...";
    }

    private static String defaultIfBlank(String candidate, String fallback) {
        return (candidate == null || candidate.isBlank()) ? fallback : candidate;
    }

    private static String normalizeUrl(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String trimmed = candidate.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeHtml(String candidate) {
        if (candidate == null) {
            return null;
        }
        String trimmed = candidate.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isFallbackRecipient(String name, String email) {
        if (email == null) {
            return false;
        }
        if (!FALLBACK_RECIPIENT_EMAIL.equalsIgnoreCase(email)) {
            return false;
        }
        return name == null || name.isBlank() || FALLBACK_RECIPIENT_NAME.equalsIgnoreCase(name);
    }
}
