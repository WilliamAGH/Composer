package com.composerai.api.exception;

import com.composerai.api.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;

/**
 * Global exception handler for all REST controllers.
 * Provides centralized, DRY error handling with consistent response format.
 * Logs all exceptions with full stack traces for server-side debugging.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle validation errors from @Valid annotations.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        logger.warn("Validation failed for request to {}", request.getRequestURI(), ex);

        List<ErrorResponse.ValidationError> validationErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> new ErrorResponse.ValidationError(
                error.getField(),
                error.getDefaultMessage(),
                error.getRejectedValue()
            ))
            .toList();

        ErrorResponse errorResponse = new ErrorResponse(
            "validation_error",
            "Validation failed for request",
            HttpStatus.BAD_REQUEST.value(),
            request.getRequestURI(),
            validationErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle constraint violations from @Validated at method level.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        logger.warn("Constraint violation for request to {}", request.getRequestURI(), ex);

        List<ErrorResponse.ValidationError> validationErrors = ex.getConstraintViolations()
            .stream()
            .map(violation -> new ErrorResponse.ValidationError(
                violation.getPropertyPath().toString(),
                violation.getMessage(),
                violation.getInvalidValue()
            ))
            .toList();

        ErrorResponse errorResponse = new ErrorResponse(
            "constraint_violation",
            "Request validation failed",
            HttpStatus.BAD_REQUEST.value(),
            request.getRequestURI(),
            validationErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle malformed JSON or request body parsing errors.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        logger.warn("Malformed request body for {}", request.getRequestURI(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
            "malformed_request",
            "Request body is malformed or cannot be parsed",
            HttpStatus.BAD_REQUEST.value(),
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle type conversion errors in request parameters.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        logger.warn("Type mismatch for parameter '{}' in request to {}",
            ex.getName(), request.getRequestURI(), ex);

        String message = String.format(
            "Parameter '%s' must be of type %s",
            ex.getName(),
            ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"
        );

        ErrorResponse errorResponse = new ErrorResponse(
            "type_mismatch",
            message,
            HttpStatus.BAD_REQUEST.value(),
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle file upload size exceeded errors.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request) {

        logger.warn("File upload size exceeded for {}", request.getRequestURI(), ex);

        String message = "File size exceeds maximum allowed limit";
        long maxUploadSize = ex.getMaxUploadSize();
        if (maxUploadSize > 0) {
            message += String.format(" (%d bytes)", maxUploadSize);
        }

        ErrorResponse errorResponse = new ErrorResponse(
            "file_too_large",
            message,
            HttpStatus.PAYLOAD_TOO_LARGE.value(),
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(errorResponse);
    }

    /**
     * Handle 404 - endpoint not found.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NoHandlerFoundException ex,
            HttpServletRequest request) {

        logger.warn("No handler found for {} {}", ex.getHttpMethod(), ex.getRequestURL());

        ErrorResponse errorResponse = new ErrorResponse(
            "not_found",
            String.format("No endpoint found for %s %s", ex.getHttpMethod(), ex.getRequestURL()),
            HttpStatus.NOT_FOUND.value(),
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handle illegal argument exceptions (bad input).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        logger.warn("Illegal argument for request to {}", request.getRequestURI(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
            "invalid_argument",
            ex.getMessage() != null ? ex.getMessage() : "Invalid argument provided",
            HttpStatus.BAD_REQUEST.value(),
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle illegal state exceptions (server state issues).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request) {

        logger.error("Illegal state encountered for request to {}", request.getRequestURI(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
            "server_error",
            "The server is in an invalid state to process this request",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handle unsupported operation exceptions (unsupported features).
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedOperation(
            UnsupportedOperationException ex,
            HttpServletRequest request) {

        logger.warn("Unsupported operation for request to {}", request.getRequestURI(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
            "unsupported_operation",
            ex.getMessage() != null ? ex.getMessage() : "This operation is not supported",
            HttpStatus.NOT_IMPLEMENTED.value(),
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(errorResponse);
    }

    /**
     * Catch-all handler for any unhandled exceptions.
     * Logs full stack trace but returns safe generic message to client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        // Log full exception with stack trace for debugging
        logger.error("Unhandled exception for request to {}", request.getRequestURI(), ex);

        // Return generic error to client (don't leak internal details)
        ErrorResponse errorResponse = new ErrorResponse(
            "internal_error",
            "An unexpected error occurred while processing your request",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
