package com.snakesafe.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.snakesafe.alert.Alert;
import com.snakesafe.alert.AlertRepository;
import com.snakesafe.alert.AlertStatus;
import com.snakesafe.alert.PatientType;
import com.snakesafe.auth.JwtService;
import com.snakesafe.auth.Role;
import com.snakesafe.catcher.CatcherController;
import com.snakesafe.catcher.CatcherRepository;
import com.snakesafe.catcher.Rescue;
import com.snakesafe.catcher.RescueRepository;
import com.snakesafe.catcher.RescueStatus;
import com.snakesafe.config.Sanitizer;
import com.snakesafe.hospital.Hospital;
import com.snakesafe.hospital.HospitalRepository;
import com.snakesafe.hospital.HospitalService;
import com.snakesafe.hospital.HospitalView;
import com.snakesafe.hospital.StockRepository;
import com.snakesafe.staff.Staff;
import com.snakesafe.staff.StaffAuthService;
import com.snakesafe.staff.StaffRepository;
import com.snakesafe.web.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Admin: sign in, hospitals, hospital staff, alerts and rescue requests. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record LoginRequest(@NotBlank @Size(max = 60) String username, @NotBlank @Size(max = 100) String password) {
    }

    public record LoginResponse(String token, int expiresInHours, String name) {
    }

    public record HospitalRequest(@NotBlank @Size(max = 150) String name, @NotBlank @Size(max = 100) String district,
            @NotBlank @Size(max = 300) String address,
            @NotBlank @Pattern(regexp = "[0-9+\\-() ]{5,20}", message = "must be a phone number") String phone,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
            @Min(0) Integer vials, Boolean active) {
    }

    public record StaffRequest(@NotBlank @Size(min = 3, max = 60) String username,
            @Size(min = 8, max = 100) String password, @NotBlank @Size(max = 100) String name,
            @NotNull Long hospitalId, Boolean active) {
    }

    public record StaffView(Long id, String username, String name, Long hospitalId, boolean active) {
    }

    public record AdminAlertView(String id, AlertStatus status, PatientType patientType, Long hospitalId,
            String hospitalName, Instant createdAt, Instant sentAt, Instant respondedAt, Instant confirmedAt,
            Instant arrivedAt, Instant cancelledAt) {
    }

    public record StatusRequest(@NotNull RescueStatus status) {
    }

    private final StaffAuthService auth;
    private final JwtService jwt;
    private final HospitalRepository hospitalRepo;
    private final HospitalService hospitals;
    private final StockRepository stocks;
    private final StaffRepository staffRepo;
    private final AlertRepository alertRepo;
    private final RescueRepository rescueRepo;
    private final CatcherRepository catcherRepo;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AdminController(StaffAuthService auth, JwtService jwt, HospitalRepository hospitalRepo,
            HospitalService hospitals, StockRepository stocks, StaffRepository staffRepo, AlertRepository alertRepo,
            RescueRepository rescueRepo, CatcherRepository catcherRepo, PasswordEncoder encoder, Clock clock) {
        this.auth = auth;
        this.jwt = jwt;
        this.hospitalRepo = hospitalRepo;
        this.hospitals = hospitals;
        this.stocks = stocks;
        this.staffRepo = staffRepo;
        this.alertRepo = alertRepo;
        this.rescueRepo = rescueRepo;
        this.catcherRepo = catcherRepo;
        this.encoder = encoder;
        this.clock = clock;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        StaffAuthService.Login login = auth.login(req.username(), req.password(), Role.ADMIN, http.getRemoteAddr());
        return new LoginResponse(login.token(), jwt.expiresInHours(), login.staff().name);
    }

    // ---- hospitals ----

    @GetMapping("/hospitals")
    @Transactional(readOnly = true)
    public List<HospitalView> listHospitals() {
        return hospitalRepo.findAll().stream().map(h -> hospitals.viewOf(h.id, null, null))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList();
    }

    @PostMapping("/hospitals")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public HospitalView createHospital(@Valid @RequestBody HospitalRequest req) {
        Hospital h = hospitalRepo.save(fill(new Hospital(), req));
        if (req.vials() != null) {
            hospitals.updateStock(h.id, req.vials());
        }
        return hospitals.viewOf(h.id, null, null);
    }

    @PutMapping("/hospitals/{id}")
    @Transactional
    public HospitalView updateHospital(@PathVariable long id, @Valid @RequestBody HospitalRequest req) {
        Hospital h = hospitalRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("HOSPITAL_NOT_FOUND", "Hospital not found."));
        hospitalRepo.save(fill(h, req));
        if (req.vials() != null) {
            hospitals.updateStock(h.id, req.vials());
        }
        return hospitals.viewOf(h.id, null, null);
    }

    /** Hospitals are hidden, not erased, so old alerts keep their history. */
    @DeleteMapping("/hospitals/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hideHospital(@PathVariable long id) {
        Hospital h = hospitalRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("HOSPITAL_NOT_FOUND", "Hospital not found."));
        h.active = false;
        hospitalRepo.save(h);
    }

    // ---- staff ----

    @GetMapping("/staff")
    public List<StaffView> listStaff() {
        return staffRepo.findAllByOrderByUsernameAsc().stream().map(AdminController::toView).toList();
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffView createStaff(@Valid @RequestBody StaffRequest req) {
        if (req.password() == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "password is required (at least 8 characters).");
        }
        if (staffRepo.findByUsernameIgnoreCase(req.username().strip()).isPresent()) {
            throw ApiException.conflict("USERNAME_TAKEN", "That username is already used.");
        }
        requireHospital(req.hospitalId());
        Staff s = new Staff();
        s.username = req.username().strip();
        s.passwordHash = encoder.encode(req.password());
        s.name = Sanitizer.clean(req.name());
        s.role = Role.STAFF;
        s.hospitalId = req.hospitalId();
        s.active = req.active() == null || req.active();
        return toView(staffRepo.save(s));
    }

    @PutMapping("/staff/{id}")
    public StaffView updateStaff(@PathVariable long id, @Valid @RequestBody StaffRequest req) {
        Staff s = staffRepo.findById(id).filter(x -> x.role == Role.STAFF)
                .orElseThrow(() -> ApiException.notFound("STAFF_NOT_FOUND", "Staff member not found."));
        staffRepo.findByUsernameIgnoreCase(req.username().strip()).filter(o -> !o.id.equals(id)).ifPresent(o -> {
            throw ApiException.conflict("USERNAME_TAKEN", "That username is already used.");
        });
        requireHospital(req.hospitalId());
        s.username = req.username().strip();
        s.name = Sanitizer.clean(req.name());
        s.hospitalId = req.hospitalId();
        if (req.active() != null) {
            s.active = req.active();
        }
        if (req.password() != null) {
            s.passwordHash = encoder.encode(req.password());
        }
        return toView(staffRepo.save(s));
    }

    /** Switches the account off instead of deleting it. */
    @DeleteMapping("/staff/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateStaff(@PathVariable long id) {
        Staff s = staffRepo.findById(id).filter(x -> x.role == Role.STAFF)
                .orElseThrow(() -> ApiException.notFound("STAFF_NOT_FOUND", "Staff member not found."));
        s.active = false;
        staffRepo.save(s);
    }

    // ---- alerts and rescues ----

    /** No names, ages or locations: admins see how the flow is working, not who was bitten. */
    @GetMapping("/alerts")
    @Transactional(readOnly = true)
    public List<AdminAlertView> alerts() {
        Map<Long, String> names = hospitalRepo.findAll().stream().collect(Collectors.toMap(h -> h.id, h -> h.name));
        return alertRepo.findTop200ByOrderByCreatedAtDesc().stream()
                .map(a -> new AdminAlertView(a.id, a.status, a.patientType, a.hospitalId, names.get(a.hospitalId),
                        a.createdAt, a.sentAt, a.respondedAt, a.confirmedAt, a.arrivedAt, a.cancelledAt))
                .toList();
    }

    @GetMapping("/rescues")
    @Transactional(readOnly = true)
    public List<CatcherController.RescueView> rescues() {
        Map<Long, com.snakesafe.catcher.Catcher> byId = catcherRepo.findAll().stream()
                .collect(Collectors.toMap(c -> c.id, Function.identity()));
        return rescueRepo.findTop200ByOrderByCreatedAtDesc().stream()
                .map(r -> CatcherController.toView(r, byId.get(r.catcherId))).toList();
    }

    @PutMapping("/rescues/{id}/status")
    public CatcherController.RescueView rescueStatus(@PathVariable String id, @Valid @RequestBody StatusRequest req) {
        Rescue r = rescueRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("RESCUE_NOT_FOUND", "Rescue request not found."));
        r.status = req.status();
        r.updatedAt = clock.instant();
        rescueRepo.save(r);
        return CatcherController.toView(r, catcherRepo.findById(r.catcherId).orElse(null));
    }

    // ---- helpers ----

    private void requireHospital(Long id) {
        if (!hospitalRepo.existsById(id)) {
            throw ApiException.badRequest("HOSPITAL_NOT_FOUND", "That hospital does not exist.");
        }
    }

    private static Hospital fill(Hospital h, HospitalRequest r) {
        h.name = Sanitizer.clean(r.name());
        h.district = Sanitizer.clean(r.district());
        h.address = Sanitizer.clean(r.address());
        h.phone = r.phone().strip();
        h.lat = r.lat();
        h.lng = r.lng();
        h.active = r.active() == null || r.active();
        return h;
    }

    private static StaffView toView(Staff s) {
        return new StaffView(s.id, s.username, s.name, s.hospitalId, s.active);
    }
}
