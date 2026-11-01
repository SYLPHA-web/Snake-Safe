package com.snakesafe.staff;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.snakesafe.alert.AlertService;
import com.snakesafe.alert.AlertStatus;
import com.snakesafe.alert.StaffAlertView;
import com.snakesafe.auth.AuthPrincipal;
import com.snakesafe.auth.Role;
import com.snakesafe.hospital.HospitalService;
import com.snakesafe.hospital.HospitalView;
import com.snakesafe.hospital.StockInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    public record LoginRequest(@NotBlank @Size(max = 60) String username, @NotBlank @Size(max = 100) String password) {
    }

    public record LoginResponse(String token, int expiresInHours, long staffId, String name, Long hospitalId) {
    }

    public record Home(String staffName, HospitalView hospital, long waitingAlerts, long onTheWayAlerts) {
    }

    public record StockRequest(@NotNull @Min(0) @Max(10000) Integer vials) {
    }

    public record DeclineRequest(@Size(max = 200) String reason) {
    }

    private final StaffAuthService auth;
    private final HospitalService hospitals;
    private final AlertService alerts;
    private final com.snakesafe.auth.JwtService jwt;

    public StaffController(StaffAuthService auth, HospitalService hospitals, AlertService alerts,
            com.snakesafe.auth.JwtService jwt) {
        this.auth = auth;
        this.hospitals = hospitals;
        this.alerts = alerts;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        StaffAuthService.Login login = auth.login(req.username(), req.password(), Role.STAFF, http.getRemoteAddr());
        Staff s = login.staff();
        return new LoginResponse(login.token(), jwt.expiresInHours(), s.id, s.name, s.hospitalId);
    }

    @GetMapping("/home")
    public Home home(@AuthenticationPrincipal AuthPrincipal me) {
        Staff s = auth.requireHospitalStaff(me);
        return new Home(s.name, hospitals.viewOf(s.hospitalId, null, null),
                alerts.countWithStatus(s.hospitalId, AlertStatus.WAITING),
                alerts.countWithStatus(s.hospitalId, AlertStatus.CONFIRMED));
    }

    /** Sets the vial count and stamps it "now", which makes the stock Verified. */
    @PutMapping("/stock")
    public StockInfo updateStock(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody StockRequest req) {
        Staff s = auth.requireHospitalStaff(me);
        return hospitals.updateStock(s.hospitalId, req.vials());
    }

    @GetMapping("/alerts")
    public List<StaffAlertView> alerts(@AuthenticationPrincipal AuthPrincipal me) {
        return alerts.staffAlerts(auth.requireHospitalStaff(me).hospitalId);
    }

    @PostMapping("/alerts/{id}/accept")
    public StaffAlertView accept(@AuthenticationPrincipal AuthPrincipal me, @PathVariable String id) {
        return alerts.staffAccept(auth.requireHospitalStaff(me).hospitalId, id);
    }

    @PostMapping("/alerts/{id}/decline")
    public StaffAlertView decline(@AuthenticationPrincipal AuthPrincipal me, @PathVariable String id,
            @Valid @RequestBody(required = false) DeclineRequest req) {
        return alerts.staffDecline(auth.requireHospitalStaff(me).hospitalId, id, req == null ? null : req.reason());
    }

    @PostMapping("/alerts/{id}/arrived")
    public StaffAlertView arrived(@AuthenticationPrincipal AuthPrincipal me, @PathVariable String id) {
        return alerts.staffArrived(auth.requireHospitalStaff(me).hospitalId, id);
    }
}
