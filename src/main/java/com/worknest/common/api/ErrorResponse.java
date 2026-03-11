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
    private String error;
    private LocalDateTime timestamp;

    public static ErrorResponse of(String message, String error) {
        return new ErrorResponse(false, message, error, LocalDateTime.now());
    }

    public static ErrorResponse of(String message) {
        return new ErrorResponse(false, message, null, LocalDateTime.now());
    }
}

