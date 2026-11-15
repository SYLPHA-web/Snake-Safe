package com.snakesafe.catcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.snakesafe.auth.AuthPrincipal;
import com.snakesafe.auth.JwtService;
import com.snakesafe.auth.OtpCode;
import com.snakesafe.auth.OtpCodeRepository;
import com.snakesafe.auth.OtpService;
import com.snakesafe.auth.Role;
import com.snakesafe.auth.UserRepository;
import com.snakesafe.config.AppProperties;
import com.snakesafe.config.RateLimiter;
import com.snakesafe.web.ApiException;

class CatcherPortalTest {

    private Map<Long, Catcher> catchersMap;
    private Map<String, Rescue> rescuesMap;
    private List<OtpCode> otpCodesList;
    private AtomicLong codeIdGen;

    private CatcherRepository catchers;
    private RescueRepository rescues;
    private OtpCodeRepository otpCodes;
    private UserRepository users;
    private JwtService jwt;
    private OtpService otpService;
    private Clock clock;
    private CatcherPortalController controller;

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-03-01T10:05:00Z");

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        catchersMap = new ConcurrentHashMap<>();
        rescuesMap = new ConcurrentHashMap<>();
        otpCodesList = new ArrayList<>();
        codeIdGen = new AtomicLong(1);
        clock = Clock.fixed(T1, ZoneOffset.UTC);

        catchers = (CatcherRepository) Proxy.newProxyInstance(
                CatcherRepository.class.getClassLoader(),
                new Class<?>[] { CatcherRepository.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findById".equals(name)) {
                        return Optional.ofNullable(catchersMap.get(args[0]));
                    }
                    if ("findByPhoneAndActiveTrue".equals(name)
                            || "findFirstByPhoneAndActiveTrueOrderByIdAsc".equals(name)) {
                        String phone = (String) args[0];
                        return catchersMap.values().stream()
                                .filter(c -> phone.equals(c.phone) && c.active)
                                .sorted(Comparator.comparing(c -> c.id))
                                .findFirst();
                    }
                    if ("save".equals(name)) {
                        Catcher c = (Catcher) args[0];
                        if (c.id == null) {
                            c.id = (long) (catchersMap.size() + 1);
                        }
                        catchersMap.put(c.id, c);
                        return c;
                    }
                    return null;
                });

        rescues = (RescueRepository) Proxy.newProxyInstance(
                RescueRepository.class.getClassLoader(),
                new Class<?>[] { RescueRepository.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findById".equals(name)) {
                        return Optional.ofNullable(rescuesMap.get(args[0]));
                    }
                    if ("findByCatcherIdOrderByCreatedAtDesc".equals(name)) {
                        Long catcherId = (Long) args[0];
                        return rescuesMap.values().stream()
                                .filter(r -> catcherId.equals(r.catcherId))
                                .sorted(Comparator.comparing((Rescue r) -> r.createdAt).reversed())
                                .toList();
                    }
                    if ("save".equals(name)) {
                        Rescue r = (Rescue) args[0];
                        rescuesMap.put(r.id, r);
                        return r;
                    }
                    return null;
                });

        otpCodes = (OtpCodeRepository) Proxy.newProxyInstance(
                OtpCodeRepository.class.getClassLoader(),
                new Class<?>[] { OtpCodeRepository.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findFirstByPhoneAndUsedFalseOrderByCreatedAtDesc".equals(name)) {
                        String phone = (String) args[0];
                        return otpCodesList.stream()
                                .filter(c -> phone.equals(c.phone) && !c.used)
                                .max(Comparator.comparing(c -> c.createdAt));
                    }
                    if ("invalidateAll".equals(name)) {
                        String phone = (String) args[0];
                        otpCodesList.stream()
                                .filter(c -> phone.equals(c.phone))
                                .forEach(c -> c.used = true);
                        return null;
                    }
                    if ("deleteExpiredBefore".equals(name)) {
                        Instant before = (Instant) args[0];
                        otpCodesList.removeIf(c -> c.expiresAt.isBefore(before));
                        return null;
                    }
                    if ("save".equals(name)) {
                        OtpCode code = (OtpCode) args[0];
                        if (code.id == null) {
                            code.id = codeIdGen.getAndIncrement();
                            otpCodesList.add(code);
                        }
                        return code;
                    }
                    return null;
                });

        users = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[] { UserRepository.class },
                (proxy, method, args) -> Optional.empty());

        AppProperties props = new AppProperties(
                "SnakeSafe", "/img/logo.svg", "Tagline", "108", "1800-116-117", 2, "en", "DEMO", "STUB", "DEMO",
                5, 2, false, false, "Asia/Kolkata", "LOCAL", "target/test-photos", false,
                "01234567890123456789012345678901", 24,
                null, null, null, false, false, "admin", "admin123", "staff123");

        RateLimiter limiter = new RateLimiter(clock);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        jwt = new JwtService(props, clock);
        otpService = new OtpService(otpCodes, users, (phone, text) -> {}, jwt, props, clock, limiter, encoder);

        controller = new CatcherPortalController(catchers, rescues, otpService, jwt, clock);

        // Seed sample catchers
        Catcher c1 = new Catcher();
        c1.id = 1L;
        c1.name = "Sample Catcher Ravi";
        c1.phone = "+919000000001";
        c1.district = "Bengaluru Urban";
        c1.lat = 12.9352;
        c1.lng = 77.6245;
        c1.active = true;
        catchersMap.put(1L, c1);

        Catcher c2 = new Catcher();
        c2.id = 2L;
        c2.name = "Sample Catcher Manju";
        c2.phone = "+919000000002";
        c2.district = "Mysuru";
        c2.lat = 12.3051;
        c2.lng = 76.6552;
        c2.active = true;
        catchersMap.put(2L, c2);

        Catcher cInactive = new Catcher();
        cInactive.id = 3L;
        cInactive.name = "Inactive Catcher";
        cInactive.phone = "+919000000003";
        cInactive.district = "Dharwad";
        cInactive.active = false;
        catchersMap.put(3L, cInactive);
    }

    private Rescue sampleRescue(String id, Long catcherId, RescueStatus status) {
        Rescue r = new Rescue();
        r.id = id;
        r.userId = 42L;
        r.catcherId = catcherId;
        r.lat = 12.97;
        r.lng = 77.59;
        r.notes = "In garden";
        r.phone = "+919876543210";
        r.status = status;
        r.createdAt = T0;
        r.updatedAt = T0;
        return r;
    }

    // --- Authentication tests ---

    @Test
    void sendOtpWithValidRegisteredCatcherPhoneSucceeds() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        var result = controller.sendOtp(new CatcherPortalController.SendOtpRequest("9000000001"), request);
        assertThat(result.sent()).isTrue();
        assertThat(result.mode()).isEqualTo("DEMO");
    }

    @Test
    void sendOtpWithUnregisteredPhoneThrows400() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertThatThrownBy(() -> controller.sendOtp(new CatcherPortalController.SendOtpRequest("9999999999"), request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getCode()).isEqualTo("CATCHER_NOT_REGISTERED");
                });
    }

    @Test
    void sendOtpWithInactiveCatcherThrows400() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertThatThrownBy(() -> controller.sendOtp(new CatcherPortalController.SendOtpRequest("9000000003"), request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getCode()).isEqualTo("CATCHER_NOT_REGISTERED");
                });
    }

    @Test
    void sendOtpWithInvalidPhoneThrows400() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertThatThrownBy(() -> controller.sendOtp(new CatcherPortalController.SendOtpRequest("invalid-phone"), request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("INVALID_PHONE"));
    }

    @Test
    void verifyOtpWithValidCodeReturnsCatcherJwt() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        controller.sendOtp(new CatcherPortalController.SendOtpRequest("9000000001"), request);

        var login = controller.verifyOtp(new CatcherPortalController.VerifyOtpRequest("9000000001", "123456"));
        assertThat(login.token()).isNotBlank();
        assertThat(login.catcher().id()).isEqualTo(1L);
        assertThat(login.catcher().name()).isEqualTo("Sample Catcher Ravi");

        AuthPrincipal principal = jwt.parse(login.token()).orElseThrow();
        assertThat(principal.id()).isEqualTo(1L);
        assertThat(principal.role()).isEqualTo(Role.CATCHER);
    }

    @Test
    void otpLoginHandlesDuplicateActiveRecordsSafelyWithoutError() {
        // Given an existing duplicate active catcher record with the same phone number
        Catcher cDuplicate = new Catcher();
        cDuplicate.id = 99L;
        cDuplicate.name = "Duplicate Catcher";
        cDuplicate.phone = "+919000000001";
        cDuplicate.district = "Bengaluru Urban";
        cDuplicate.active = true;
        catchersMap.put(99L, cDuplicate);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        // sendOtp must not throw IncorrectResultSizeDataAccessException or 500 error
        var sendResult = controller.sendOtp(new CatcherPortalController.SendOtpRequest("9000000001"), request);
        assertThat(sendResult.sent()).isTrue();

        // verifyOtp must safely and deterministically select the first active catcher
        var login = controller.verifyOtp(new CatcherPortalController.VerifyOtpRequest("9000000001", "123456"));
        assertThat(login.token()).isNotBlank();
        assertThat(login.catcher().id()).isEqualTo(1L);
        assertThat(login.catcher().name()).isEqualTo("Sample Catcher Ravi");
    }

    @Test
    void verifyOtpWithInvalidCodeThrows400() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        controller.sendOtp(new CatcherPortalController.SendOtpRequest("9000000001"), request);

        assertThatThrownBy(() -> controller.verifyOtp(new CatcherPortalController.VerifyOtpRequest("9000000001", "000000")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("INVALID_CODE"));
    }

    // --- Profile & Access tests ---

    @Test
    void getMeReturnsCatcherDetails() {
        AuthPrincipal me = new AuthPrincipal(1L, Role.CATCHER);
        var summary = controller.me(me);
        assertThat(summary.id()).isEqualTo(1L);
        assertThat(summary.name()).isEqualTo("Sample Catcher Ravi");
        assertThat(summary.district()).isEqualTo("Bengaluru Urban");
    }

    @Test
    void getMeWithUserRoleThrowsForbidden() {
        AuthPrincipal me = new AuthPrincipal(1L, Role.USER);
        assertThatThrownBy(() -> controller.me(me))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void getMeWithNullPrincipalThrowsForbidden() {
        assertThatThrownBy(() -> controller.me(null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // --- Listing Rescues tests ---

    @Test
    void listRescuesReturnsOnlyAssignedRescuesForAuthenticatedCatcher() {
        Rescue r1 = sampleRescue("r-1", 1L, RescueStatus.REQUESTED);
        Rescue r2 = sampleRescue("r-2", 1L, RescueStatus.ACCEPTED);
        Rescue r3 = sampleRescue("r-3", 2L, RescueStatus.REQUESTED); // Belongs to Catcher 2
        rescuesMap.put(r1.id, r1);
        rescuesMap.put(r2.id, r2);
        rescuesMap.put(r3.id, r3);

        AuthPrincipal me = new AuthPrincipal(1L, Role.CATCHER);
        var list = controller.listRescues(me);

        assertThat(list).hasSize(2);
        assertThat(list).extracting("id").containsExactlyInAnyOrder("r-1", "r-2");
        assertThat(list).noneMatch(r -> "r-3".equals(r.id()));
    }

    // --- Accept Action tests ---

    @Test
    void acceptRescueFromRequestedSucceeds() {
        Rescue r = sampleRescue("r-acc", 1L, RescueStatus.REQUESTED);
        rescuesMap.put(r.id, r);

        AuthPrincipal me = new AuthPrincipal(1L, Role.CATCHER);
        var updated = controller.accept("r-acc", me);

        assertThat(updated.status()).isEqualTo(RescueStatus.ACCEPTED);
        assertThat(rescuesMap.get("r-acc").status).isEqualTo(RescueStatus.ACCEPTED);
        assertThat(rescuesMap.get("r-acc").updatedAt).isEqualTo(T1);
    }

    @Test
    void acceptRescueFromOtherCatcherThrowsForbidden() {
        Rescue r = sampleRescue("r-other", 2L, RescueStatus.REQUESTED);
        rescuesMap.put(r.id, r);

        AuthPrincipal me = new AuthPrincipal(1L, Role.CATCHER);
        assertThatThrownBy(() -> controller.accept("r-other", me))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void acceptRescueAlreadyAcceptedThrowsConflict() {
        Rescue r = sampleRescue("r-already-acc", 1L, RescueStatus.ACCEPTED);
        rescuesMap.put(r.id, r);

        AuthPrincipal me = new AuthPrincipal(1L, Role.CATCHER);
        assertThatThrownBy(() -> controller.accept("r-already-acc", me))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("INVALID_STATE");
                });
    }

    @Test
    void acceptRescueCancelledByPatientThrowsConflict() {
        Rescue r = sampleRescue("r-cancelled", 1L, RescueStatus.CANCELLED);
        rescuesMap.put(r.id, r);

        AuthPrincipal me = new AuthPrincipal(1L, Role.CATCHER);
        assertThatThrownBy(() -> controller.accept("r-cancelled", me))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("INVALID_STATE");
                });
    }

    // --- Decline Action tests ---

    @Test
    void declineRescueFromRequestedSucceeds() {
        Rescue r = sampleRescue("r-dec", 1L, RescueStatus.REQUESTED);
        rescuesMap.put(r.id, r);

        AuthPrincipal me = new AuthPrincipal(1L, Role.CATCHER);
        var updated = controller.decline("r-dec", me);

        assertThat(updated.status()).isEqualTo(RescueStatus.DECLINED);
        assertThat(rescuesMap.get("r-dec").status).isEqualTo(RescueStatus.DECLINED);
        assertThat(rescuesMap.get("r-dec").updatedAt).isEqualTo(T1);
    }

    @Test
    void declineRescueFromAcceptedThrowsConflict() {
        Rescue r = sampleRescue("r-accepted", 1L, RescueStatus.ACCEPTED);
        rescuesMap.put(r.id, r);

        AuthPrincipal me = new AuthPrincipal(1L, Role.CATCHER);
        assertThatThrownBy(() -> controller.decline("r-accepted", me))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void declineRescueFromOtherCatcherThrowsForbidden() {
        Rescue r = sampleRescue("r-other-dec", 2L, RescueStatus.REQUESTED);
        rescuesMap.put(r.id, r);

        AuthPrincipal me = new AuthPrincipal(1L, Role.CATCHER);
        assertThatThrownBy(() -> controller.decline("r-other-dec", me))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // --- Done (Completed) Action tests ---

    @Test
    void markDoneFromAcceptedSucceeds() {
        Rescue r = sampleRescue("r-done", 1L, RescueStatus.ACCEPTED);
        rescuesMap.put(r.id, r);

        AuthPrincipal me = new AuthPrincipal(1L, Role.CATCHER);
        var updated = controller.markDone("r-done", me);

        assertThat(updated.status()).isEqualTo(RescueStatus.DONE);
        assertThat(rescuesMap.get("r-done").status).isEqualTo(RescueStatus.DONE);
        assertThat(rescuesMap.get("r-done").updatedAt).isEqualTo(T1);
    }

    @Test
    void markDoneFromRequestedThrowsConflict() {
        Rescue r = sampleRescue("r-req-done", 1L, RescueStatus.REQUESTED);
        rescuesMap.put(r.id, r);

        AuthPrincipal me = new AuthPrincipal(1L, Role.CATCHER);
        assertThatThrownBy(() -> controller.markDone("r-req-done", me))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void markDoneFromDeclinedThrowsConflict() {
        Rescue r = sampleRescue("r-dec-done", 1L, RescueStatus.DECLINED);
        rescuesMap.put(r.id, r);

        AuthPrincipal me = new AuthPrincipal(1L, Role.CATCHER);
        assertThatThrownBy(() -> controller.markDone("r-dec-done", me))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void markDoneFromOtherCatcherThrowsForbidden() {
        Rescue r = sampleRescue("r-other-done", 2L, RescueStatus.ACCEPTED);
        rescuesMap.put(r.id, r);

        AuthPrincipal me = new AuthPrincipal(1L, Role.CATCHER);
        assertThatThrownBy(() -> controller.markDone("r-other-done", me))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
