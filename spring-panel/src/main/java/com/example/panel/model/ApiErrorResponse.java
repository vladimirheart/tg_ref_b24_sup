package com.example.panel.model;

import java.time.OffsetDateTime;

public record ApiErrorResponse(
        boolean success,
        String error,
        String errorCode,
        String path,
        OffsetDateTime timestamp
) {
}
