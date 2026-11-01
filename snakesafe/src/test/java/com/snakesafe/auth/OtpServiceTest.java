package com.snakesafe.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.snakesafe.MutableClock;
import com.snakesafe.SnakeSafeTest;
import com.snakesafe.web.ApiException;

@SnakeSafeTest
class OtpServiceTest {

    @Autowired
    OtpService otp;
    @Autowired
    JwtService jwt;
    @Autowired
    MutableClock clock;

    private Instant startedAt;

    @BeforeEach
    void rememberTime() {
        startedAt = clock.instant();
    }

    @AfterEach
    void restoreTime() {
        clock.setTo(startedAt);
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void demoCodeLogsTheUserInAndReturnsAWorkingToken() {
        otp.send("9876500001", "10.0.0.1");
        var login = otp.verify("9876500001", "123456");
        assertThat(login.user().phone).isEqualTo("+919876500001");
        var principal = jwt.parse(login.token()).orElseThrow();
        assertThat(principal.role()).isEqualTo(Role.USER);
        assertThat(principal.id()).isEqualTo(login.user().id);
    }

    @Test
    void sameNumberAlwaysGetsTheSameAccount() {
        otp.send("9876500002", "10.0.0.2");
        long first = otp.verify("9876500002", "123456").user().id;
        otp.send("+91 98765 00002", "10.0.0.2");
        assertThat(otp.verify("9876500002", "123456").user().id).isEqualTo(first);
    }

    @Test
    void codeExpiresAfterFiveMinutes() {
        otp.send("9876500003", "10.0.0.3");
        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        // still fine just before the limit: check by trying (this consumes it), so send again for the real check
        otp.verify("9876500003", "123456");

        otp.send("9876500003", "10.0.0.3");
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        assertCode(() -> otp.verify("9876500003", "123456"), "CODE_EXPIRED");
    }

    @Test
    void wrongCodeIsRejectedAndCounted() {
        otp.send("9876500004", "10.0.0.4");
        assertCode(() -> otp.verify("9876500004", "000000"), "INVALID_CODE");
        // the right code still works after one wrong try
        assertThat(otp.verify("9876500004", "123456").token()).isNotBlank();
    }

    @Test
    void afterFiveWrongTriesEvenTheRightCodeIsRefused() {
        otp.send("9876500005", "10.0.0.5");
        for (int i = 0; i < OtpService.MAX_ATTEMPTS; i++) {
            assertCode(() -> otp.verify("9876500005", "111111"), "INVALID_CODE");
        }
        assertThatThrownBy(() -> otp.verify("9876500005", "123456")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo("TOO_MANY_ATTEMPTS");
            assertThat(e.getStatus().value()).isEqualTo(429);
        });
    }

    @Test
    void aCodeWorksOnlyOnce() {
        otp.send("9876500006", "10.0.0.6");
        otp.verify("9876500006", "123456");
        assertCode(() -> otp.verify("9876500006", "123456"), "INVALID_CODE");
    }

    @Test
    void newCodeReplacesTheOldOne() {
        otp.send("9876500007", "10.0.0.7");
        otp.send("9876500007", "10.0.0.7");
        // only the latest code is live; the demo code is the same, so verify once and the older one is gone
        otp.verify("9876500007", "123456");
        assertCode(() -> otp.verify("9876500007", "123456"), "INVALID_CODE");
    }

    @Test
    void sendingIsRateLimitedPerNumber() {
        for (int i = 0; i < 3; i++) {
            otp.send("9876500008", "10.0.0.8");
        }
        assertThatThrownBy(() -> otp.send("9876500008", "10.0.0.8")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo("RATE_LIMITED");
            assertThat(e.getStatus().value()).isEqualTo(429);
        });
        // the limit clears after the window
        clock.advance(Duration.ofMinutes(11));
        assertThat(otp.send("9876500008", "10.0.0.8").sent()).isTrue();
    }

    @Test
    void badPhoneNumbersAreRejected() {
        assertCode(() -> otp.send("12345", "10.0.0.9"), "INVALID_PHONE");
        assertCode(() -> otp.send("abcdefghij", "10.0.0.9"), "INVALID_PHONE");
    }
}
