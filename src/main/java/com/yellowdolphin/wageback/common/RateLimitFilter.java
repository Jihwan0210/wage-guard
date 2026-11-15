package com.yellowdolphin.wageback.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// IP당 분당 요청 제한: 업로드 5(한 장 · 여러 장 요청 합쳐서) · 해설/진정서 10 · 그 외 API 60 (API.md 요청 제한)
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String BODY = "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
            + "\"detail\":\"요청이 너무 많아요. 잠시 후 다시 시도해 주세요.\",\"code\":\"TOO_MANY_REQUESTS\",\"field\":null}";

    private final Map<String, long[]> counts = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public RateLimitFilter() {
        this(System::currentTimeMillis);
    }

    RateLimitFilter(LongSupplier clock) {
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String bucket = path.endsWith("/documents/extract") || path.endsWith("/documents/extract-batch") ? "upload"
                : path.endsWith("/explanation") || path.endsWith("/complaint-draft") ? "ai" : "api";
        int limit = switch (bucket) {
            case "upload" -> 5;
            case "ai" -> 10;
            default -> 60;
        };
        if (allow(bucket + "|" + request.getRemoteAddr(), limit)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BODY);
    }

    // 1분 단위 고정 창으로 센다
    private boolean allow(String key, int limit) {
        long minute = clock.getAsLong() / 60_000;
        long[] c = counts.compute(key, (k, v) -> v == null || v[0] != minute ? new long[] { minute, 1 } : new long[] { minute, v[1] + 1 });
        if (counts.size() > 10_000) {
            counts.values().removeIf(v -> v[0] != minute);
        }
        return c[1] <= limit;
    }
}
