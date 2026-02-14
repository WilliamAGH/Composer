package com.composerai.api.exception;

import com.composerai.api.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global exception handler for all REST controllers.
 * Provides centralized, DRY error handling with consistent response format.
 * Logs all exceptions with full stack traces for server-side debugging.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle validation errors from @Valid annotations.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        log.debug("Validation failed for request to {} - {}", request.getRequestURI(), ex.getMessage());

        List<ErrorResponse.ValidationError> validationErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.ValidationError(
                        error.getField(), error.getDefaultMessage(), error.getRejectedValue()))
                .toList();

        ErrorResponse errorResponse = new ErrorResponse(
                "validation_error",
                "Validation failed for request",
                HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI(),
                validationErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle constraint violations from @Validated at method level.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        log.debug("Constraint violation for request to {} - {}", request.getRequestURI(), ex.getMessage());

        List<ErrorResponse.ValidationError> validationErrors = ex.getConstraintViolations().stream()
                .map(violation -> new ErrorResponse.ValidationError(
                        violation.getPropertyPath().toString(), violation.getMessage(), violation.getInvalidValue()))
                .toList();

        ErrorResponse errorResponse = new ErrorResponse(
                "constraint_violation",
                "Request validation failed",
                HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI(),
                validationErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle malformed JSON or request body parsing errors.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.debug("Malformed request body for {} - {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                "malformed_request",
                "Request body is malformed or cannot be parsed",
                HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle type conversion errors in request parameters.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        log.debug(
                "Type mismatch for parameter '{}' in request to {} - {}",
                ex.getName(),
                request.getRequestURI(),
                ex.getMessage());

        String message = String.format(
                "Parameter '%s' must be of type %s",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        ErrorResponse errorResponse =
                new ErrorResponse("type_mismatch", message, HttpStatus.BAD_REQUEST.value(), request.getRequestURI());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle file upload size exceeded errors.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {

        log.debug("File upload size exceeded for {}", request.getRequestURI());

        String message = "File size exceeds maximum allowed limit";
        long maxUploadSize = ex.getMaxUploadSize();
        if (maxUploadSize > 0) {
            message += String.format(" (%d bytes)", maxUploadSize);
        }

        ErrorResponse errorResponse = new ErrorResponse(
                "file_too_large", message, HttpStatus.PAYLOAD_TOO_LARGE.value(), request.getRequestURI());

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(errorResponse);
    }

    /**
     * Handle 404 - endpoint not found.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoHandlerFoundException ex, HttpServletRequest request) {

        log.debug("No handler found for {} {}", ex.getHttpMethod(), ex.getRequestURL());

        ErrorResponse errorResponse = new ErrorResponse(
                "not_found",
                String.format("No endpoint found for %s %s", ex.getHttpMethod(), ex.getRequestURL()),
                HttpStatus.NOT_FOUND.value(),
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handle static resource lookup failures. Only emit JSON for API requests; otherwise let the
     * default resource handling respond (prevents browsers requesting favicons from triggering
     * JSON serialization errors).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(NoResourceFoundException ex, HttpServletRequest request)
            throws NoResourceFoundException {

        if (!isApiRequest(request)) {
            // Non-API static asset – allow container to generate the usual 404 without JSON body.
            throw ex;
        }

        ErrorResponse errorResponse = new ErrorResponse(
                "not_found", "Requested resource was not found", HttpStatus.NOT_FOUND.value(), request.getRequestURI());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handle illegal argument exceptions (bad input).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {

        log.debug("Illegal argument for request to {} - {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                "invalid_argument",
                ex.getMessage() != null ? ex.getMessage() : "Invalid argument provided",
                HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle illegal state exceptions (server state issues).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {

        log.warn("Illegal state encountered for request to {}", request.getRequestURI(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
                "server_error",
                "The server is in an invalid state to process this request",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handle unsupported operation exceptions (unsupported features).
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedOperation(
            UnsupportedOperationException ex, HttpServletRequest request) {

        log.debug("Unsupported operation for request to {} - {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                "unsupported_operation",
                ex.getMessage() != null ? ex.getMessage() : "This operation is not supported",
                HttpStatus.NOT_IMPLEMENTED.value(),
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(errorResponse);
    }

    /**
     * Catch-all handler for any unhandled exceptions.
     * Logs full stack trace but returns safe generic message to client.
     *
     * IMPORTANT: Silently ignores browser diagnostic requests (Chrome DevTools, Firefox, etc.)
     * to prevent log pollution. These requests are normal browser behavior and should not
     * generate error traces in production logs.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request)
            throws Exception {

        String requestUri = request.getRequestURI();

        // Silently ignore browser diagnostic/devtools requests to prevent log pollution
        if (isBrowserDiagnosticRequest(requestUri)) {
            ErrorResponse errorResponse =
                    new ErrorResponse("not_found", "Resource not found", HttpStatus.NOT_FOUND.value(), requestUri);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }

        if (!isApiRequest(request)) {
            // Allow non-API requests (e.g., static assets) to bubble up to the default handlers.
            throw ex;
        }

        // Log full exception with stack trace for debugging
        log.error("Unhandled exception for request to {}", requestUri, ex);

        // Return generic error to client (don't leak internal details)
        ErrorResponse errorResponse = new ErrorResponse(
                "internal_error",
                "An unexpected error occurred while processing your request",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                requestUri);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Check if a request URI is a known browser diagnostic request.
     * These are automated requests from browsers' DevTools, extensions, or built-in features.
     *
     * @param uri The request URI to check
     * @return true if this is a known browser diagnostic request that should be silently ignored
     */
    private boolean isBrowserDiagnosticRequest(String uri) {
        return uri != null
                && (
                // Chrome DevTools configuration requests
                uri.contains("/.well-known/appspecific/")
                        || uri.contains("/com.chrome.devtools")
                        ||
                        // Firefox devtools
                        uri.contains("/.well-known/firefox/")
                        ||
                        // Common browser diagnostic endpoints
                        uri.endsWith(".map")
                        || uri.contains("/sourcemap")
                        || uri.contains("/__webpack_hmr"));
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/api/");
    }
}
