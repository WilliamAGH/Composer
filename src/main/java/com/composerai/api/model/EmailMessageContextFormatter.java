package com.composerai.api.model;

import com.composerai.api.util.StringUtils;
import com.composerai.api.util.TemporalUtils;

public final class EmailMessageContextFormatter {

    private EmailMessageContextFormatter() {}

    public static String buildContext(EmailMessage emailMessage) {
        if (emailMessage == null) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append(
                "=== Email Metadata (reference-only; summarize without restating these fields unless the user specifically asks) ===\n");
        context.append("Subject: ")
                .append(StringUtils.defaultIfBlank(emailMessage.subject(), "No subject"))
                .append(" [Do not copy into summary/translation unless explicitly requested]")
                .append('\n');
        context.append("From: ").append(StringUtils.defaultIfBlank(emailMessage.senderName(), "Unknown sender"));
        if (StringUtils.hasText(emailMessage.senderEmail())) {
            context.append(" <").append(emailMessage.senderEmail()).append('>');
        }
        context.append('\n');
        context.append("To: ").append(StringUtils.defaultIfBlank(emailMessage.recipientName(), "Unknown recipient"));
        if (StringUtils.hasText(emailMessage.recipientEmail())) {
            context.append(" <").append(emailMessage.recipientEmail()).append('>');
        }
        context.append('\n');

        String displayTimestamp = emailMessage.receivedTimestampDisplay();
        context.append("Email sent on: ")
                .append(StringUtils.defaultIfBlank(displayTimestamp, "Unknown date"))
                .append(" [Reference only; do not restate unless timing is central or explicitly requested]")
                .append('\n');

        if (StringUtils.hasText(emailMessage.receivedTimestampIso())) {
            String relative = TemporalUtils.getRelativeTime(emailMessage.receivedTimestampIso());
            if (StringUtils.hasText(relative)) {
                context.append("Time elapsed since email was sent: ")
                        .append(relative)
                        .append(" [Reference only; avoid restating unless relevant to the user's question]")
                        .append('\n');
                context.append(
                        "(Note: User questions about 'today' or 'now' refer to the CURRENT date in the system prompt above, NOT this email's date)\n");
            }
        }

        context.append('\n');
        context.append("=== Email Body ===\n");
        String body = StringUtils.firstNonBlank(
                emailMessage.emailBodyTransformedMarkdown(),
                emailMessage.emailBodyTransformedText(),
                emailMessage.emailBodyRaw());
        if (StringUtils.hasText(body)) {
            context.append(body.strip());
        } else {
            context.append("(Email body is empty)");
        }

        return context.toString();
    }
}
