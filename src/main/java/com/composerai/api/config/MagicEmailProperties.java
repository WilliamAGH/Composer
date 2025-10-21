package com.composerai.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Magic Email Configuration - Single Source of Truth
 *
 * Configuration for AI-powered email insights and analysis features.
 * Override via application.properties or environment variables.
 *
 * Configuration structure:
 *   magic-email:
 *     insights:
 *       prompt: "..."
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "magic-email")
public class MagicEmailProperties {

    private Insights insights = new Insights();

    @Getter
    @Setter
    public static class Insights {
        private String prompt = """
            Analyze the email provided in the context above and extract concrete, actionable insights:

            1. Quick Summary: What is this email actually about? Be specific with names, topics, amounts, products mentioned.

            2. Action Items / Tasks: What specific tasks does the recipient need to do based on THIS email's content? \
            Only include actions explicitly mentioned or clearly implied by the email—don't add generic advice like "unsubscribe if not interested."

            3. Expected Responses: Are there specific questions to answer, decisions to make, or replies expected? Quote relevant parts.

            4. Deadlines & Time-Sensitive Info: List any dates, times, deadlines, or time-bound information mentioned in the email.

            5. Key Data Points: Important numbers, amounts, names, links, or references that stand out.

            6. Recommended Next Steps: Based on the actual email content, what are logical next actions?

            CRITICAL: Extract information FROM THE EMAIL CONTENT. Don't invent generic tasks. If a section doesn't apply \
            (e.g., no deadlines mentioned), say "None mentioned in email" rather than making up generic advice.""";
    }
}
