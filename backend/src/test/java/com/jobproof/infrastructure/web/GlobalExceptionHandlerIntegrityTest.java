package com.jobproof.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerIntegrityTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void otherDataIntegrityViolationStaysSystemFailure500() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleUnknown(
                new DataIntegrityViolationException("Duplicate entry for key 'uk_notification_dedup'"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ApiError error = response.getBody() == null ? null : response.getBody().error();
        assertNotNull(error);
        assertEquals("SYSTEM_FAILURE", error.category());
        assertEquals("SYSTEM_FAILURE", error.reason());
        assertEquals("系统繁忙，请稍后重试", error.message());
    }

    @Test
    void accountsEmailUniqueMessageIsNotRemappedByGlobalHandler() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleUnknown(
                new DataIntegrityViolationException("Duplicate entry for key 'uk_accounts_email'"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ApiError error = response.getBody() == null ? null : response.getBody().error();
        assertNotNull(error);
        assertEquals("SYSTEM_FAILURE", error.reason());
    }

    @Test
    void sseClientDisconnectDoesNotBecomeSystemFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/v1/career-planning/sessions/session-1/events");

        ResponseEntity<ApiResponse<Void>> response = handler.handleIo(
                new IOException("client disconnected"), request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void nonStreamingIoFailureStaysSystemFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/files/file-1");

        ResponseEntity<ApiResponse<Void>> response = handler.handleIo(
                new IOException("storage failed"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("SYSTEM_FAILURE", response.getBody().error().reason());
    }
}
