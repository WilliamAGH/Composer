/**
 * HtmlConverter: normalize HTML and convert to plain text or Markdown
 * with URL policies and basic cleanup
 * 
 * @author William Callahan
 * @since 2025-09-18
 * @version 0.0.1
 */
package com.composerai.api.service.email;

import com.composerai.api.service.HtmlToText;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

/**
 * HtmlConverter: normalize HTML and convert to plain text or Markdown
 * with URL policies and basic cleanup
 */
public final class HtmlConverter {

    private HtmlConverter() {}

    public static String convertHtml(String html, HtmlToText.OutputFormat format, HtmlToText.UrlPolicy urlsPolicy) {
        if (html == null || html.isBlank()) return "";
        String preprocessed = preprocessHtml(html, urlsPolicy);
        switch (format) {
            case MARKDOWN -> {
                FlexmarkHtmlConverter converter = FlexmarkHtmlConverter.builder().build();
                String md = converter.convert(preprocessed).trim();
                return cleanupOutput(md);
            }
            case PLAIN -> {
                String txt = htmlToPlain(preprocessed);
                if (txt == null || txt.isBlank()) {
                    String relaxed = preprocessHtml(html, HtmlToText.UrlPolicy.KEEP);
                    txt = htmlToPlain(relaxed);
                }
                return cleanupOutput(txt);
            }
            default -> {
                String txt = htmlToPlain(preprocessed);
                return cleanupOutput(txt);
            }
        }
    }

    /**
     * Minimal-custom-code HTML → plain text preserving line breaks
     */
    public static String htmlToPlain(String html) {
        Document doc = Jsoup.parse(html);
        for (Element br : doc.select("br")) br.after("\\n");
        for (Element p : doc.select("p")) { p.prependText("\n"); p.appendText("\n"); }
        for (Element li : doc.select("li")) { li.prepend("- "); li.appendText("\n"); }
        for (Element h : doc.select("h1, h2, h3, h4, h5, h6")) { h.prependText("\n"); h.appendText("\n"); }
        String text = Jsoup.parse(doc.html()).text();
        text = text.replace("\\n", "\n");
        text = text.replace('\u00A0', ' ');
        return text.trim();
    }

    /**
     * Remove noise, apply URL policy, and flatten layout tables
     */
    public static String preprocessHtml(String html, HtmlToText.UrlPolicy policy) {
        if (html == null || html.isBlank()) return html;
        Document doc = Jsoup.parse(html);

        // Convert <br> to real newlines early (helps both MD and TXT)
        for (Element br : doc.select("br")) br.replaceWith(new TextNode("\n"));

        // Remove non-content elements commonly present in marketing emails
        doc.select("script, style, noscript, svg, iframe").remove();

        // Remove utility/tracking links/icons (Mailchimp & common bulk-mail patterns)
        for (Element a : doc.select("a[href]")) {
            String href = a.attr("href").toLowerCase();
            String text = a.text().toLowerCase();
            boolean isTrackingHost = href.contains("list-manage.com")
                || href.contains("campaign-archive.com")
                || href.contains("mailchimpapp.net")
                || href.contains("track/click")
                || href.contains("track/open.php");
            boolean isUtilityText = text.contains("view this email")
                || text.contains("read in browser")
                || text.contains("share on twitter")
                || text.contains("share on facebook")
                || text.contains("email marketing powered by mailchimp")
                || text.contains("unsubscribe")
                || text.contains("update your preferences")
                || text.contains("add us to your address book");
            if (isTrackingHost || isUtilityText) a.remove();
        }
        for (Element img : doc.select("img[src]")) {
            String src = img.attr("src").toLowerCase();
            String alt = img.attr("alt");
            boolean isTrackingImg = src.contains("track/open.php")
                || src.contains("cdn-images.mailchimp.com/monkey_rewards")
                || src.contains("social_connect_tweet.png");
            if (isTrackingImg) {
                if (alt != null && !alt.isBlank()) img.replaceWith(new TextNode(alt));
                else img.remove();
            }
        }

        if (policy == HtmlToText.UrlPolicy.STRIP_ALL) {
            for (Element a : doc.select("a")) a.unwrap();
            for (Element img : doc.select("img")) {
                String alt = img.attr("alt");
                if (alt != null && !alt.isBlank()) img.replaceWith(new TextNode(alt));
                else img.remove();
            }
        } else if (policy == HtmlToText.UrlPolicy.CLEAN_ONLY) {
            for (Element a : doc.select("a[href]")) {
                String cleaned = sanitizeUrl(a.attr("href"));
                if (cleaned == null || cleaned.isBlank()) a.unwrap();
                else a.attr("href", cleaned);
            }
            for (Element img : doc.select("img[src]")) {
                String cleaned = sanitizeUrl(img.attr("src"));
                if (cleaned == null || cleaned.isBlank()) {
                    String alt = img.attr("alt");
                    if (alt != null && !alt.isBlank()) img.replaceWith(new TextNode(alt));
                    else img.remove();
                } else img.attr("src", cleaned);
            }

            // Remove entire paragraphs that are boilerplate utility/footer
            String[] utilityKeys = new String[] {
                "unsubscribe",
                "update your preferences",
                "view this email",
                "read in browser",
                "email marketing powered by mailchimp",
                "add us to your address book",
                "you are receiving this email because",
                "want to change how you receive these emails",
                "our mailing address is:"
            };
            for (Element el : doc.select("p, small, footer")) {
                String t = el.text().toLowerCase();
                for (String key : utilityKeys) {
                    if (t.contains(key)) { el.remove(); break; }
                }
            }
            // Remove targeted footer-like containers by known footer classes/ids
            for (Element el : doc.select(
                "div[class*='templateFooter'],div[id*='templateFooter'],div[class*='mcnFooter'],div[id*='mcn-footer'],div[class*='monkey_rewards'],div[class*='unsubscribe'],div[id*='unsubscribe'],div[class*='email-footer'],div[id*='email-footer']"
            )) {
                el.remove();
            }
        }

        // Flatten layout tables to avoid giant Markdown tables
        for (Element cell : doc.select("th, td")) { cell.appendText("\n"); cell.unwrap(); }
        for (Element wrapper : doc.select("table, thead, tbody, tfoot, tr")) wrapper.unwrap();

        return doc.html();
    }

    /**
     * Drop trackers/non-http schemes and query/fragment; limit pathological length
     */
    public static String sanitizeUrl(String url) {
        try {
            if (url == null || url.isBlank()) return null;
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();
            if (scheme == null) {
                String pathOnly = uri.getPath();
                return (pathOnly == null || pathOnly.isBlank()) ? null : pathOnly;
            }
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme) && !"mailto".equalsIgnoreCase(scheme)) return null;
            java.net.URI cleaned = new java.net.URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null);
            String out = cleaned.toString();
            if (out.length() > 2048) return null;
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Drop noisy separator lines, collapse excessive blanks, strip zero-width chars
     * and convert any residual <br> tags to newlines
     */
    public static String cleanupOutput(String content) {
        if (content == null || content.isBlank()) return content;
        content = normalizeInvisible(content)
            .replaceAll("(?i)<br\\s*/?>", "\n");
        String[] lines = content.split("\r?\n", -1);
        StringBuilder sb = new StringBuilder(content.length());
        int blankRun = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            String lower = trimmed.toLowerCase();
            if (trimmed.matches("^[\\-\\|_\\s]{50,}$")) continue;
            if (trimmed.equals("You can or .")) continue;
            if (lower.contains("you are receiving this email because")
                || lower.contains("email marketing powered by mailchimp")
                || lower.contains("want to change how you receive these emails")
                || lower.contains("our mailing address is:")
                || lower.contains("unsubscribe")
                || lower.contains("update your preferences")) continue;
            // Collapse extreme space runs inside a line
            line = line.replaceAll(" {10,}", " ");
            if (trimmed.isEmpty()) {
                blankRun++;
                if (blankRun > 2) continue;
            } else blankRun = 0;
            sb.append(line).append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * Remove zero-width and soft-hyphen characters that pollute outputs
     */
    private static String normalizeInvisible(String s) {
        return s.replaceAll("[\\u200B\\u200C\\u200D\\uFEFF\\u2060\\u00AD]", "");
    }
}


