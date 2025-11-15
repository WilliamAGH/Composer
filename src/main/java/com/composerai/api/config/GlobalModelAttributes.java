package com.composerai.api.config;

import com.composerai.api.ai.AiFunctionCatalogHelper;
import com.composerai.api.dto.AiFunctionCatalogDto;
import com.composerai.api.dto.SseEventType;
import com.composerai.api.service.ReasoningStreamAdapter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global model attributes available to all Thymeleaf templates.
 * Provides single source of truth for frontend constants derived from backend enums.
 *
 * Access in templates via th:inline="javascript" with inline expressions.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private static final String SITE_NAME = "Composer";
    private static final String DEFAULT_PAGE_TITLE = "Composer - AI email that drafts and manages your mailbox";
    private static final String DEFAULT_DESCRIPTION = "AI webmail that triages your inbox, summarizes threads, drafts replies, schedules meetings, pulls historical context, and ensures follow-up.";
    private static final String OG_IMAGE_ALT = "Composer UI showing AI triage labels, thread summary, and one-click reply suggestions.";
    private static final int OG_IMAGE_WIDTH = 1900;
    private static final int OG_IMAGE_HEIGHT = 1000;
    private static final String OG_IMAGE_PATH = "/opengraph.png";

    private final AppProperties appProperties;
    private final AiFunctionCatalogHelper aiFunctionCatalogHelper;

    public GlobalModelAttributes(ObjectProvider<AppProperties> appPropertiesProvider,
                                 ObjectProvider<AiFunctionCatalogHelper> aiFunctionCatalogHelperProvider) {
        this.appProperties = appPropertiesProvider.getIfAvailable(AppProperties::new);
        this.aiFunctionCatalogHelper = aiFunctionCatalogHelperProvider.getIfAvailable(
            () -> new AiFunctionCatalogHelper(new AiFunctionCatalogProperties())
        );
    }

    @ModelAttribute("siteName")
    public String siteName() {
        return SITE_NAME;
    }

    @ModelAttribute("defaultPageTitle")
    public String defaultPageTitle() {
        return DEFAULT_PAGE_TITLE;
    }

    @ModelAttribute("metaDescription")
    public String metaDescription() {
        return DEFAULT_DESCRIPTION;
    }

    @ModelAttribute("ogDescription")
    public String ogDescription() {
        return DEFAULT_DESCRIPTION;
    }

    @ModelAttribute("ogImageAlt")
    public String ogImageAlt() {
        return OG_IMAGE_ALT;
    }

    @ModelAttribute("ogImageWidth")
    public int ogImageWidth() {
        return OG_IMAGE_WIDTH;
    }

    @ModelAttribute("ogImageHeight")
    public int ogImageHeight() {
        return OG_IMAGE_HEIGHT;
    }

    @ModelAttribute("ogImageUrl")
    public String ogImageUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
            .path(OG_IMAGE_PATH)
            .build()
            .toUriString();
    }

    @ModelAttribute("canonicalUrl")
    public String canonicalUrl(HttpServletRequest request) {
        return ServletUriComponentsBuilder.fromRequestUri(request)
            .replaceQuery(null)
            .build()
            .toUriString();
    }

    /**
     * Exposes SSE event type mappings to all templates.
     * Maps enum names to their event string values (e.g., METADATA → "metadata").
     *
     * @return map of SSE event types (METADATA, RENDERED_HTML, DONE, ERROR, REASONING)
     */
    @ModelAttribute("sseEvents")
    public Map<String, String> sseEvents() {
        return Arrays.stream(SseEventType.values())
            .collect(Collectors.toMap(
                Enum::name,
                SseEventType::getEventName
            ));
    }

    /**
     * Exposes reasoning phase names to all templates.
     * Maps enum names to their string representation (e.g., THINKING → "THINKING").
     *
     * @return map of reasoning phases (THINKING, PROGRESS, STREAMING, FAILED)
     */
    @ModelAttribute("reasoningPhases")
    public Map<String, String> reasoningPhases() {
        return Arrays.stream(ReasoningStreamAdapter.Phase.values())
            .collect(Collectors.toMap(
                Enum::name,
                Enum::name
            ));
    }

    @ModelAttribute("emailRenderModes")
    public Map<String, String> emailRenderModes() {
        return Arrays.stream(AppProperties.EmailRenderMode.values())
            .collect(Collectors.toMap(
                Enum::name,
                Enum::name
            ));
    }

    @ModelAttribute("emailRenderMode")
    public String emailRenderMode() {
        AppProperties.EmailRendering rendering = appProperties.getEmailRendering();
        AppProperties.EmailRenderMode mode = rendering != null ? rendering.getMode() : null;
        return mode != null ? mode.name() : AppProperties.EmailRenderMode.HTML.name();
    }

    @ModelAttribute("aiFunctionCatalog")
    public AiFunctionCatalogDto aiFunctionCatalog() {
        return aiFunctionCatalogHelper.dto();
    }
}
