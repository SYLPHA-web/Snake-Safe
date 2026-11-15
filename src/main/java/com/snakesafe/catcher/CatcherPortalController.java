package com.snakesafe.catcher;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.snakesafe.auth.AuthPrincipal;
import com.snakesafe.auth.JwtService;
import com.snakesafe.auth.OtpService;
import com.snakesafe.auth.Phones;
import com.snakesafe.auth.Role;
import com.snakesafe.web.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/catcher")
public class CatcherPortalController {

    public record SendOtpRequest(@NotBlank String phone) {
    }

    public record VerifyOtpRequest(@NotBlank String phone, @NotBlank String code) {
    }

    public record CatcherSummary(Long id, String name, String phone, String district) {
    }

    public record LoginResponse(String token, int expiresInHours, CatcherSummary catcher) {
    }

    public record CatcherRescueView(String id, Long catcherId, String phone, double lat, double lng,
            String notes, RescueStatus status, Instant createdAt, Instant updatedAt) {
    }

    private final CatcherRepository catchers;
    private final RescueRepository rescues;
    private final OtpService otpService;
    private final JwtService jwt;
    private final Clock clock;

    public CatcherPortalController(CatcherRepository catchers, RescueRepository rescues,
            OtpService otpService, JwtService jwt, Clock clock) {
        this.catchers = catchers;
        this.rescues = rescues;
        this.otpService = otpService;
        this.jwt = jwt;
        this.clock = clock;
    }

    @PostMapping("/login/send")
    public OtpService.SendResult sendOtp(@Valid @RequestBody SendOtpRequest req, HttpServletRequest http) {
        String phone = Phones.normalize(req.phone());
        catchers.findFirstByPhoneAndActiveTrueOrderByIdAsc(phone)
                .orElseThrow(() -> ApiException.badRequest("CATCHER_NOT_REGISTERED",
                        "No active snake catcher found with this phone number."));
        return otpService.send(phone, http.getRemoteAddr());
    }

    @PostMapping("/login/verify")
    public LoginResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        String phone = Phones.normalize(req.phone());
        Catcher catcher = catchers.findFirstByPhoneAndActiveTrueOrderByIdAsc(phone)
                .orElseThrow(() -> ApiException.badRequest("CATCHER_NOT_REGISTERED",
                        "No active snake catcher found with this phone number."));
        otpService.verifyCodeOnly(phone, req.code());
        String token = jwt.issue(catcher.id, Role.CATCHER);
        return new LoginResponse(token, jwt.expiresInHours(), toSummary(catcher));
    }

    @GetMapping("/me")
    public CatcherSummary me(@AuthenticationPrincipal AuthPrincipal me) {
        Catcher c = requireCatcher(me);
        return toSummary(c);
    }

    @GetMapping("/rescues")
    @Transactional(readOnly = true)
    public List<CatcherRescueView> listRescues(@AuthenticationPrincipal AuthPrincipal me) {
        Catcher c = requireCatcher(me);
        return rescues.findByCatcherIdOrderByCreatedAtDesc(c.id).stream()
                .map(CatcherPortalController::toView)
                .toList();
    }

    @PostMapping("/rescues/{id}/accept")
    @Transactional
    public CatcherRescueView accept(@PathVariable String id, @AuthenticationPrincipal AuthPrincipal me) {
        Catcher c = requireCatcher(me);
        Rescue r = rescues.findById(id)
                .orElseThrow(() -> ApiException.notFound("RESCUE_NOT_FOUND", "Rescue request not found."));
        if (!r.catcherId.equals(c.id)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this rescue request.");
        }
        if (r.status == RescueStatus.CANCELLED) {
            throw ApiException.conflict("INVALID_STATE", "This rescue request was cancelled by the caller.");
        }
        if (r.status != RescueStatus.REQUESTED) {
            throw ApiException.conflict("INVALID_STATE", "Cannot accept request in " + r.status + " status.");
        }
        r.status = RescueStatus.ACCEPTED;
        r.updatedAt = clock.instant();
        return toView(rescues.save(r));
    }

    @PostMapping("/rescues/{id}/decline")
    @Transactional
    public CatcherRescueView decline(@PathVariable String id, @AuthenticationPrincipal AuthPrincipal me) {
        Catcher c = requireCatcher(me);
        Rescue r = rescues.findById(id)
                .orElseThrow(() -> ApiException.notFound("RESCUE_NOT_FOUND", "Rescue request not found."));
        if (!r.catcherId.equals(c.id)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this rescue request.");
        }
        if (r.status != RescueStatus.REQUESTED) {
            throw ApiException.conflict("INVALID_STATE", "Cannot decline request in " + r.status + " status.");
        }
        r.status = RescueStatus.DECLINED;
        r.updatedAt = clock.instant();
        return toView(rescues.save(r));
    }

    @PostMapping("/rescues/{id}/done")
    @Transactional
    public CatcherRescueView markDone(@PathVariable String id, @AuthenticationPrincipal AuthPrincipal me) {
        Catcher c = requireCatcher(me);
        Rescue r = rescues.findById(id)
                .orElseThrow(() -> ApiException.notFound("RESCUE_NOT_FOUND", "Rescue request not found."));
        if (!r.catcherId.equals(c.id)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this rescue request.");
        }
        if (r.status != RescueStatus.ACCEPTED) {
            throw ApiException.conflict("INVALID_STATE", "Only accepted rescue requests can be marked as completed.");
        }
        r.status = RescueStatus.DONE;
        r.updatedAt = clock.instant();
        return toView(rescues.save(r));
    }

    private Catcher requireCatcher(AuthPrincipal me) {
        if (me == null || me.role() != Role.CATCHER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only snake catchers can access this.");
        }
        return catchers.findById(me.id()).filter(x -> x.active)
                .orElseThrow(() -> ApiException.notFound("CATCHER_NOT_FOUND", "Snake catcher not found."));
    }

    public static CatcherRescueView toView(Rescue r) {
        return new CatcherRescueView(r.id, r.catcherId, r.phone, r.lat, r.lng, r.notes, r.status, r.createdAt, r.updatedAt);
    }

    public static CatcherSummary toSummary(Catcher c) {
        return new CatcherSummary(c.id, c.name, c.phone, c.district);
    }
}
