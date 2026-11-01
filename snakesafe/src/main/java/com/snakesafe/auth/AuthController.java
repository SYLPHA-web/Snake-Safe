package com.snakesafe.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/auth/otp")
public class AuthController {

    public record SendRequest(@NotBlank @Size(max = 20) String phone) {
    }

    public record VerifyRequest(@NotBlank @Size(max = 20) String phone,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "must be 6 digits") String code) {
    }

    public record UserSummary(long id, String phone, String name, String language) {
    }

    public record TokenResponse(String token, int expiresInHours, UserSummary user) {
    }

    private final OtpService otp;

    public AuthController(OtpService otp) {
        this.otp = otp;
    }

    @PostMapping("/send")
    public OtpService.SendResult send(@Valid @RequestBody SendRequest request, HttpServletRequest http) {
        return otp.send(request.phone(), http.getRemoteAddr());
    }

    @PostMapping("/verify")
    public TokenResponse verify(@Valid @RequestBody VerifyRequest request) {
        OtpService.LoginResult result = otp.verify(request.phone(), request.code());
        User u = result.user();
        return new TokenResponse(result.token(), result.expiresInHours(),
                new UserSummary(u.id, u.phone, u.name, u.language));
    }
}
