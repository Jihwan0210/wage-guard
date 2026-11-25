package com.yellowdolphin.wageback.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityHeadersFilterTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 요청_제한에_걸린_429_응답에도_보안_헤더가_붙는다() throws Exception {
        MockHttpServletResponse res = null;
        for (int i = 0; i < 11; i++) { // 해설은 분당 10번까지. 11번째가 429
            res = mvc.perform(post("/api/v1/calculations/calc_none/explanation").with(r -> {
                r.setRemoteAddr("198.51.100.77");
                return r;
            }).contentType(MediaType.APPLICATION_JSON).content("{\"lang\":\"ko\"}")).andReturn().getResponse();
        }
        assertEquals(429, res.getStatus());
        assertEquals("nosniff", res.getHeader("X-Content-Type-Options"));
    }


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
