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

    @ExceptionHandler(DuplicateTenantKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateTenantKeyException(
            DuplicateTenantKeyException ex, HttpServletRequest request) {
        logger.warn("Duplicate tenant key: {} at {}", ex.getMessage(), request.getRequestURI());
        ErrorResponse error = ErrorResponse.of(ex.getMessage(), "DUPLICATE_TENANT_KEY", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmailException(
            DuplicateEmailException ex, HttpServletRequest request) {
        logger.warn("Duplicate email: {} at {}", ex.getMessage(), request.getRequestURI());
        ErrorResponse error = ErrorResponse.of(ex.getMessage(), "DUPLICATE_EMAIL", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentialsException(
            InvalidCredentialsException ex, HttpServletRequest request) {
        logger.warn("Invalid credentials at {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse error = ErrorResponse.of(ex.getMessage(), "INVALID_CREDENTIALS", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(InactiveUserException.class)
    public ResponseEntity<ErrorResponse> handleInactiveUserException(
            InactiveUserException ex, HttpServletRequest request) {
        logger.warn("Inactive user blocked at {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse error = ErrorResponse.of(ex.getMessage(), "INACTIVE_USER", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTokenException(
            InvalidTokenException ex, HttpServletRequest request) {
        logger.warn("Invalid token at {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse error = ErrorResponse.of(ex.getMessage(), "INVALID_TOKEN", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpiredException(
            TokenExpiredException ex, HttpServletRequest request) {
        logger.warn("Expired token at {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse error = ErrorResponse.of(ex.getMessage(), "TOKEN_EXPIRED", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(TokenRevokedException.class)
    public ResponseEntity<ErrorResponse> handleTokenRevokedException(
            TokenRevokedException ex, HttpServletRequest request) {
        logger.warn("Revoked token at {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse error = ErrorResponse.of(ex.getMessage(), "TOKEN_REVOKED", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenOperationException(
            ForbiddenOperationException ex, HttpServletRequest request) {
        logger.warn("Forbidden operation at {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse error = ErrorResponse.of(ex.getMessage(), "FORBIDDEN_OPERATION", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
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

