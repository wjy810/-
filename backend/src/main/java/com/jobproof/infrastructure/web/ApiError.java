package com.jobproof.infrastructure.web;

public record ApiError(String category, String reason, String message) {
}
