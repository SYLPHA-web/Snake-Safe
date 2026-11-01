package com.snakesafe.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.snakesafe.config.AppProperties;
import com.snakesafe.config.RateLimiter;
import com.snakesafe.web.ApiException;

@Service
public class OtpService {

    public static final String DEMO_CODE = "123456";
    public static final Duration CODE_LIFETIME = Duration.ofMinutes(5);
    public static final int MAX_ATTEMPTS = 5;

    private static final Duration SEND_WINDOW = Duration.ofMinutes(10);
    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final OtpCodeRepository codes;
    private final UserRepository users;
    private final SmsSender sms;
    private final JwtService jwt;
    private final AppProperties props;
    private final Clock clock;
    private final RateLimiter limiter;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpCodeRepository codes, UserRepository users, SmsSender sms, JwtService jwt,
            AppProperties props, Clock clock, RateLimiter limiter, PasswordEncoder encoder) {
        this.codes = codes;
        this.users = users;
        this.sms = sms;
        this.jwt = jwt;
        this.props = props;
        this.clock = clock;
        this.limiter = limiter;
        this.encoder = encoder;
    }

    public record SendResult(boolean sent, int expiresInSeconds, String mode) {
    }

    public record LoginResult(String token, User user, int expiresInHours) {
    }

    public SendResult send(String rawPhone, String ip) {
        String phone = Phones.normalize(rawPhone);
        limiter.check("otp-phone:" + phone, 3, SEND_WINDOW);
        limiter.check("otp-ip:" + ip, 10, SEND_WINDOW);

        String code = props.demoOtp() ? DEMO_CODE : String.format("%06d", random.nextInt(1_000_000));
        Instant now = clock.instant();

        codes.deleteExpiredBefore(now.minus(Duration.ofDays(1)));
        codes.invalidateAll(phone);
        OtpCode entry = new OtpCode();
        entry.phone = phone;
        entry.codeHash = encoder.encode(code);
        entry.createdAt = now;
        entry.expiresAt = now.plus(CODE_LIFETIME);
        codes.save(entry);

        if (props.demoOtp()) {
            log.info("[DEMO OTP] code for ***{} is {}", phone.substring(phone.length() - 4), code);
        } else {
            sms.send(phone, "Your " + props.name() + " code is " + code + ". It expires in 5 minutes.");
        }
        return new SendResult(true, (int) CODE_LIFETIME.toSeconds(), props.otpMode());
    }

    /** Not transactional on purpose: a wrong guess must still be counted when the error is thrown. */
    public LoginResult verify(String rawPhone, String code) {
        String phone = Phones.normalize(rawPhone);
        OtpCode entry = codes.findFirstByPhoneAndUsedFalseOrderByCreatedAtDesc(phone)
                .orElseThrow(() -> ApiException.badRequest("INVALID_CODE", "That code is not right. Ask for a new code."));

        if (clock.instant().isAfter(entry.expiresAt)) {
            throw ApiException.badRequest("CODE_EXPIRED", "That code has expired. Ask for a new code.");
        }
        if (entry.attempts >= MAX_ATTEMPTS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many wrong tries. Ask for a new code.");
        }
        if (!encoder.matches(code, entry.codeHash)) {
            entry.attempts++;
            codes.save(entry);
            throw ApiException.badRequest("INVALID_CODE", "That code is not right. Tries left: "
                    + Math.max(0, MAX_ATTEMPTS - entry.attempts) + ".");
        }

        entry.used = true;
        codes.save(entry);
        User user = users.findByPhone(phone).orElseGet(() -> createUser(phone));
        return new LoginResult(jwt.issue(user.id, Role.USER), user, jwt.expiresInHours());
    }

    private User createUser(String phone) {
        User user = new User();
        user.phone = phone;
        user.language = props.defaultLanguage();
        user.createdAt = clock.instant();
        try {
            return users.save(user);
        } catch (DataIntegrityViolationException e) {
            return users.findByPhone(phone).orElseThrow(() -> e);
        }
    }
}
