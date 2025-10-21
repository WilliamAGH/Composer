package com.composerai.api.exception;

import com.composerai.api.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleResourceNotFound_nonApiRequestRethrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/favicon.ico");

        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, request.getRequestURI());

        assertThrows(NoResourceFoundException.class, () -> handler.handleResourceNotFound(ex, request));
    }

    @Test
    void handleResourceNotFound_apiRequestReturnsJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/missing");

        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, request.getRequestURI());

        var response = handler.handleResourceNotFound(ex, request);
        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        ErrorResponse body = response.getBody();
        assertEquals("not_found", body.error());
        assertEquals("/api/missing", body.path());
    }

    @Test
    void handleGenericException_nonApiRequestRethrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/apple-touch-icon-precomposed.png");
        RuntimeException ex = new RuntimeException("boom");

        assertThrows(RuntimeException.class, () -> handler.handleGenericException(ex, request));
    }

    @Test
    void handleGenericException_apiRequestReturnsJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/chat");
        RuntimeException ex = new RuntimeException("boom");

        var response = handler.handleGenericException(ex, request);
        assertEquals(500, response.getStatusCode().value());
        assertEquals("internal_error", response.getBody().error());
    }
}
