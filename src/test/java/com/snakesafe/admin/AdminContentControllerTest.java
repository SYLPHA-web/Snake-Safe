package com.snakesafe.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.snakesafe.catcher.Catcher;
import com.snakesafe.catcher.CatcherRepository;
import com.snakesafe.identify.SnakePhotoRepository;
import com.snakesafe.snake.SnakeRepository;
import com.snakesafe.web.ApiException;

class AdminContentControllerTest {

    private Map<Long, Catcher> catchersMap;
    private CatcherRepository catcherRepo;
    private SnakeRepository snakeRepo;
    private SnakePhotoRepository photoRepo;
    private AdminContentController controller;

    @BeforeEach
    void setUp() {
        catchersMap = new ConcurrentHashMap<>();

        catcherRepo = (CatcherRepository) Proxy.newProxyInstance(
                CatcherRepository.class.getClassLoader(),
                new Class<?>[] { CatcherRepository.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findAll".equals(name)) {
                        return new ArrayList<>(catchersMap.values());
                    }
                    if ("findById".equals(name)) {
                        return Optional.ofNullable(catchersMap.get(args[0]));
                    }
                    if ("existsById".equals(name)) {
                        return catchersMap.containsKey(args[0]);
                    }
                    if ("existsByPhoneAndActiveTrue".equals(name)) {
                        String phone = (String) args[0];
                        return catchersMap.values().stream()
                                .anyMatch(c -> phone.equals(c.phone) && c.active);
                    }
                    if ("existsByPhoneAndActiveTrueAndIdNot".equals(name)) {
                        String phone = (String) args[0];
                        Long id = (Long) args[1];
                        return catchersMap.values().stream()
                                .anyMatch(c -> phone.equals(c.phone) && c.active && !c.id.equals(id));
                    }
                    if ("findFirstByPhoneAndActiveTrueOrderByIdAsc".equals(name)
                            || "findByPhoneAndActiveTrue".equals(name)) {
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

        snakeRepo = (SnakeRepository) Proxy.newProxyInstance(
                SnakeRepository.class.getClassLoader(),
                new Class<?>[] { SnakeRepository.class },
                (proxy, method, args) -> null);

        photoRepo = (SnakePhotoRepository) Proxy.newProxyInstance(
                SnakePhotoRepository.class.getClassLoader(),
                new Class<?>[] { SnakePhotoRepository.class },
                (proxy, method, args) -> null);

        controller = new AdminContentController(snakeRepo, catcherRepo, photoRepo, null);

        // Preload an active catcher and an inactive catcher
        Catcher c1 = new Catcher();
        c1.id = 1L;
        c1.name = "Active Catcher One";
        c1.phone = "+919000000001";
        c1.district = "Bengaluru Urban";
        c1.lat = 12.97;
        c1.lng = 77.59;
        c1.active = true;
        catchersMap.put(1L, c1);

        Catcher c2 = new Catcher();
        c2.id = 2L;
        c2.name = "Inactive Catcher Two";
        c2.phone = "+919000000002";
        c2.district = "Mysuru";
        c2.lat = 12.30;
        c2.lng = 76.65;
        c2.active = false;
        catchersMap.put(2L, c2);
    }

    @Test
    void createCatcherWithUniquePhoneSucceeds() {
        var req = new AdminContentController.CatcherRequest(
                "New Catcher", "+919000000099", "Mangaluru", 12.91, 74.85, true);
        var created = controller.createCatcher(req);

        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("New Catcher");
        assertThat(created.phone()).isEqualTo("+919000000099");
        assertThat(created.active()).isTrue();
    }

    @Test
    void createCatcherWithExistingActivePhoneThrowsConflict() {
        var req = new AdminContentController.CatcherRequest(
                "Duplicate Active Catcher", "9000000001", "Bengaluru Urban", 12.97, 77.59, true);

        assertThatThrownBy(() -> controller.createCatcher(req))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("PHONE_TAKEN");
                    assertThat(ex.getMessage()).contains("already registered");
                });
    }

    @Test
    void createCatcherReusingInactivePhoneSucceeds() {
        // Phone of inactive catcher c2 should be reusable for a new active catcher
        var req = new AdminContentController.CatcherRequest(
                "Reused Phone Catcher", "+919000000002", "Mysuru", 12.30, 76.65, true);
        var created = controller.createCatcher(req);

        assertThat(created.id()).isNotNull();
        assertThat(created.phone()).isEqualTo("+919000000002");
        assertThat(created.active()).isTrue();
    }

    @Test
    void updateCatcherKeepingSamePhoneSucceeds() {
        // Updating c1's name without changing its phone number must succeed
        var req = new AdminContentController.CatcherRequest(
                "Updated Active Catcher One", "+919000000001", "Bengaluru Urban", 12.97, 77.59, true);
        var updated = controller.updateCatcher(1L, req);

        assertThat(updated.id()).isEqualTo(1L);
        assertThat(updated.name()).isEqualTo("Updated Active Catcher One");
        assertThat(updated.phone()).isEqualTo("+919000000001");
    }

    @Test
    void updateCatcherToAnotherActiveCatcherPhoneThrowsConflict() {
        // Adding another active catcher c3
        Catcher c3 = new Catcher();
        c3.id = 3L;
        c3.name = "Active Catcher Three";
        c3.phone = "+919000000003";
        c3.district = "Shivamogga";
        c3.lat = 13.92;
        c3.lng = 75.56;
        c3.active = true;
        catchersMap.put(3L, c3);

        // Updating c3 to have c1's phone "+919000000001" must throw 409 PHONE_TAKEN
        var req = new AdminContentController.CatcherRequest(
                "Active Catcher Three Renamed", "9000000001", "Shivamogga", 13.92, 75.56, true);

        assertThatThrownBy(() -> controller.updateCatcher(3L, req))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("PHONE_TAKEN");
                    assertThat(ex.getMessage()).contains("already registered");
                });
    }

    @Test
    void updateCatcherToInactiveCatcherPhoneSucceeds() {
        // Updating c1 to have c2's phone "+919000000002" (which is inactive) should succeed
        var req = new AdminContentController.CatcherRequest(
                "Active Catcher One", "+919000000002", "Bengaluru Urban", 12.97, 77.59, true);
        var updated = controller.updateCatcher(1L, req);

        assertThat(updated.phone()).isEqualTo("+919000000002");
    }

    @Test
    void updateCatcherDeactivatingDoesNotConflict() {
        // Updating c2 (inactive) to another phone while keeping active=false
        var req = new AdminContentController.CatcherRequest(
                "Inactive Catcher Two", "+919000000001", "Mysuru", 12.30, 76.65, false);
        var updated = controller.updateCatcher(2L, req);

        assertThat(updated.active()).isFalse();
    }

    @Test
    void i18nContainsCpLogoutInEnglishAndKannada() throws IOException {
        String content = Files.readString(Path.of("src/main/resources/static/js/i18n.js"));
        assertThat(content).contains("'cp.logout': 'Sign out'");
        assertThat(content).contains("'cp.logout': 'ಸೈನ್ ಔಟ್'");
    }
}
