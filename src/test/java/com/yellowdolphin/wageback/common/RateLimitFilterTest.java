package com.yellowdolphin.wageback.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    final AtomicLong now = new AtomicLong(0);
    final RateLimitFilter filter = new RateLimitFilter(now::get);

    MockHttpServletResponse call(String path, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    int allowedOf(String path, int tries) throws Exception {
        int ok = 0;
        for (int i = 0; i < tries; i++) {
            if (call(path, "1.1.1.1").getStatus() == 200) {
                ok++;
            }
        }
        return ok;
    }

    @Test
    void 경로별_분당_제한() throws Exception {
        assertEquals(5, allowedOf("/api/v1/documents/extract", 8));
        assertEquals(10, allowedOf("/api/v1/calculations/a/explanation", 12));
        assertEquals(60, allowedOf("/api/v1/calculations", 70));
        assertEquals(200, call("/index.html", "1.1.1.1").getStatus()); // 화면 파일은 세지 않는다
    }

    @Test
    void 넘으면_429_다음_분엔_다시_된다() throws Exception {
        allowedOf("/api/v1/documents/extract", 5);
        MockHttpServletResponse res = call("/api/v1/documents/extract", "1.1.1.1");
        assertEquals(429, res.getStatus());
        assertTrue(res.getContentAsString().contains("TOO_MANY_REQUESTS"));
        assertEquals(200, call("/api/v1/documents/extract", "2.2.2.2").getStatus()); // 다른 IP
        now.set(60_000);
        assertEquals(200, call("/api/v1/documents/extract", "1.1.1.1").getStatus());
    }
}
