package com.worknest.common.exception;

public class EmailServiceUnavailableException extends RuntimeException {
    public EmailServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
