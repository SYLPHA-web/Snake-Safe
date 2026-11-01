package com.snakesafe.config;

import java.io.IOException;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Behind Vercel or any load balancer every request arrives from the proxy's address, so all visitors would share
 * one rate limit. With app.trust-proxy=true the first X-Forwarded-For address is used as the visitor's address.
 * Leave it false when the app is reachable directly, because then the header could be faked.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ClientIpFilter extends OncePerRequestFilter {

    private static final Pattern ADDRESS = Pattern.compile("[0-9a-fA-F:.]{3,45}");

    private final boolean trustProxy;

    @Autowired
    public ClientIpFilter(AppProperties props) {
        this(props.trustProxy());
    }

    ClientIpFilter(boolean trustProxy) {
        this.trustProxy = trustProxy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String forwarded = trustProxy ? request.getHeader("X-Forwarded-For") : null;
        String ip = forwarded == null ? null : forwarded.split(",")[0].trim();
        if (ip == null || !ADDRESS.matcher(ip).matches()) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public String getRemoteAddr() {
                return ip;
            }
        }, response);
    }
}
