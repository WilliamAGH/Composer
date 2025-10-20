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
import com.composerai.api.util.StringUtils;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.parser.ParserEmulationProfile;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

/**
 * HtmlConverter: normalize HTML and convert to plain text or Markdown
 * with URL policies and basic cleanup.
 */
public final class HtmlConverter {

    private static final MarkdownRenderer MARKDOWN_RENDERER = new MarkdownRenderer();

    private HtmlConverter() {}

    /**
     * Render Markdown content to sanitized HTML using the shared Flexmark + JSoup pipeline.
     */
    public static String markdownToSafeHtml(String markdown) {
        return MARKDOWN_RENDERER.render(markdown);
    }

    public static String convertHtml(String html, HtmlToText.OutputFormat format, HtmlToText.UrlPolicy urlsPolicy) {
        return convertHtml(html, format, urlsPolicy, true);
    }

    public static String convertHtml(String html, HtmlToText.OutputFormat format, HtmlToText.UrlPolicy urlsPolicy, boolean suppressUtility) {
        if (html == null || html.isBlank()) return "";

        String preprocessed = preprocessHtml(html, urlsPolicy, suppressUtility);
        FlexmarkHtmlConverter converter = FlexmarkHtmlConverter.builder().build();

        return switch (format) {
            case MARKDOWN -> {
                String md = converter.convert(preprocessed).trim();
                yield cleanupOutput(md, suppressUtility);
            }
            case PLAIN -> {
                String md = converter.convert(preprocessed).trim();
                String txt = markdownToPlain(md);
                if ((txt == null || txt.isBlank()) && !suppressUtility) {
                    String relaxed = preprocessHtml(html, HtmlToText.UrlPolicy.KEEP, false);
                    txt = markdownToPlain(converter.convert(relaxed).trim());
                }
                yield cleanupOutput(txt, suppressUtility);
            }
            default -> cleanupOutput(htmlToPlain(preprocessed), suppressUtility);
        };
    }

    /**
     * Minimal-custom-code HTML → plain text preserving line breaks.
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
     * Remove noise, apply URL policy, and flatten layout tables.
     */
    public static String preprocessHtml(String html, HtmlToText.UrlPolicy policy, boolean suppressUtility) {
        if (html == null || html.isBlank()) return html;
        Document doc = Jsoup.parse(html);

        // Convert <br> to real newlines early (helps both MD and TXT)
        for (Element br : doc.select("br")) br.replaceWith(new TextNode("\n"));

        // Remove non-content elements commonly present in marketing emails
        doc.select("script, style, noscript, svg, iframe").remove();

        // URL-related handling for anchors and images
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
            if (isTrackingHost) {
                a.remove();
            } else if (suppressUtility && isUtilityText) {
                a.remove();
            } else if (policy == HtmlToText.UrlPolicy.STRIP_ALL) {
                a.unwrap();
            } else if (policy == HtmlToText.UrlPolicy.CLEAN_ONLY) {
                String cleaned = StringUtils.sanitizeUrl(a.attr("href"));
                if (cleaned == null || cleaned.isBlank()) a.unwrap();
                else a.attr("href", cleaned);
            }
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
            } else if (policy == HtmlToText.UrlPolicy.STRIP_ALL) {
                if (alt != null && !alt.isBlank()) img.replaceWith(new TextNode(alt));
                else img.remove();
            } else if (policy == HtmlToText.UrlPolicy.CLEAN_ONLY) {
                String cleaned = StringUtils.sanitizeUrl(img.attr("src"));
                if (cleaned == null || cleaned.isBlank()) {
                    if (alt != null && !alt.isBlank()) img.replaceWith(new TextNode(alt));
                    else img.remove();
                } else img.attr("src", cleaned);
            }
        }

        // Optional utility/footer block removal
        if (suppressUtility) {
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
            for (Element el : doc.select(
                "div[class*='templateFooter'],div[id*='templateFooter'],div[class*='mcnFooter'],div[id*='mcn-footer'],div[class*='monkey_rewards'],div[class*='unsubscribe'],div[id*='unsubscribe'],div[class*='email-footer'],div[id*='email-footer']"
            )) {
                el.remove();
            }
        }

        // Add paragraph separators for table rows before unwrapping to preserve item breaks
        for (Element row : doc.select("tr")) {
            row.appendText("\n\n");
        }

        // Flatten layout tables to avoid giant Markdown tables
        for (Element cell : doc.select("th, td")) { cell.appendText("\n"); cell.unwrap(); }
        for (Element wrapper : doc.select("table, thead, tbody, tfoot, tr")) wrapper.unwrap();

        // Insert paragraph breaks around inline emphasis to avoid run-on lines after conversion
        for (Element inl : doc.select("em, i, strong, b")) {
            inl.after(new TextNode("\n\n"));
        }

        // Wrap text nodes into paragraphs: split on double-newlines; single newlines -> <br>
        wrapTextNodesIntoParagraphs(doc, "body, div, section, article, main");

        return doc.html();
    }

    private static void wrapTextNodesIntoParagraphs(Document doc, String selectors) {
        for (Element el : doc.select(selectors)) {
            // copy to avoid concurrent modification
            java.util.List<TextNode> textNodes = new java.util.ArrayList<>(el.textNodes());
            for (TextNode tn : textNodes) {
                String raw = tn.getWholeText();
                if (raw == null) continue;
                String normalized = raw.replace("\r\n", "\n");
                if (normalized.trim().isEmpty()) continue;
                String[] paragraphs = normalized.split("\n{2,}");
                int idx = el.childNodes().indexOf(tn);
                Node ref = tn;
                tn.remove();
                for (int i = 0; i < paragraphs.length; i++) {
                    String para = paragraphs[i];
                    if (para == null || para.trim().isEmpty()) continue;
                    Element p = doc.createElement("p");
                    String[] lines = para.split("\n");
                    for (int j = 0; j < lines.length; j++) {
                        String line = lines[j];
                        if (!line.isEmpty()) p.appendChild(new TextNode(line));
                        if (j < lines.length - 1) p.appendChild(doc.createElement("br"));
                    }
                    if (ref != null && idx >= 0 && idx <= el.childNodeSize()) {
                        el.insertChildren(idx, p);
                        idx++;
                    } else {
                        el.appendChild(p);
                    }
                }
            }
        }
    }

    /**
     * Convert Markdown to plain text while preserving line/paragraph breaks.
     */
    public static String markdownToPlain(String md) {
        if (md == null || md.isBlank()) return md;
        String out = md;
        out = out.replaceAll("!\\[([^\\]]*)\\]\\([^\\)]*\\)", "$1");
        out = out.replaceAll("\\[([^\\]]+)\\]\\(([^\\)]*)\\)", "$1");
        out = out.replaceAll("(?m)^[#]{1,6}\\s*", "");
        out = out.replaceAll("[*_`~]+", "");
        out = out.replaceAll("  \\n", "\n");
        out = out.replaceAll("\n{3,}", "\n\n");
        return out.trim();
    }

    /**
     * Drop noisy separator lines, collapse excessive blanks, strip zero-width chars
     * and convert any residual <br> tags to newlines.
     */
    public static String cleanupOutput(String content) {
        return cleanupOutput(content, true);
    }

    public static String cleanupOutput(String content, boolean suppressUtility) {
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
            if (suppressUtility) {
                if (trimmed.equals("You can or .")) continue;
                if (lower.contains("you are receiving this email because")
                    || lower.contains("email marketing powered by mailchimp")
                    || lower.contains("want to change how you receive these emails")
                    || lower.contains("our mailing address is:")
                    || lower.contains("unsubscribe")
                    || lower.contains("update your preferences")) continue;
            }
            line = line.replaceAll(" {10,}", " ");
            if (trimmed.isEmpty()) {
                blankRun++;
                if (blankRun > 2) continue;
            } else blankRun = 0;
            sb.append(line).append('\n');
        }
        String out = sb.toString().trim();
        out = out.replaceAll("\n\\s*,\\s*\n", ", ");
        // Generic: insert paragraph breaks before emphasized By-sections common in newsletters
        out = out.replaceAll("(?<!\n)\\*By\\s", "\n\n*By ");
        return out;
    }

    /**
     * Remove zero-width and soft-hyphen characters that pollute outputs.
     */
    private static String normalizeInvisible(String s) {
        return s.replaceAll("[\\u200B\\u200C\\u200D\\uFEFF\\u2060\\u00AD]", "");
    }

    private static final class MarkdownRenderer {
        private final Parser parser;
        private final HtmlRenderer renderer;
        private final Cleaner cleaner;

        MarkdownRenderer() {
            MutableDataSet options = new MutableDataSet();
            ParserEmulationProfile.GITHUB_DOC.setIn(options);
            this.parser = Parser.builder(options).build();
            this.renderer = HtmlRenderer.builder(options)
                .escapeHtml(true)
                .percentEncodeUrls(true)
                .softBreak("\n")
                .build();

            Safelist safelist = Safelist.basicWithImages();
            safelist.addTags("table", "thead", "tbody", "tfoot", "tr", "th", "td", "pre", "code");
            safelist.addAttributes("a", "href", "title", "rel", "target");
            safelist.addAttributes("code", "class");
            this.cleaner = new Cleaner(safelist);
        }

        synchronized String render(String markdown) {
            if (markdown == null || markdown.isBlank()) {
                return "";
            }
            com.vladsch.flexmark.util.ast.Node document = parser.parse(markdown);
            String renderedHtml = renderer.render(document);
            Document dirty = Jsoup.parseBodyFragment(renderedHtml);
            Document clean = cleaner.clean(dirty);
            clean.outputSettings().prettyPrint(false);

            // Collapse soft line breaks rendered as <br> when models emit single newlines mid-sentence.
            // Preserve explicit breaks in semantic containers (lists, tables, code blocks).
            clean.select("body > br").remove();
            clean.select("p").forEach(p -> {
                p.select("br").stream()
                    .filter(br -> !hasStructuralParent(br))
                    .forEach(org.jsoup.nodes.Element::remove);
                normalizeWhitespace(p);
            });
            for (Element anchor : clean.select("a[href]")) {
                anchor.attr("rel", "noopener noreferrer");
                anchor.attr("target", "_blank");
            }
            return clean.body().html();
        }

        private boolean hasStructuralParent(Element element) {
            for (Element parent = element.parent(); parent != null; parent = parent.parent()) {
                if (parent.normalName().matches("pre|code|ul|ol|li|table|thead|tbody|tfoot|tr|th|td")) {
                    return true;
                }
            }
            return false;
        }

        private void normalizeWhitespace(Element element) {
            element.textNodes().forEach(node -> node.text(node.text().replaceAll("\\s+", " ")));
        }
    }
}
