package com.composerai.api.model;

import com.composerai.api.util.TemporalUtils;

public final class EmailMessageContextFormatter {

    private EmailMessageContextFormatter() {
    }

    public static String buildContext(EmailMessage emailMessage) {
        if (emailMessage == null) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("=== Email Metadata ===\n");
        context.append("Subject: ")
            .append(valueOrFallback(emailMessage.subject(), "No subject"))
            .append('\n');
        context.append("From: ")
            .append(valueOrFallback(emailMessage.senderName(), "Unknown sender"));
        if (isNotBlank(emailMessage.senderEmail())) {
            context.append(" <").append(emailMessage.senderEmail()).append('>');
        }
        context.append('\n');
        context.append("To: ")
            .append(valueOrFallback(emailMessage.recipientName(), "Unknown recipient"));
        if (isNotBlank(emailMessage.recipientEmail())) {
            context.append(" <").append(emailMessage.recipientEmail()).append('>');
        }
        context.append('\n');

        String displayTimestamp = emailMessage.receivedTimestampDisplay();
        context.append("Email sent on: ")
            .append(valueOrFallback(displayTimestamp, "Unknown date"))
            .append('\n');

        if (isNotBlank(emailMessage.receivedTimestampIso())) {
            String relative = TemporalUtils.getRelativeTime(emailMessage.receivedTimestampIso());
            if (isNotBlank(relative)) {
                context.append("Time elapsed since email was sent: ")
                    .append(relative)
                    .append('\n');
                context.append("(Note: User questions about 'today' or 'now' refer to the CURRENT date in the system prompt above, NOT this email's date)\n");
            }
        }

        context.append('\n');
        context.append("=== Email Body ===\n");
        String body = firstNonBlank(
            emailMessage.emailBodyTransformedMarkdown(),
            emailMessage.emailBodyTransformedText(),
            emailMessage.emailBodyRaw()
        );
        if (isNotBlank(body)) {
            context.append(body.strip());
        } else {
            context.append("(Email body is empty)");
        }

        return context.toString();
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String valueOrFallback(String value, String fallback) {
        return isNotBlank(value) ? value : fallback;
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (isNotBlank(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
