package com.composerai.api.application.usecase.chat;

import com.composerai.api.service.ContextBuilder;
import com.composerai.api.service.email.HtmlConverter;
import com.composerai.api.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Stores sanitized draft context for a later catalog command. */
@Service
public class StoreDraftContextUseCase {

    private static final Logger logger = LoggerFactory.getLogger(StoreDraftContextUseCase.class);

    private final ContextBuilder.EmailContextCache emailContextRegistry;

    public StoreDraftContextUseCase(ContextBuilder.EmailContextCache emailContextRegistry) {
        this.emailContextRegistry = emailContextRegistry;
    }

    public void store(String contextId, String content) {
        String safeId = StringUtils.trimToNull(contextId);
        String sanitizedContent = HtmlConverter.cleanupOutput(content, false);
        if (safeId == null) {
            logger.warn("Cannot store draft context with blank contextId");
            return;
        }
        if (StringUtils.isBlank(sanitizedContent)) {
            logger.warn("Skipping draft context store for {} because content is blank", safeId);
            return;
        }
        emailContextRegistry.store(safeId, sanitizedContent);
        logger.debug("Stored draft context: id={}, length={}", safeId, sanitizedContent.length());
    }
}
