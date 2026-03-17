package com.worknest.common.exception;

public class TenantResolutionException extends RuntimeException {

    public TenantResolutionException(String message) {
        super(message);
    }

    public TenantResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

