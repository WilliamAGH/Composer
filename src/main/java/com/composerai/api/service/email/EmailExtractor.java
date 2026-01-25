/**
 * EmailExtractor: MIME/EML helpers to extract HTML/text and decode headers
 *
 * @author William Callahan
 * @since 2025-09-18
 * @version 0.0.1
 */
package com.composerai.api.service.email;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import java.io.InputStream;
import java.util.Date;
import java.util.Optional;

/**
 * EmailExtractor: MIME/EML helpers to extract HTML/text and decode headers
 */
public final class EmailExtractor {

    private EmailExtractor() {}

    public static Optional<String> extractFirstHtml(Part part) throws Exception {
        if (part.isMimeType("text/html")) {
            Object content = part.getContent();
            if (content instanceof String s) return Optional.of(s);
        }
        if (part.isMimeType("multipart/alternative")) {
            MimeMultipart mp = (MimeMultipart) part.getContent();
            String html = null;
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                if (bp.isMimeType("text/html")) {
                    Object c = bp.getContent();
                    if (c instanceof String s) html = s;
                }
            }
            if (html != null) return Optional.of(html);
        }
        if (part.isMimeType("multipart/*")) {
            MimeMultipart mp = (MimeMultipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                Optional<String> nested = extractFirstHtml((Part) bp);
                if (nested.isPresent()) return nested;
            }
        }
        if (part.isMimeType("message/rfc822")) {
            Object content = part.getContent();
            if (content instanceof MimeMessage nested) {
                return extractFirstHtml(nested);
            }
        }
        return Optional.empty();
    }

    public static Optional<String> extractFirstPlainText(Part part) throws Exception {
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            if (content instanceof String s) return Optional.of(s);
        }
        if (part.isMimeType("multipart/*")) {
            MimeMultipart mp = (MimeMultipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                Optional<String> nested = extractFirstPlainText((Part) bp);
                if (nested.isPresent()) return nested;
            }
        }
        if (part.isMimeType("message/rfc822")) {
            Object content = part.getContent();
            if (content instanceof MimeMessage nested) {
                return extractFirstPlainText(nested);
            }
        }
        return Optional.empty();
    }

    public static MimeMessage loadMessage(Session session, InputStream in) throws Exception {
        return new MimeMessage(session, in);
    }

    public static String formatAddresses(Address[] addresses) {
        if (addresses == null || addresses.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < addresses.length; i++) {
            Address addr = addresses[i];
            String out;
            if (addr instanceof InternetAddress ia) {
                String personal = ia.getPersonal();
                String email = ia.getAddress();
                try {
                    if (personal != null && !personal.isBlank()) personal = MimeUtility.decodeText(personal);
                } catch (Exception ignore) {
                }
                out = (personal != null && !personal.isBlank())
                        ? personal + " <" + email + ">"
                        : (email != null ? email : ia.toString());
            } else {
                String raw = addr.toString();
                try {
                    out = MimeUtility.decodeText(raw);
                } catch (Exception e) {
                    out = raw;
                }
            }
            sb.append(out);
            if (i < addresses.length - 1) sb.append("; ");
        }
        return sb.toString();
    }

    /**
     * Build a plain metadata header block (used by both plain and markdown flows)
     */
    public static String buildMetadataHeader(
            MimeMessage message, com.composerai.api.service.HtmlToText.OutputFormat format) {
        try {
            String from = formatAddresses(message.getFrom());
            String to = formatAddresses(message.getRecipients(jakarta.mail.Message.RecipientType.TO));
            String cc = formatAddresses(message.getRecipients(jakarta.mail.Message.RecipientType.CC));
            String subject = Optional.ofNullable(message.getSubject()).orElse("");
            Date sent = message.getSentDate();
            String iso = sent != null
                    ? java.time.OffsetDateTime.ofInstant(sent.toInstant(), java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    : "";
            StringBuilder sb = new StringBuilder(256);
            sb.append("Sender: ").append(from).append('\n');
            sb.append("Recipient(s): ").append(joinRecipients(to, cc)).append('\n');
            sb.append("Date/time: ").append(iso).append('\n');
            sb.append("Subject: ").append(subject).append("\n\n");
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String joinRecipients(String to, String cc) {
        if (to == null) to = "";
        if (cc == null) cc = "";
        if (!to.isBlank() && !cc.isBlank()) return to + "; " + cc;
        return !to.isBlank() ? to : cc;
    }
}
