package com.yellowdolphin.wageback.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityHeadersFilterTest {

    @Test
    void 화면_파일과_API_응답_모두에_보안_헤더가_붙는다() throws Exception {
        for (String path : new String[] { "/index.html", "/api/v1/health" }) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            new SecurityHeadersFilter().doFilter(new MockHttpServletRequest("GET", path), res, new MockFilterChain());
            assertEquals("nosniff", res.getHeader("X-Content-Type-Options"));
            assertEquals("no-referrer", res.getHeader("Referrer-Policy"));
            String csp = res.getHeader("Content-Security-Policy");
            assertTrue(csp.contains("default-src 'self'") && csp.contains("frame-ancestors 'none'") && csp.contains("object-src 'none'"), csp);
        }
    }
}
