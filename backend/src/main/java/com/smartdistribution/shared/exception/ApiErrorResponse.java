package com.smartdistribution.shared.exception;

import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        List<FieldError> fieldErrors,
        String traceId
) {

    public ApiErrorResponse {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public record FieldError(
            String field,
            String code,
            String message
    ) {
    }
}
