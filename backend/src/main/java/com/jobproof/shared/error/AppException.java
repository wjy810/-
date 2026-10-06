package com.jobproof.shared.error;

public class AppException extends RuntimeException {

    private final ErrorCategory category;
    private final String reason;

    public AppException(ErrorCategory category, String reason, String message) {
        super(message);
        this.category = category;
        this.reason = reason;
    }

    public ErrorCategory category() {
        return category;
    }

    public String reason() {
        return reason;
    }

    public static AppException user(String reason, String message) {
        return new AppException(ErrorCategory.USER_CORRECTABLE, reason, message);
    }

    public static AppException forbidden(String reason, String message) {
        return new AppException(ErrorCategory.FORBIDDEN, reason, message);
    }

    public static AppException conflict(String reason, String message) {
        return new AppException(ErrorCategory.CONFLICT, reason, message);
    }

    public static AppException rateLimited(String reason, String message) {
        return new AppException(ErrorCategory.RATE_LIMITED, reason, message);
    }

    public static AppException unauthenticated() {
        return new AppException(ErrorCategory.UNAUTHENTICATED, "UNAUTHENTICATED", "未登录或会话已失效");
    }

    public static AppException dependency(String reason, String message) {
        return new AppException(ErrorCategory.DEPENDENCY_FAILED, reason, message);
    }
}
