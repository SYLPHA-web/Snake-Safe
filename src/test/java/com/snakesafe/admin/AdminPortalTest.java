package com.snakesafe.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.snakesafe.alert.Alert;
import com.snakesafe.alert.AlertRepository;
import com.snakesafe.alert.AlertStatus;
import com.snakesafe.auth.JwtService;
import com.snakesafe.auth.Role;
import com.snakesafe.catcher.Catcher;
import com.snakesafe.catcher.CatcherRepository;
import com.snakesafe.catcher.Rescue;
import com.snakesafe.catcher.RescueRepository;
import com.snakesafe.catcher.RescueStatus;
import com.snakesafe.config.AppProperties;
import com.snakesafe.hospital.AntivenomStock;
import com.snakesafe.hospital.Hospital;
import com.snakesafe.hospital.HospitalRepository;
import com.snakesafe.hospital.HospitalService;
import com.snakesafe.hospital.StockRepository;
import com.snakesafe.identify.SnakePhoto;
import com.snakesafe.identify.SnakePhotoRepository;
import com.snakesafe.staff.Staff;
import com.snakesafe.staff.StaffAuthService;
import com.snakesafe.staff.StaffRepository;

class AdminPortalTest {

    private Map<Long, Hospital> hospitalsMap;
    private Map<Long, AntivenomStock> stocksMap;
    private Map<Long, Staff> staffMap;
    private Map<String, Alert> alertsMap;
    private Map<String, Rescue> rescuesMap;
    private Map<Long, Catcher> catchersMap;
    private List<SnakePhoto> photosList;

    private HospitalRepository hospitalRepo;
    private StockRepository stockRepo;
    private StaffRepository staffRepo;
    private AlertRepository alertRepo;
    private RescueRepository rescueRepo;
    private CatcherRepository catcherRepo;
    private SnakePhotoRepository photoRepo;

    private HospitalService hospitalService;
    private AdminController controller;
    private Clock clock;
    private static final Instant NOW = Instant.parse("2026-04-01T12:00:00Z");

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        hospitalsMap = new ConcurrentHashMap<>();
        stocksMap = new ConcurrentHashMap<>();
        staffMap = new ConcurrentHashMap<>();
        alertsMap = new ConcurrentHashMap<>();
        rescuesMap = new ConcurrentHashMap<>();
        catchersMap = new ConcurrentHashMap<>();
        photosList = new ArrayList<>();

        clock = Clock.fixed(NOW, ZoneOffset.UTC);

        hospitalRepo = (HospitalRepository) Proxy.newProxyInstance(
                HospitalRepository.class.getClassLoader(),
                new Class<?>[] { HospitalRepository.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("count".equals(name)) {
                        return (long) hospitalsMap.size();
                    }
                    if ("countByActiveTrue".equals(name)) {
                        return hospitalsMap.values().stream().filter(h -> h.active).count();
                    }
                    if ("findAll".equals(name)) {
                        return new ArrayList<>(hospitalsMap.values());
                    }
                    if ("findById".equals(name)) {
                        return Optional.ofNullable(hospitalsMap.get(args[0]));
                    }
                    if ("existsById".equals(name)) {
                        return hospitalsMap.containsKey(args[0]);
                    }
                    if ("save".equals(name)) {
                        Hospital h = (Hospital) args[0];
                        if (h.id == null) {
                            h.id = (long) (hospitalsMap.size() + 1);
                        }
                        hospitalsMap.put(h.id, h);
                        return h;
                    }
                    return null;
                });

        stockRepo = (StockRepository) Proxy.newProxyInstance(
                StockRepository.class.getClassLoader(),
                new Class<?>[] { StockRepository.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findByHospitalId".equals(name)) {
                        return Optional.ofNullable(stocksMap.get(args[0]));
                    }
                    if ("findAll".equals(name)) {
                        return new ArrayList<>(stocksMap.values());
                    }
                    if ("findByHospitalIdIn".equals(name)) {
                        List<Long> ids = (List<Long>) args[0];
                        return stocksMap.values().stream().filter(s -> ids.contains(s.hospitalId)).toList();
                    }
                    if ("save".equals(name)) {
                        AntivenomStock s = (AntivenomStock) args[0];
                        stocksMap.put(s.hospitalId, s);
                        return s;
                    }
                    return null;
                });

        staffRepo = (StaffRepository) Proxy.newProxyInstance(
                StaffRepository.class.getClassLoader(),
                new Class<?>[] { StaffRepository.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findByRoleOrderByUsernameAsc".equals(name)) {
                        Role role = (Role) args[0];
                        return staffMap.values().stream()
                                .filter(s -> s.role == role)
                                .sorted(Comparator.comparing(s -> s.username))
                                .toList();
                    }
                    if ("findAllByOrderByUsernameAsc".equals(name)) {
                        return staffMap.values().stream()
                                .sorted(Comparator.comparing(s -> s.username))
                                .toList();
                    }
                    if ("findById".equals(name)) {
                        return Optional.ofNullable(staffMap.get(args[0]));
                    }
                    if ("existsById".equals(name)) {
                        return staffMap.containsKey(args[0]);
                    }
                    if ("save".equals(name)) {
                        Staff s = (Staff) args[0];
                        if (s.id == null) {
                            s.id = (long) (staffMap.size() + 1);
                        }
                        staffMap.put(s.id, s);
                        return s;
                    }
                    return null;
                });

        alertRepo = (AlertRepository) Proxy.newProxyInstance(
                AlertRepository.class.getClassLoader(),
                new Class<?>[] { AlertRepository.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("countByStatus".equals(name)) {
                        AlertStatus st = (AlertStatus) args[0];
                        return alertsMap.values().stream().filter(a -> a.status == st).count();
                    }
                    if ("findAll".equals(name)) {
                        return new ArrayList<>(alertsMap.values());
                    }
                    return null;
                });

        rescueRepo = (RescueRepository) Proxy.newProxyInstance(
                RescueRepository.class.getClassLoader(),
                new Class<?>[] { RescueRepository.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("countByStatusIn".equals(name)) {
                        List<RescueStatus> list = (List<RescueStatus>) args[0];
                        return rescuesMap.values().stream().filter(r -> list.contains(r.status)).count();
                    }
                    if ("findAll".equals(name)) {
                        return new ArrayList<>(rescuesMap.values());
                    }
                    if ("findById".equals(name)) {
                        return Optional.ofNullable(rescuesMap.get(args[0]));
                    }
                    if ("save".equals(name)) {
                        Rescue r = (Rescue) args[0];
                        rescuesMap.put(r.id, r);
                        return r;
                    }
                    return null;
                });

        catcherRepo = (CatcherRepository) Proxy.newProxyInstance(
                CatcherRepository.class.getClassLoader(),
                new Class<?>[] { CatcherRepository.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("countByActiveTrue".equals(name)) {
                        return catchersMap.values().stream().filter(c -> c.active).count();
                    }
                    if ("findAll".equals(name)) {
                        return new ArrayList<>(catchersMap.values());
                    }
                    if ("findById".equals(name)) {
                        return Optional.ofNullable(catchersMap.get(args[0]));
                    }
                    return null;
                });

        photoRepo = (SnakePhotoRepository) Proxy.newProxyInstance(
                SnakePhotoRepository.class.getClassLoader(),
                new Class<?>[] { SnakePhotoRepository.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("countByVerifiedByIsNull".equals(name)) {
                        return photosList.stream().filter(p -> p.verifiedBy == null).count();
                    }
                    if ("findAll".equals(name)) {
                        return new ArrayList<>(photosList);
                    }
                    return null;
                });

        AppProperties props = new AppProperties(
                "SnakeSafe", "/img/logo.svg", "Tagline", "108", "1800-116-117", 24, "en", "DEMO", "STUB", "DEMO",
                5, 2, false, false, "Asia/Kolkata", "LOCAL", "target/test-photos", false,
                "01234567890123456789012345678901", 24,
                null, null, null, false, false, "admin", "admin123", "staff123");

        hospitalService = new HospitalService(hospitalRepo, stockRepo, props, clock);

        controller = new AdminController(
                null, new JwtService(props, clock), hospitalRepo, hospitalService, stockRepo, staffRepo,
                alertRepo, rescueRepo, catcherRepo, photoRepo, new BCryptPasswordEncoder(), clock);
    }

    @Test
    void testStatsCalculation() {
        // Hospitals: 1 active, 1 deactivated
        Hospital h1 = new Hospital();
        h1.id = 1L;
        h1.name = "Active Hospital";
        h1.active = true;
        hospitalsMap.put(h1.id, h1);

        Hospital h2 = new Hospital();
        h2.id = 2L;
        h2.name = "Inactive Hospital";
        h2.active = false;
        hospitalsMap.put(h2.id, h2);

        // Stocks: h1 has 20 verified vials, h2 has 50 vials (but h2 is inactive)
        AntivenomStock s1 = new AntivenomStock();
        s1.hospitalId = 1L;
        s1.vials = 20;
        s1.updatedAt = NOW.minusSeconds(3600); // 1 hr ago, verified
        stocksMap.put(1L, s1);

        AntivenomStock s2 = new AntivenomStock();
        s2.hospitalId = 2L;
        s2.vials = 50;
        s2.updatedAt = NOW.minusSeconds(3600);
        stocksMap.put(2L, s2);

        // Alerts: 2 waiting, 1 arrived
        Alert a1 = new Alert();
        a1.id = "a1";
        a1.status = AlertStatus.WAITING;
        alertsMap.put(a1.id, a1);

        Alert a2 = new Alert();
        a2.id = "a2";
        a2.status = AlertStatus.WAITING;
        alertsMap.put(a2.id, a2);

        Alert a3 = new Alert();
        a3.id = "a3";
        a3.status = AlertStatus.ARRIVED;
        alertsMap.put(a3.id, a3);

        // Rescues: 1 requested, 1 accepted, 1 done, 1 cancelled
        Rescue r1 = new Rescue();
        r1.id = "r1";
        r1.status = RescueStatus.REQUESTED;
        rescuesMap.put(r1.id, r1);

        Rescue r2 = new Rescue();
        r2.id = "r2";
        r2.status = RescueStatus.ACCEPTED;
        rescuesMap.put(r2.id, r2);

        Rescue r3 = new Rescue();
        r3.id = "r3";
        r3.status = RescueStatus.DONE;
        rescuesMap.put(r3.id, r3);

        Rescue r4 = new Rescue();
        r4.id = "r4";
        r4.status = RescueStatus.CANCELLED;
        rescuesMap.put(r4.id, r4);

        // Catchers: 2 active, 1 inactive
        Catcher c1 = new Catcher();
        c1.id = 1L;
        c1.active = true;
        catchersMap.put(c1.id, c1);

        Catcher c2 = new Catcher();
        c2.id = 2L;
        c2.active = true;
        catchersMap.put(c2.id, c2);

        Catcher c3 = new Catcher();
        c3.id = 3L;
        c3.active = false;
        catchersMap.put(c3.id, c3);

        // Photos: 2 unverified (verifiedBy == null), 1 verified
        SnakePhoto p1 = new SnakePhoto();
        p1.id = 1L;
        p1.verifiedBy = null;
        photosList.add(p1);

        SnakePhoto p2 = new SnakePhoto();
        p2.id = 2L;
        p2.verifiedBy = null;
        photosList.add(p2);

        SnakePhoto p3 = new SnakePhoto();
        p3.id = 3L;
        p3.verifiedBy = "admin";
        photosList.add(p3);

        AdminController.AdminStatsView stats = controller.stats();

        assertThat(stats.totalHospitals()).isEqualTo(2);
        assertThat(stats.activeHospitals()).isEqualTo(1);
        assertThat(stats.verifiedVials()).isEqualTo(20); // only active hospital's verified stock
        assertThat(stats.waitingAlerts()).isEqualTo(2);
        assertThat(stats.activeRescues()).isEqualTo(2); // REQUESTED + ACCEPTED
        assertThat(stats.activeCatchers()).isEqualTo(2);
        assertThat(stats.unverifiedPhotos()).isEqualTo(2);
    }

    @Test
    void testHospitalActiveStatusIncludedInList() {
        Hospital h1 = new Hospital();
        h1.id = 1L;
        h1.name = "Active Hospital";
        h1.district = "Bengaluru";
        h1.address = "MG Road";
        h1.phone = "9876543210";
        h1.lat = 12.97;
        h1.lng = 77.59;
        h1.active = true;
        hospitalsMap.put(h1.id, h1);

        Hospital h2 = new Hospital();
        h2.id = 2L;
        h2.name = "Inactive Hospital";
        h2.district = "Mysuru";
        h2.address = "Sayyaji Rao Rd";
        h2.phone = "9876543211";
        h2.lat = 12.30;
        h2.lng = 76.65;
        h2.active = false;
        hospitalsMap.put(h2.id, h2);

        List<AdminController.HospitalAdminView> list = controller.listHospitals();
        assertThat(list).hasSize(2);

        AdminController.HospitalAdminView view1 = list.stream().filter(h -> h.id().equals(1L)).findFirst().orElseThrow();
        assertThat(view1.active()).isTrue();

        AdminController.HospitalAdminView view2 = list.stream().filter(h -> h.id().equals(2L)).findFirst().orElseThrow();
        assertThat(view2.active()).isFalse();
    }

    @Test
    void testEditingDeactivatedHospitalPreservesInactiveState() {
        Hospital h = new Hospital();
        h.id = 10L;
        h.name = "Old Hospital";
        h.district = "Mandya";
        h.address = "Main Rd";
        h.phone = "9876543210";
        h.lat = 12.5;
        h.lng = 76.8;
        h.active = false; // currently deactivated
        hospitalsMap.put(h.id, h);

        // Edit without specifying active (active == null)
        AdminController.HospitalRequest reqNoActive = new AdminController.HospitalRequest(
                "Updated Name", "Mandya", "New Address", "9876543210", 12.5, 76.8, 5, null);
        AdminController.HospitalAdminView updated = controller.updateHospital(10L, reqNoActive);

        // Must still be inactive!
        assertThat(updated.active()).isFalse();
        assertThat(hospitalsMap.get(10L).active).isFalse();

        // Edit with active = true
        AdminController.HospitalRequest reqReactivate = new AdminController.HospitalRequest(
                "Updated Name", "Mandya", "New Address", "9876543210", 12.5, 76.8, 5, true);
        AdminController.HospitalAdminView reactivated = controller.updateHospital(10L, reqReactivate);

        assertThat(reactivated.active()).isTrue();
        assertThat(hospitalsMap.get(10L).active).isTrue();
    }

    @Test
    void testStaffManagementExcludesAdminAccounts() {
        Staff s1 = new Staff();
        s1.id = 1L;
        s1.username = "nurse_mary";
        s1.name = "Mary";
        s1.role = Role.STAFF;
        s1.hospitalId = 1L;
        s1.active = true;
        staffMap.put(s1.id, s1);

        Staff s2 = new Staff();
        s2.id = 2L;
        s2.username = "admin_super";
        s2.name = "Super Admin";
        s2.role = Role.ADMIN;
        s2.hospitalId = null;
        s2.active = true;
        staffMap.put(s2.id, s2);

        Staff s3 = new Staff();
        s3.id = 3L;
        s3.username = "doctor_john";
        s3.name = "John";
        s3.role = Role.STAFF;
        s3.hospitalId = 1L;
        s3.active = true;
        staffMap.put(s3.id, s3);

        List<AdminController.StaffView> staffList = controller.listStaff();

        assertThat(staffList).hasSize(2);
        assertThat(staffList.stream().map(AdminController.StaffView::username))
                .containsExactly("doctor_john", "nurse_mary")
                .doesNotContain("admin_super");
    }
}
