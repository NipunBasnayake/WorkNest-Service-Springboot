package com.worknest.common.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private boolean success;
    private String message;
    private String errorCode;
    private String path;
    private LocalDateTime timestamp;

    public static ErrorResponse of(String message, String errorCode, String path) {
        return new ErrorResponse(false, message, errorCode, path, LocalDateTime.now());
    }

    public static ErrorResponse of(String message, String errorCode) {
        return new ErrorResponse(false, message, errorCode, null, LocalDateTime.now());
    }
}

