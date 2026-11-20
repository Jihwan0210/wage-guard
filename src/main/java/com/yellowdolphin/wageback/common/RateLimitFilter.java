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
// LLM 을 부르는 요청은 모든 사용자를 합쳐 하루 상한도 둔다: 업로드 300 · 해설/진정서 600
// (누가 작정하고 불러도 LLM 요금이 끝없이 나가지 않게)
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String BODY = "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
            + "\"detail\":\"요청이 너무 많아요. 잠시 후 다시 시도해 주세요.\",\"code\":\"TOO_MANY_REQUESTS\",\"field\":null}";
    private static final String DAILY_BODY = BODY.replace("요청이 너무 많아요. 잠시 후 다시 시도해 주세요.", "오늘은 이 기능을 쓸 수 있는 횟수가 다 찼어요. 내일 다시 해 주세요.")
            .replace("TOO_MANY_REQUESTS", "DAILY_LIMIT");
    private static final long MINUTE = 60_000, DAY = 86_400_000;

    private final Map<String, long[]> counts = new ConcurrentHashMap<>(); // 분당, IP별
    private final Map<String, long[]> daily = new ConcurrentHashMap<>(); // 하루, 전체
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
        // 분당 제한에 걸린 요청은 하루 상한에서 세지 않는다
        String body = !allow(counts, bucket + "|" + request.getRemoteAddr(), limit, MINUTE) ? BODY
                : !bucket.equals("api") && !allow(daily, bucket, bucket.equals("upload") ? 300 : 600, DAY) ? DAILY_BODY : null;
        if (body == null) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }

    // 고정 창(1분 · 하루)으로 센다. 하루 창은 UTC 자정(한국 오전 9시)에 바뀐다
    private boolean allow(Map<String, long[]> map, String key, int limit, long window) {
        long slot = clock.getAsLong() / window;
        long[] c = map.compute(key, (k, v) -> v == null || v[0] != slot ? new long[] { slot, 1 } : new long[] { slot, v[1] + 1 });
        if (map.size() > 10_000) {
            map.values().removeIf(v -> v[0] != slot);
        }
        return c[1] <= limit;
    }
}
