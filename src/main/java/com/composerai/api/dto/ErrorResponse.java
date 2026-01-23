package com.composerai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standard error response DTO using Java record for immutability.
 * Provides consistent error structure across all API endpoints.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String error,
    String message,
    int status,
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime timestamp,
    String path,
    List<ValidationError> validationErrors
) {
    /**
     * Create error response with timestamp.
     */
    public ErrorResponse(String error, String message, int status, String path) {
        this(error, message, status, LocalDateTime.now(), path, null);
    }

    /**
     * Create error response with validation errors.
     */
    public ErrorResponse(String error, String message, int status, String path, List<ValidationError> validationErrors) {
        this(error, message, status, LocalDateTime.now(), path, validationErrors);
    }

    /**
     * Simple error response.
     */
    public ErrorResponse(String message, int status) {
        this("error", message, status, LocalDateTime.now(), null, null);
    }

    /**
     * Validation error detail record.
     */
    public record ValidationError(
        String field,
        String message,
        Object rejectedValue
    ) {}
}
