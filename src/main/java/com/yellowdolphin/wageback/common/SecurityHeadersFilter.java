package com.yellowdolphin.wageback.common;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 모든 응답에 붙이는 보안 헤더
// - nosniff: 올린 파일 · 응답을 브라우저가 다른 형식으로 짐작해 실행하지 않게
// - no-referrer: 노동청 같은 바깥 사이트로 나갈 때 우리 주소를 알리지 않게
// - CSP: 우리 서버의 파일만 불러온다. 사진 미리보기(blob · data), 화면이 직접 넣는 style 속성, 사는 곳 주소 찾기(카카오 우편번호 서비스) 스크립트 · 창만 예외
// 요청 제한 필터보다 먼저 돈다: 429 로 끝나는 응답에도 헤더가 붙어야 한다
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String CSP = "default-src 'self'; img-src 'self' data: blob:; media-src 'self' blob:; style-src 'self' 'unsafe-inline'; "
            + "script-src 'self' https://t1.kakaocdn.net; frame-src postcode.map.kakao.com postcode.map.daum.net; "
            + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Content-Security-Policy", CSP);
        chain.doFilter(request, response);
    }
}
