package com.yellowdolphin.wageback.common;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 모든 응답에 붙이는 보안 헤더
// - nosniff: 올린 파일 · 응답을 브라우저가 다른 형식으로 짐작해 실행하지 않게
// - no-referrer: 노동청 같은 바깥 사이트로 나갈 때 우리 주소를 알리지 않게
// - CSP: 우리 서버의 파일만 불러온다. 사진 미리보기(blob · data)와 화면이 직접 넣는 style 속성만 예외
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String CSP = "default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; "
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
