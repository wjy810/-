package com.jobproof.infrastructure.web;

import com.jobproof.shared.error.AppException;
import com.jobproof.shared.error.ErrorCategory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Map<ErrorCategory, HttpStatus> STATUS = Map.of(
            ErrorCategory.USER_CORRECTABLE, HttpStatus.BAD_REQUEST,
            ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN,
            ErrorCategory.CONFLICT, HttpStatus.CONFLICT,
            ErrorCategory.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
            ErrorCategory.DEPENDENCY_FAILED, HttpStatus.BAD_GATEWAY,
            ErrorCategory.SYSTEM_FAILURE, HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorCategory.REQUIRES_HUMAN, HttpStatus.UNPROCESSABLE_ENTITY,
            ErrorCategory.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleApp(AppException ex) {
        HttpStatus status = serviceUnavailable(ex)
                ? HttpStatus.SERVICE_UNAVAILABLE
                : STATUS.getOrDefault(ex.category(), HttpStatus.BAD_REQUEST);
        return jsonError(status, new ApiError(ex.category().name(), ex.reason(), ex.getMessage()));
    }

    private static boolean serviceUnavailable(AppException ex) {
        return ex.category() == ErrorCategory.DEPENDENCY_FAILED
                && (ex.reason().startsWith("VERIFICATION_")
                || "REGISTRATION_UNAVAILABLE".equals(ex.reason()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidation(Exception ex) {
        return jsonError(HttpStatus.BAD_REQUEST, new ApiError(
                ErrorCategory.USER_CORRECTABLE.name(), "VALIDATION_FAILED", "请求参数不正确"));
    }

    @ExceptionHandler({
            OptimisticLockingFailureException.class,
            ObjectOptimisticLockingFailureException.class,
            OptimisticLockException.class})
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(Exception ex) {
        return jsonError(HttpStatus.CONFLICT, new ApiError(
                ErrorCategory.CONFLICT.name(),
                "VERSION_CONFLICT",
                "对象版本冲突，已拒绝覆盖。请刷新后查看双方内容再选择。"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return jsonError(HttpStatus.METHOD_NOT_ALLOWED, new ApiError(
                ErrorCategory.USER_CORRECTABLE.name(), "METHOD_NOT_ALLOWED", "该路径不支持此方法"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMedia(HttpMediaTypeNotSupportedException ex) {
        return jsonError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, new ApiError(
                ErrorCategory.USER_CORRECTABLE.name(),
                "UNSUPPORTED_MEDIA_TYPE",
                "请求内容类型不受支持，请使用 application/json"));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return jsonError(HttpStatus.NOT_ACCEPTABLE, new ApiError(
                ErrorCategory.USER_CORRECTABLE.name(),
                "NOT_ACCEPTABLE",
                "响应内容类型不受支持，请使用 application/json"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return jsonError(HttpStatus.BAD_REQUEST, new ApiError(
                ErrorCategory.USER_CORRECTABLE.name(), "VALIDATION_FAILED", "请求体无法解析"));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class,
            MultipartException.class})
    public ResponseEntity<ApiResponse<Void>> handleMissingOrMismatchedInput(Exception ex) {
        return jsonError(HttpStatus.BAD_REQUEST, new ApiError(
                ErrorCategory.USER_CORRECTABLE.name(),
                "VALIDATION_FAILED",
                "请求参数不正确"));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception ex) {
        return jsonError(HttpStatus.NOT_FOUND, new ApiError(
                ErrorCategory.USER_CORRECTABLE.name(), "NOT_FOUND", "接口不存在"));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiResponse<Void>> handleIo(IOException ex, HttpServletRequest request) {
        if (request != null && request.getRequestURI().endsWith("/events")) {
            log.debug("SSE client disconnected path={}", request.getRequestURI());
            return ResponseEntity.noContent().build();
        }
        return handleUnknown(ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        log.error("unhandled error", ex);
        return jsonError(HttpStatus.INTERNAL_SERVER_ERROR, new ApiError(
                ErrorCategory.SYSTEM_FAILURE.name(), "SYSTEM_FAILURE", "系统繁忙，请稍后重试"));
    }

    /** 错误体一律 JSON，避免客户端 Accept 导致二次写失败。不改变原状态码。 */
    private static ResponseEntity<ApiResponse<Void>> jsonError(HttpStatus status, ApiError error) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(error));
    }
}
