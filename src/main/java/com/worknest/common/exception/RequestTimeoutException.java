package com.worknest.common.exception;

public class RequestTimeoutException extends RuntimeException {
    public RequestTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

    public RequestTimeoutException(String message) {
        super(message);
    }
}
