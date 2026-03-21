package com.worknest.common.exception;

public class DownstreamCommunicationException extends RuntimeException {
    public DownstreamCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }

    public DownstreamCommunicationException(String message) {
        super(message);
    }
}
