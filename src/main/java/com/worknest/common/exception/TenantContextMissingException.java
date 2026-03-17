package com.worknest.common.exception;

public class TenantContextMissingException extends RuntimeException {

    public TenantContextMissingException(String message) {
        super(message);
    }

    public TenantContextMissingException(String message, Throwable cause) {
        super(message, cause);
    }
}
