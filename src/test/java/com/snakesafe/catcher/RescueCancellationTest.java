package com.snakesafe.catcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import com.snakesafe.auth.AuthPrincipal;
import com.snakesafe.auth.Role;
import com.snakesafe.config.RateLimiter;
import com.snakesafe.web.ApiException;

class RescueCancellationTest {

    private Map<Long, Catcher> catchersMap;
    private Map<String, Rescue> rescuesMap;
    private CatcherRepository catchers;
    private RescueRepository rescues;
    private RateLimiter limiter;
    private Clock clock;
    private CatcherController controller;

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-03-01T10:05:00Z");

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        catchersMap = new ConcurrentHashMap<>();
        rescuesMap = new ConcurrentHashMap<>();
        clock = Clock.fixed(T1, ZoneOffset.UTC);
        limiter = new RateLimiter(clock);

        catchers = (CatcherRepository) Proxy.newProxyInstance(
                CatcherRepository.class.getClassLoader(),
                new Class<?>[] { CatcherRepository.class },
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        return Optional.ofNullable(catchersMap.get(args[0]));
                    }
                    return null;
                });

        rescues = (RescueRepository) Proxy.newProxyInstance(
                RescueRepository.class.getClassLoader(),
                new Class<?>[] { RescueRepository.class },
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        return Optional.ofNullable(rescuesMap.get(args[0]));
                    }
                    if ("save".equals(method.getName())) {
                        Rescue r = (Rescue) args[0];
                        rescuesMap.put(r.id, r);
                        return r;
                    }
                    return null;
                });

        controller = new CatcherController(catchers, rescues, limiter, clock);

        Catcher c = new Catcher();
        c.id = 1L;
        c.name = "Ramesh Kumar";
        c.phone = "+919876543210";
        c.district = "Bengaluru";
        c.active = true;
        catchersMap.put(1L, c);
    }

    private Rescue sampleRescue(String id, Long userId, RescueStatus status) {
        Rescue r = new Rescue();
        r.id = id;
        r.userId = userId;
        r.catcherId = 1L;
        r.lat = 12.97;
        r.lng = 77.59;
        r.notes = "Behind garden shed";
        r.phone = "+919876543210";
        r.status = status;
        r.createdAt = T0;
        r.updatedAt = T0;
        return r;
    }

    @Test
    void cancelGuestRescueWhenRequestedSucceeds() {
        Rescue r = sampleRescue("rec-guest", null, RescueStatus.REQUESTED);
        rescuesMap.put(r.id, r);

        CatcherController.RescueView view = controller.cancel("rec-guest", null);

        assertThat(view.id()).isEqualTo("rec-guest");
        assertThat(view.status()).isEqualTo(RescueStatus.CANCELLED);
        assertThat(r.status).isEqualTo(RescueStatus.CANCELLED);
        assertThat(r.updatedAt).isEqualTo(T1);
        assertThat(rescuesMap.get("rec-guest").status).isEqualTo(RescueStatus.CANCELLED);
    }

    @Test
    void cancelWhenAlreadyCancelledIsIdempotent() {
        Rescue r = sampleRescue("rec-already-cancelled", null, RescueStatus.CANCELLED);
        rescuesMap.put(r.id, r);

        CatcherController.RescueView view = controller.cancel("rec-already-cancelled", null);

        assertThat(view.status()).isEqualTo(RescueStatus.CANCELLED);
    }

    @Test
    void cancelWhenAcceptedThrowsConflict() {
        Rescue r = sampleRescue("rec-accepted", null, RescueStatus.ACCEPTED);
        rescuesMap.put(r.id, r);

        assertThatThrownBy(() -> controller.cancel("rec-accepted", null))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("INVALID_STATE");
                });
    }

    @Test
    void cancelWhenDoneThrowsConflict() {
        Rescue r = sampleRescue("rec-done", null, RescueStatus.DONE);
        rescuesMap.put(r.id, r);

        assertThatThrownBy(() -> controller.cancel("rec-done", null))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("INVALID_STATE");
                });
    }

    @Test
    void cancelNonExistentRescueThrowsNotFound() {
        assertThatThrownBy(() -> controller.cancel("missing", null))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getCode()).isEqualTo("RESCUE_NOT_FOUND");
                });
    }

    @Test
    void authenticatedUserCanCancelOwnRescue() {
        Rescue r = sampleRescue("rec-user", 42L, RescueStatus.REQUESTED);
        rescuesMap.put(r.id, r);

        AuthPrincipal me = new AuthPrincipal(42L, Role.USER);
        CatcherController.RescueView view = controller.cancel("rec-user", me);

        assertThat(view.status()).isEqualTo(RescueStatus.CANCELLED);
        assertThat(r.status).isEqualTo(RescueStatus.CANCELLED);
        assertThat(rescuesMap.get("rec-user").status).isEqualTo(RescueStatus.CANCELLED);
    }

    @Test
    void differentAuthenticatedUserCannotCancelOthersRescue() {
        Rescue r = sampleRescue("rec-user", 42L, RescueStatus.REQUESTED);
        rescuesMap.put(r.id, r);

        AuthPrincipal attacker = new AuthPrincipal(99L, Role.USER);
        assertThatThrownBy(() -> controller.cancel("rec-user", attacker))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getCode()).isEqualTo("FORBIDDEN");
                });
    }

    @Test
    void guestCannotCancelAuthenticatedUsersRescue() {
        Rescue r = sampleRescue("rec-user", 42L, RescueStatus.REQUESTED);
        rescuesMap.put(r.id, r);

        assertThatThrownBy(() -> controller.cancel("rec-user", null))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getCode()).isEqualTo("FORBIDDEN");
                });
    }

    @Test
    void creatingRescueAsAuthenticatedUserStoresUserId() {
        AuthPrincipal me = new AuthPrincipal(42L, Role.USER);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        CatcherController.RescueCreate req = new CatcherController.RescueCreate(1L, 12.97, 77.59, "Shed", "9876543210");
        CatcherController.RescueView view = controller.request(req, me, request);

        assertThat(view.catcherId()).isEqualTo(1L);
        assertThat(view.status()).isEqualTo(RescueStatus.REQUESTED);
        Rescue saved = rescuesMap.get(view.id());
        assertThat(saved).isNotNull();
        assertThat(saved.userId).isEqualTo(42L);
    }

    @Test
    void creatingRescueAsGuestStoresNullUserId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        CatcherController.RescueCreate req = new CatcherController.RescueCreate(1L, 12.97, 77.59, "Shed", "9876543210");
        CatcherController.RescueView view = controller.request(req, null, request);

        assertThat(view.catcherId()).isEqualTo(1L);
        assertThat(view.status()).isEqualTo(RescueStatus.REQUESTED);
        Rescue saved = rescuesMap.get(view.id());
        assertThat(saved).isNotNull();
        assertThat(saved.userId).isNull();
    }
}
