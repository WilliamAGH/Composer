package com.composerai.api.service.email;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

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
            doc.select("script, style, noscript, iframe, object, embed, applet, form, input, button, base, meta, link").remove();

            // Remove all JavaScript event handlers and javascript: URLs
            removeJavaScriptHandlers(doc);

            // Neutralize dangerous CSS that could break page layout
            neutralizeDangerousCss(doc);

            // Apply jsoup Safelist for additional sanitization (defense in depth)
            Safelist safelist = buildEmailDisplaySafelist();
            String bodyHtml = doc.body().html();
            String cleaned = Jsoup.clean(bodyHtml, safelist);

            return cleaned.trim().isEmpty() ? null : cleaned;

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
            // Remove all on* attributes
            el.attributes().asList().removeIf(attr ->
                attr.getKey().toLowerCase().startsWith("on")
            );

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
     * Specifically targets position:fixed and position:absolute which could escape the iframe.
     */
    private static void neutralizeDangerousCss(Document doc) {
        for (Element el : doc.getAllElements()) {
            if (!el.hasAttr("style")) {
                continue;
            }

            String style = el.attr("style");

            // Replace position:fixed and position:absolute with position:relative
            // This prevents email content from escaping its container
            String cleanedStyle = style
                .replaceAll("(?i)position\\s*:\\s*fixed", "position: relative")
                .replaceAll("(?i)position\\s*:\\s*absolute", "position: relative");

            // Remove z-index to prevent content overlaying UI
            cleanedStyle = cleanedStyle.replaceAll("(?i)z-index\\s*:\\s*[^;]+;?", "");

            // Ensure images are constrained
            if (el.tagName().equalsIgnoreCase("img")) {
                if (!cleanedStyle.toLowerCase().contains("max-width")) {
                    cleanedStyle += "; max-width: 100%;";
                }
            }

            el.attr("style", cleanedStyle);
        }
    }

    /**
     * Build a Safelist tailored for email HTML display.
     * Allows common email formatting while blocking dangerous elements.
     */
    private static Safelist buildEmailDisplaySafelist() {
        return Safelist.relaxed()
            // Allow data URIs for inline images (common in emails)
            .addProtocols("img", "src", "http", "https", "data")
            // Additional attributes for email styling
            .addAttributes("img", "alt", "title", "width", "height", "style")
            .addAttributes("table", "border", "cellpadding", "cellspacing", "style", "width")
            .addAttributes("td", "colspan", "rowspan", "style", "width")
            .addAttributes("th", "colspan", "rowspan", "style", "width")
            .addAttributes("div", "style", "class")
            .addAttributes("span", "style", "class")
            .addAttributes("p", "style", "class")
            .addAttributes("a", "style", "class", "href", "title", "rel", "target")
            .addAttributes(":all", "class");
    }
}
