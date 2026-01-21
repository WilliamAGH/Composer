package com.composerai.api.controller;

import com.composerai.api.ai.AiFunctionCatalogHelper;
import com.composerai.api.config.AiFunctionCatalogProperties;
import com.composerai.api.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiFunctionCatalogController.class)
@Import(AiFunctionCatalogControllerTest.TestConfig.class)
class AiFunctionCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void catalogReturnsFunctions() throws Exception {
        mockMvc.perform(get("/api/ai-functions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.functionsByKey.compose").exists())
            .andExpect(jsonPath("$.functionsByKey.summarize").exists());
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        AiFunctionCatalogProperties aiFunctionCatalogProperties() {
            return new AiFunctionCatalogProperties();
        }

        @Bean
        AiFunctionCatalogHelper aiFunctionCatalogHelper(AiFunctionCatalogProperties properties) {
            return new AiFunctionCatalogHelper(properties);
        }

        @Bean
        AppProperties appProperties() {
            return new AppProperties();
        }
    }
}
