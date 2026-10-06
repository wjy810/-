package com.jobproof.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Error body of every failed API call (docs/03 §6.2). {@code requestId} links the response to server
 * logs; {@code fields} carries per-field validation messages when the request body was invalid.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String category, String reason, String message, String requestId, Map<String, String> fields) {

    public ApiError(String category, String reason, String message) {
        this(category, reason, message, RequestIdFilter.current(), null);
    }

    public ApiError withFields(Map<String, String> value) {
        return new ApiError(category, reason, message, requestId, value == null || value.isEmpty() ? null : value);
    }
}
