package com.portfolio.banking.web;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldErrorItem> fieldErrors
) {
    public record FieldErrorItem(String field, String message) {
    }

    public static ErrorResponse of(int status, String error, String code, String message, String path, List<FieldErrorItem> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, error, code, message, path, fieldErrors);
    }
}
