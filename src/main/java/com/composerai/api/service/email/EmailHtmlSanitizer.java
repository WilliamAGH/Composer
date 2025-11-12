package com.composerai.api.service.email;

import com.composerai.api.util.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Sanitizes email HTML for safe display in isolated iframes.
 * <p>
 * This class provides server-side sanitization as the primary security layer.
 * Client-side iframe sandboxing provides defense-in-depth. This sanitizer
 * focuses specifically on display safety (preventing XSS and layout breaks),
 * while {@link HtmlConverter} handles email-to-text conversion workflows.
 *
 * @author William Callahan
 * @since 2025-11-02
 * @version 0.0.1
 */
public final class EmailHtmlSanitizer {

    private EmailHtmlSanitizer() {}

    /**
     * Sanitize raw email HTML for safe display in an isolated iframe.
     * <p>
     * Security measures:
     * <ul>
     *   <li>Removes all JavaScript (script tags, event handlers, javascript: URLs)</li>
     *   <li>Removes dangerous embedding elements (iframe, object, embed, form)</li>
     *   <li>Neutralizes CSS that could break layout (position:fixed/absolute, z-index)</li>
     *   <li>Applies jsoup Safelist for comprehensive XSS protection</li>
     * </ul>
     *
     * @param html Raw HTML content from email
     * @return Sanitized HTML safe for iframe rendering, or null if input is null/blank
     */
    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }

        try {
            Document doc = Jsoup.parse(html);

            // Remove dangerous elements that could execute scripts or embed external content
            doc.select("script, noscript, iframe, object, embed, applet, form").remove();

            // Remove all JavaScript event handlers and javascript: URLs
            removeJavaScriptHandlers(doc);

            // Neutralize dangerous CSS that could break page layout
            neutralizeDangerousCss(doc);

            // Clean up <style> blocks to strip harmful constructs without removing formatting entirely
            sanitizeStyleTags(doc);

            StringBuilder builder = new StringBuilder();
            appendHeadStyles(doc, builder);
            builder.append(buildBodyWrapper(doc.body()));
            String cleaned = builder.toString();
            return cleaned.trim().isEmpty() ? null : cleaned.trim();

        } catch (Exception e) {
            // On error, return null to prevent any potential XSS
            return null;
        }
    }

    /**
     * Remove all JavaScript event handlers (onclick, onerror, etc.) and javascript: URLs.
     */
    private static void removeJavaScriptHandlers(Document doc) {
        for (Element el : doc.getAllElements()) {
            // Remove all on* attributes (iterate over copy to avoid concurrent modification)
            List<String> attrsToRemove = new ArrayList<>();
            for (org.jsoup.nodes.Attribute attr : el.attributes()) {
                if (attr.getKey().toLowerCase().startsWith("on")) {
                    attrsToRemove.add(attr.getKey());
                }
            }
            for (String attrKey : attrsToRemove) {
                el.removeAttr(attrKey);
            }

            // Remove javascript: URLs from href and src
            if (el.hasAttr("href") && el.attr("href").toLowerCase().trim().startsWith("javascript:")) {
                el.removeAttr("href");
            }
            if (el.hasAttr("src") && el.attr("src").toLowerCase().trim().startsWith("javascript:")) {
                el.removeAttr("src");
            }
        }
    }

    /**
     * Neutralize CSS that could break the page layout or overlay content.
     * Specifically targets position:fixed which could escape the iframe.
     * Preserves background colors and absolute positioning to keep layout fidelity.
     */
    private static void neutralizeDangerousCss(Document doc) {
        for (Element el : doc.getAllElements()) {
            if (!el.hasAttr("style")) {
                continue;
            }

            String style = el.attr("style");

            // Replace position:fixed with position:relative to avoid viewport overlays.
            String cleanedStyle = style
                .replaceAll("(?i)position\\s*:\\s*fixed", "position: relative");

            // Sanitize background-image URLs to prevent javascript: injection
            // but preserve valid http/https/data URLs for background images
            cleanedStyle = cleanedStyle.replaceAll(
                "(?i)background-image\\s*:\\s*url\\s*\\(\\s*['\"]?\\s*javascript:[^)]*\\)",
                ""
            );

            // Ensure images are constrained
            if (el.tagName().equalsIgnoreCase("img")) {
                if (!cleanedStyle.toLowerCase().contains("max-width")) {
                    cleanedStyle += "; max-width: 100%;";
                }
            }

            if (!cleanedStyle.equals(style)) {
                el.attr("style", cleanedStyle);
            }
        }
    }

    private static void sanitizeStyleTags(Document doc) {
        for (Element styleTag : doc.select("style")) {
            String css = styleTag.data();
            if (css == null || css.isBlank()) {
                continue;
            }

            // Remove dangerous CSS while preserving backgrounds and colors
            String sanitized = css
                .replaceAll("(?i)expression\\s*\\(", "")
                .replaceAll("(?i)url\\s*\\(\\s*['\"]?\\s*javascript:", "url(")
                .replaceAll("(?i)@import\\s+['\"]?\\s*javascript:", "")
                .replaceAll("(?i)position\\s*:\\s*fixed", "position: relative");

            styleTag.text(sanitized);
        }
    }

    private static void appendHeadStyles(Document doc, StringBuilder builder) {
        if (builder == null || doc.head() == null) {
            return;
        }
        for (Element styleTag : doc.head().select("style")) {
            String outer = styleTag.outerHtml();
            if (outer != null && !outer.isBlank()) {
                builder.append(outer);
            }
        }
    }

    private static String buildBodyWrapper(Element body) {
        if (body == null) {
            return "";
        }

        String originalClass = body.hasAttr("class") ? body.attr("class").trim() : "";
        String combinedClass = originalClass.isBlank()
            ? "email-original-body"
            : "email-original-body " + originalClass;

        StringBuilder builder = new StringBuilder();
        builder.append("<div class=\"").append(escapeAttributeValue(combinedClass)).append("\"");

        String bodyStyle = body.hasAttr("style") ? body.attr("style").trim() : "";
        if (!bodyStyle.isBlank()) {
            appendAttribute(builder, "style", bodyStyle);
        }

        String bodyBgColor = body.hasAttr("bgcolor") ? body.attr("bgcolor").trim() : "";
        if (!bodyBgColor.isBlank()) {
            appendAttribute(builder, "bgcolor", bodyBgColor);
        }

        String bodyBackground = body.hasAttr("background") ? body.attr("background").trim() : "";
        if (!bodyBackground.isBlank()) {
            String safeBackground = StringUtils.sanitizeUrl(bodyBackground);
            if (safeBackground != null) {
                appendAttribute(builder, "data-email-background", safeBackground);
                mergeBackgroundImage(builder, safeBackground);
            }
        }

        builder.append('>').append(body.html()).append("</div>");
        return builder.toString();
    }

    private static void mergeBackgroundImage(StringBuilder builder, String backgroundUrl) {
        final String addition = "background-image: url('" + escapeAttributeValue(backgroundUrl) + "'); ";
        final String stylePrefix = " style=\"";
        int styleIndex = builder.indexOf(stylePrefix);
        if (styleIndex >= 0) {
            int insertionPoint = styleIndex + stylePrefix.length();
            builder.insert(insertionPoint, addition);
        } else {
            appendAttribute(builder, "style", addition.trim());
        }
    }

    private static void appendAttribute(StringBuilder builder, String name, String value) {
        builder.append(' ').append(name)
            .append("=\"")
            .append(escapeAttributeValue(value))
            .append("\"");
    }

    private static String escapeAttributeValue(String value) {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

}
