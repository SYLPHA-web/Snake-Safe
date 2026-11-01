package com.snakesafe.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletRequest;

class ClientIpFilterTest {

    private static String seenBy(boolean trust, String forwarded) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.1.1.1");
        if (forwarded != null) {
            req.addHeader("X-Forwarded-For", forwarded);
        }
        AtomicReference<String> seen = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest request, jakarta.servlet.ServletResponse response) {
                seen.set(request.getRemoteAddr());
            }
        };
        new ClientIpFilter(trust).doFilter(req, new MockHttpServletResponse(), chain);
        return seen.get();
    }

    @Test
    void usesTheFirstForwardedAddressWhenTheProxyIsTrusted() throws Exception {
        assertThat(seenBy(true, "203.0.113.7, 76.76.21.21")).isEqualTo("203.0.113.7");
        assertThat(seenBy(true, "2001:db8::1")).isEqualTo("2001:db8::1");
    }

    @Test
    void ignoresTheHeaderWhenTheProxyIsNotTrusted() throws Exception {
        assertThat(seenBy(false, "203.0.113.7")).isEqualTo("10.1.1.1");
    }

    @Test
    void ignoresJunkInTheHeader() throws Exception {
        assertThat(seenBy(true, "<script>alert(1)</script>")).isEqualTo("10.1.1.1");
        assertThat(seenBy(true, null)).isEqualTo("10.1.1.1");
    }
}
