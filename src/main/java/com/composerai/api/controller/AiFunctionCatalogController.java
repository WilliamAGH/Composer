package com.composerai.api.controller;

import com.composerai.api.ai.AiFunctionCatalogHelper;
import com.composerai.api.dto.AiFunctionCatalogDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple REST surface so clients can refresh the AI function catalog without depending solely on
 * server-side bootstrapping.
 */
@RestController
@RequestMapping("/api/ai-functions")
public class AiFunctionCatalogController {

    private final AiFunctionCatalogHelper catalogHelper;

    public AiFunctionCatalogController(AiFunctionCatalogHelper catalogHelper) {
        this.catalogHelper = catalogHelper;
    }

    @GetMapping
    public AiFunctionCatalogDto catalog() {
        return catalogHelper.dto();
    }
}
