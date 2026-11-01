package com.snakesafe.config;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String CSP = "default-src 'self'; img-src 'self' data: blob:; "
            + "style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self' ws: wss:; "
            + "object-src 'none'; base-uri 'self'; frame-ancestors 'none'";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean docs = path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
        if (!docs) {
            response.setHeader("Content-Security-Policy", CSP);
        }
        response.setHeader("Permissions-Policy", "geolocation=(self), camera=(self), microphone=()");
        chain.doFilter(request, response);
    }
}
