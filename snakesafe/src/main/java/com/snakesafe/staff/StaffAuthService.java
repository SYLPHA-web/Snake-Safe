package com.snakesafe.staff;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.snakesafe.auth.AuthPrincipal;
import com.snakesafe.auth.JwtService;
import com.snakesafe.auth.Role;
import com.snakesafe.config.RateLimiter;
import com.snakesafe.web.ApiException;

@Service
public class StaffAuthService {

    private final StaffRepository staff;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RateLimiter limiter;

    public StaffAuthService(StaffRepository staff, PasswordEncoder encoder, JwtService jwt, RateLimiter limiter) {
        this.staff = staff;
        this.encoder = encoder;
        this.jwt = jwt;
        this.limiter = limiter;
    }

    public record Login(String token, Staff staff) {
    }

    /** Same error for "no such user" and "wrong password" so usernames cannot be guessed. */
    public Login login(String username, String password, Role expected, String ip) {
        limiter.check("login:" + ip, 20, Duration.ofMinutes(10));
        limiter.check("login-user:" + username.toLowerCase(), 8, Duration.ofMinutes(10));
        Staff s = staff.findByUsernameIgnoreCase(username.strip())
                .filter(x -> x.active && x.role == expected)
                .filter(x -> encoder.matches(password, x.passwordHash))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_LOGIN",
                        "Wrong username or password."));
        return new Login(jwt.issue(s.id, s.role), s);
    }

    /** Loads the signed-in staff member from the token and makes sure they are still active. */
    public Staff require(AuthPrincipal me, Role role) {
        return staff.findById(me.id())
                .filter(s -> s.active && s.role == role)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please sign in again."));
    }

    public Staff requireHospitalStaff(AuthPrincipal me) {
        Staff s = require(me, Role.STAFF);
        if (s.hospitalId == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NO_HOSPITAL", "This account is not linked to a hospital.");
        }
        return s;
    }
}
