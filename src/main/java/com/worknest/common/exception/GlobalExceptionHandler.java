package com.worknest.common.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.worknest.common.api.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(
            BadRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request, ex, false);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request, ex, false);
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTenantNotFoundException(
            TenantNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", ex.getMessage(), request, ex, false);
    }

    @ExceptionHandler(TenantResolutionException.class)
    public ResponseEntity<ErrorResponse> handleTenantResolutionException(
            TenantResolutionException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "TENANT_RESOLUTION_ERROR", ex.getMessage(), request, ex, true);
    }

    @ExceptionHandler(TenantContextMissingException.class)
    public ResponseEntity<ErrorResponse> handleTenantContextMissingException(
            TenantContextMissingException ex, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                "TENANT_CONTEXT_MISSING",
                "Tenant context is required for this request",
                request,
                ex,
                false
        );
    }

    @ExceptionHandler(DuplicateTenantKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateTenantKeyException(
            DuplicateTenantKeyException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "DUPLICATE_TENANT_KEY", ex.getMessage(), request, ex, false);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmailException(
            DuplicateEmailException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", ex.getMessage(), request, ex, false);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentialsException(
            InvalidCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage(), request, ex, false);
    }

    @ExceptionHandler(InactiveUserException.class)
    public ResponseEntity<ErrorResponse> handleInactiveUserException(
            InactiveUserException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "INACTIVE_USER", ex.getMessage(), request, ex, false);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTokenException(
            InvalidTokenException ex, HttpServletRequest request) {
        String errorCode = isRefreshEndpoint(request) ? "REFRESH_TOKEN_INVALID" : "INVALID_TOKEN";
        return build(HttpStatus.UNAUTHORIZED, errorCode, ex.getMessage(), request, ex, false);
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpiredException(
            TokenExpiredException ex, HttpServletRequest request) {
        String errorCode = isRefreshEndpoint(request) ? "REFRESH_TOKEN_EXPIRED" : "TOKEN_EXPIRED";
        return build(HttpStatus.UNAUTHORIZED, errorCode, ex.getMessage(), request, ex, false);
    }

    @ExceptionHandler(TokenRevokedException.class)
    public ResponseEntity<ErrorResponse> handleTokenRevokedException(
            TokenRevokedException ex, HttpServletRequest request) {
        String errorCode = isRefreshEndpoint(request) ? "REFRESH_TOKEN_REVOKED" : "TOKEN_REVOKED";
        return build(HttpStatus.UNAUTHORIZED, errorCode, ex.getMessage(), request, ex, false);
    }

    @ExceptionHandler(PasswordResetTokenInvalidException.class)
    public ResponseEntity<ErrorResponse> handlePasswordResetTokenInvalidException(
            PasswordResetTokenInvalidException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_TOKEN_INVALID", ex.getMessage(), request, ex, false);
    }

    @ExceptionHandler(PasswordResetTokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handlePasswordResetTokenExpiredException(
            PasswordResetTokenExpiredException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_TOKEN_EXPIRED", ex.getMessage(), request, ex, false);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenOperationException(
            ForbiddenOperationException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION", ex.getMessage(), request, ex, false);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> ErrorResponse.FieldValidationError.builder()
                        .field(fieldError.getField())
                        .message(fieldError.getDefaultMessage())
                        .rejectedValue(fieldError.getRejectedValue())
                        .build())
                .toList();
        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Validation failed",
                request,
                ex,
                false,
                errors
        );
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(
            BindException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldValidationError> errors = ex.getFieldErrors().stream()
                .map(fieldError -> ErrorResponse.FieldValidationError.builder()
                        .field(fieldError.getField())
                        .message(fieldError.getDefaultMessage())
                        .rejectedValue(fieldError.getRejectedValue())
                        .build())
                .toList();
        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Validation failed",
                request,
                ex,
                false,
                errors
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage(), request, ex, false);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldValidationError> errors = ex.getConstraintViolations().stream()
                .map(violation -> ErrorResponse.FieldValidationError.builder()
                        .field(violation.getPropertyPath() == null ? null : violation.getPropertyPath().toString())
                        .message(violation.getMessage())
                        .rejectedValue(violation.getInvalidValue())
                        .build())
                .toList();
        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Validation failed",
                request,
                ex,
                false,
                errors
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Invalid value for parameter '" + ex.getName() + "'";
        return build(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message, request, ex, false);
    }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        Throwable root = getRootCause(ex);
        if (root instanceof InvalidFormatException invalidFormatException
            && invalidFormatException.getTargetType() != null
            && invalidFormatException.getTargetType().isEnum()) {
            String field = invalidFormatException.getPath().isEmpty()
                ? "request"
                : invalidFormatException.getPath().get(invalidFormatException.getPath().size() - 1).getFieldName();
            String allowedValues = Arrays.stream(invalidFormatException.getTargetType().getEnumConstants())
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
            String message = "Invalid value '" + invalidFormatException.getValue() + "' for '" + field
                + "'. Allowed values: [" + allowedValues + "]";
            return build(HttpStatus.BAD_REQUEST, "INVALID_ENUM_VALUE", message, request, ex, false);
        }

        return build(
            HttpStatus.BAD_REQUEST,
            "MALFORMED_REQUEST",
            "Malformed request payload",
            request,
            ex,
            false
        );
        }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        return build(
                HttpStatus.CONFLICT,
                "DATA_INTEGRITY_VIOLATION",
                "Request violates data integrity constraints",
                request,
                ex,
                true
        );
    }

    @ExceptionHandler({DataAccessException.class, JpaSystemException.class, SQLException.class})
    public ResponseEntity<ErrorResponse> handleDatabaseException(
            Exception ex, HttpServletRequest request) {
        Throwable rootCause = getRootCause(ex);
        if (rootCause instanceof TenantContextMissingException tenantContextMissingException) {
            return handleTenantContextMissingException(tenantContextMissingException, request);
        }
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "DATABASE_ERROR",
                "A database error occurred while processing your request",
                request,
                ex,
                true
        );
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public ResponseEntity<ErrorResponse> handleMultipartException(
            Exception ex, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                "MULTIPART_ERROR",
                "Invalid multipart request or file size exceeded the configured limit",
                request,
                ex,
                false
        );
    }

    @ExceptionHandler(EmailServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleEmailServiceUnavailableException(
            EmailServiceUnavailableException ex, HttpServletRequest request) {
        return build(
                HttpStatus.SERVICE_UNAVAILABLE,
                "EMAIL_SERVICE_UNAVAILABLE",
                "Unable to send email right now. Please try again shortly.",
                request,
                ex,
                true
        );
    }

    @ExceptionHandler({RequestTimeoutException.class, AsyncRequestTimeoutException.class})
    public ResponseEntity<ErrorResponse> handleRequestTimeoutException(
            Exception ex, HttpServletRequest request) {
        return build(
                HttpStatus.GATEWAY_TIMEOUT,
                "REQUEST_TIMEOUT",
                "The request took too long to complete. Please retry.",
                request,
                ex,
                true
        );
    }

    @ExceptionHandler({DownstreamCommunicationException.class, ResourceAccessException.class, MailException.class})
    public ResponseEntity<ErrorResponse> handleDownstreamCommunicationException(
            Exception ex, HttpServletRequest request) {
        String code = ex instanceof MailException ? "EMAIL_SERVICE_UNAVAILABLE" : "DOWNSTREAM_COMMUNICATION_FAILURE";
        String message = ex instanceof MailException
                ? "Unable to send email right now. Please try again shortly."
                : "Unable to complete the request due to downstream communication failure.";
        HttpStatus status = ex instanceof MailException ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        return build(status, code, message, request, ex, true);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex, HttpServletRequest request) {
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please contact support if the problem persists.",
                request,
                ex,
                true
        );
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private boolean isRefreshEndpoint(HttpServletRequest request) {
        return "/api/auth/refresh".equals(request.getRequestURI());
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Exception exception,
            boolean logStackTrace) {
        return build(status, code, message, request, exception, logStackTrace, List.of());
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Exception exception,
            boolean logStackTrace,
            List<ErrorResponse.FieldValidationError> errors) {
        if (logStackTrace) {
            logger.error("{} at {}: {}", code, request.getRequestURI(), exception.getMessage(), exception);
        } else {
            logger.warn("{} at {}: {}", code, request.getRequestURI(), message);
        }
        ErrorResponse error = ErrorResponse.of(
                status,
                code,
                message,
                request.getRequestURI(),
                resolveTraceId(request),
                errors
        );
        return ResponseEntity.status(status).body(error);
    }

    private String resolveTraceId(HttpServletRequest request) {
        String headerTraceId = request.getHeader("X-Trace-Id");
        if (headerTraceId != null && !headerTraceId.isBlank()) {
            return headerTraceId.trim();
        }

        Object traceIdAttribute = request.getAttribute("traceId");
        if (traceIdAttribute instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }

        return UUID.randomUUID().toString();
    }
}
