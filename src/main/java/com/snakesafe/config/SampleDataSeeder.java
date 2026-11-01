package com.snakesafe.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.snakesafe.auth.Role;
import com.snakesafe.catcher.Catcher;
import com.snakesafe.catcher.CatcherRepository;
import com.snakesafe.hospital.AntivenomStock;
import com.snakesafe.hospital.Hospital;
import com.snakesafe.hospital.HospitalRepository;
import com.snakesafe.hospital.StockRepository;
import com.snakesafe.snake.Snake;
import com.snakesafe.snake.SnakeCatalog;
import com.snakesafe.snake.SnakeRepository;
import com.snakesafe.staff.Staff;
import com.snakesafe.staff.StaffRepository;

/**
 * Fills an empty database with clearly-labelled SAMPLE data (hospitals, stock, staff, admin, snakes, catchers).
 * Controlled by app.seed-sample-data. Each table is only filled when it is empty, so restarts add nothing twice.
 */
@Component
public class SampleDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);

    public static final String STAFF_PASSWORD = "staff123";
    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "admin123";

    private record H(String city, String district, double lat, double lng, int vials, Duration age) {
    }

    private static final List<H> HOSPITALS = List.of(
            new H("Bengaluru", "Bengaluru Urban", 12.9716, 77.5946, 12, Duration.ofMinutes(15)),
            new H("Mysuru", "Mysuru", 12.2958, 76.6394, 8, Duration.ofMinutes(45)),
            new H("Mangaluru", "Dakshina Kannada", 12.9141, 74.8560, 5, Duration.ofHours(5)),
            new H("Hubballi", "Dharwad", 15.3647, 75.1240, 10, Duration.ofMinutes(80)),
            new H("Belagavi", "Belagavi", 15.8497, 74.4977, 0, Duration.ofMinutes(20)),
            new H("Kalaburagi", "Kalaburagi", 17.3297, 76.8343, 6, Duration.ofHours(26)),
            new H("Shivamogga", "Shivamogga", 13.9299, 75.5681, 4, Duration.ofDays(3)),
            new H("Davanagere", "Davanagere", 14.4644, 75.9218, 7, Duration.ofMinutes(100)));

    private final AppProperties props;
    private final Clock clock;
    private final HospitalRepository hospitals;
    private final StockRepository stocks;
    private final StaffRepository staff;
    private final SnakeRepository snakes;
    private final CatcherRepository catchers;
    private final PasswordEncoder encoder;

    public SampleDataSeeder(AppProperties props, Clock clock, HospitalRepository hospitals, StockRepository stocks,
            StaffRepository staff, SnakeRepository snakes, CatcherRepository catchers, PasswordEncoder encoder) {
        this.props = props;
        this.clock = clock;
        this.hospitals = hospitals;
        this.stocks = stocks;
        this.staff = staff;
        this.snakes = snakes;
        this.catchers = catchers;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (props.seedReferenceData()) {
            seedSnakes();
        }
        boolean ownAdmin = bootstrapAdmin();
        if (!props.seedSampleData()) {
            return;
        }
        if (hospitals.count() == 0) {
            seedHospitals();
        }
        if (catchers.count() == 0) {
            seedCatchers();
        }
        if (!ownAdmin && staff.findByUsernameIgnoreCase(ADMIN_USERNAME).isEmpty()) {
            Staff admin = new Staff();
            admin.username = ADMIN_USERNAME;
            admin.passwordHash = encoder.encode(ADMIN_PASSWORD);
            admin.name = "Demo Admin";
            admin.role = Role.ADMIN;
            staff.save(admin);
        }
        log.info("Sample data is ready (demo logins are listed in the README)");
    }

    private void seedHospitals() {
        Instant now = clock.instant();
        for (H h : HOSPITALS) {
            Hospital hospital = new Hospital();
            hospital.name = "Sample Hospital " + h.city();
            hospital.district = h.district();
            hospital.address = "Sample address, " + h.city() + ", Karnataka";
            hospital.phone = "080-5550-01" + String.format("%02d", HOSPITALS.indexOf(h) + 1);
            hospital.lat = h.lat();
            hospital.lng = h.lng();
            hospitals.save(hospital);

            AntivenomStock stock = new AntivenomStock();
            stock.hospitalId = hospital.id;
            stock.vials = h.vials();
            stock.updatedAt = now.minus(h.age());
            stocks.save(stock);

            Staff s = new Staff();
            s.username = "staff-" + h.city().toLowerCase();
            s.passwordHash = encoder.encode(props.seedStaffPassword());
            s.name = "Sample Staff " + h.city();
            s.role = Role.STAFF;
            s.hospitalId = hospital.id;
            staff.save(s);
        }
    }

    private void seedCatchers() {
        record C(String name, String phone, String district, double lat, double lng) {
        }
        List<C> sample = List.of(
                new C("Sample Catcher Ravi", "+919000000001", "Bengaluru Urban", 12.9352, 77.6245),
                new C("Sample Catcher Manju", "+919000000002", "Mysuru", 12.3051, 76.6552),
                new C("Sample Catcher Ismail", "+919000000003", "Dakshina Kannada", 12.8700, 74.8800),
                new C("Sample Catcher Suresh", "+919000000004", "Shivamogga", 13.9320, 75.5700),
                new C("Sample Catcher Basavaraj", "+919000000005", "Dharwad", 15.4589, 75.0078),
                new C("Sample Catcher Anand", "+919000000006", "Belagavi", 15.8600, 74.5100));
        for (C c : sample) {
            Catcher entity = new Catcher();
            entity.name = c.name();
            entity.phone = c.phone();
            entity.district = c.district();
            entity.lat = c.lat();
            entity.lng = c.lng();
            catchers.save(entity);
        }
    }

    /** Adds any species from the catalog that is not in the table yet. Never changes rows that already exist. */
    private void seedSnakes() {
        int added = 0;
        for (SnakeCatalog.Entry e : SnakeCatalog.ALL) {
            if (snakes.findFirstByScientificNameIgnoreCase(e.scientificName()).isPresent()) {
                continue;
            }
            Snake s = new Snake();
            s.nameEn = e.nameEn();
            s.nameKn = e.nameKn();
            s.scientificName = e.scientificName();
            s.venomous = e.venomous();
            s.description = e.description();
            s.habitat = e.habitat();
            s.districts = e.districts();
            s.imageUrl = "/img/snakes/" + e.nameEn().toLowerCase().replace("'", "").replace(' ', '-') + ".jpg";
            snakes.save(s);
            added++;
        }
        if (added > 0) {
            log.info("Added {} snake species from the reference list", added);
        }
    }

    /** First admin from ADMIN_USERNAME / ADMIN_PASSWORD (at least 10 characters). Created once, never overwritten. */
    private boolean bootstrapAdmin() {
        String user = props.adminUsername();
        String pass = props.adminPassword();
        if (user == null || user.isBlank()) {
            return false;
        }
        if (pass == null || pass.length() < 10) {
            log.warn("ADMIN_USERNAME is set but ADMIN_PASSWORD is missing or shorter than 10 characters: no admin was created");
            return false;
        }
        if (staff.findByUsernameIgnoreCase(user.strip()).isEmpty()) {
            Staff admin = new Staff();
            admin.username = user.strip();
            admin.passwordHash = encoder.encode(pass);
            admin.name = "Administrator";
            admin.role = Role.ADMIN;
            staff.save(admin);
            log.info("Created admin account '{}'", admin.username);
        }
        return true;
    }
}
