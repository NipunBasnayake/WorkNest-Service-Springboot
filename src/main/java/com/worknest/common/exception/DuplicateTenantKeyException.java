package com.worknest.common.exception;

public class DuplicateTenantKeyException extends RuntimeException {

    public DuplicateTenantKeyException(String message) {
        super(message);
    }
}
