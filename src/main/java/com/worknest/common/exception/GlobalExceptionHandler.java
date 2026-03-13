package com.worknest.common.exception;

import com.worknest.common.api.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Global exception handler for the application.
 * Catches all exceptions and returns standardized error responses.
 * Logs errors for monitoring and debugging.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(
            BadRequestException ex, HttpServletRequest request) {
        logger.warn("Bad request: {} at {}", ex.getMessage(), request.getRequestURI());
        ErrorResponse error = ErrorResponse.of(ex.getMessage(), "BAD_REQUEST", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {
        logger.warn("Resource not found: {} at {}", ex.getMessage(), request.getRequestURI());
        ErrorResponse error = ErrorResponse.of(ex.getMessage(), "RESOURCE_NOT_FOUND", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTenantNotFoundException(
            TenantNotFoundException ex, HttpServletRequest request) {
        logger.warn("Tenant not found: {} at {}", ex.getMessage(), request.getRequestURI());
        ErrorResponse error = ErrorResponse.of(ex.getMessage(), "TENANT_NOT_FOUND", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(TenantResolutionException.class)
    public ResponseEntity<ErrorResponse> handleTenantResolutionException(
            TenantResolutionException ex, HttpServletRequest request) {
        logger.error("Tenant resolution error: {} at {}", ex.getMessage(), request.getRequestURI());
        ErrorResponse error = ErrorResponse.of(ex.getMessage(), "TENANT_RESOLUTION_ERROR", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle validation errors from @Valid annotations.
     * Returns all validation error messages.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        // Collect all validation errors
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));

        if (message.isEmpty()) {
            message = "Validation failed";
        }

        logger.warn("Validation error: {} at {}", message, request.getRequestURI());
        ErrorResponse error = ErrorResponse.of(message, "VALIDATION_ERROR", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {
        logger.warn("Invalid argument: {} at {}", ex.getMessage(), request.getRequestURI());
        ErrorResponse error = ErrorResponse.of(ex.getMessage(), "INVALID_ARGUMENT", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle all other unexpected exceptions.
     * Logs full stack trace for debugging.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex, HttpServletRequest request) {
        logger.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponse error = ErrorResponse.of(
                "An unexpected error occurred. Please contact support if the problem persists.",
                "INTERNAL_SERVER_ERROR",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Format field error for better readability
     */
    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}

