package com.riceguard.api.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class RequestLogFilterTest {
    private final RequestLogFilter filter = new RequestLogFilter();

    private MockHttpServletResponse call(MockHttpServletRequest req) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void echoesValidRequestId() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/fields");
        req.setRequestURI("/api/v1/fields");
        req.addHeader(RequestLogFilter.HEADER, "app-3f2a9c1d");
        assertEquals("app-3f2a9c1d", call(req).getHeader(RequestLogFilter.HEADER));
    }

    @Test
    void replacesUnsafeRequestId() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/fields");
        req.setRequestURI("/api/v1/fields");
        req.addHeader(RequestLogFilter.HEADER, "bad id\r\nInjected: 1");
        String id = call(req).getHeader(RequestLogFilter.HEADER);
        assertNotNull(id);
        assertTrue(id.matches("^[A-Za-z0-9-]{8}$"), id);
    }

    @Test
    void ignoresNonApiPaths() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        req.setRequestURI("/actuator/health");
        assertNull(call(req).getHeader(RequestLogFilter.HEADER));
    }
}
