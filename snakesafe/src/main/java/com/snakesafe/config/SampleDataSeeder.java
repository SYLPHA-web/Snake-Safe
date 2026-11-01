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
        if (!props.seedSampleData()) {
            return;
        }
        if (hospitals.count() == 0) {
            seedHospitals();
        }
        if (snakes.count() == 0) {
            seedSnakes();
        }
        if (catchers.count() == 0) {
            seedCatchers();
        }
        if (staff.findByUsernameIgnoreCase(ADMIN_USERNAME).isEmpty()) {
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
            s.passwordHash = encoder.encode(STAFF_PASSWORD);
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

    private void seedSnakes() {
        String western = "Uttara Kannada, Shivamogga, Chikkamagaluru, Kodagu, Udupi, Dakshina Kannada";
        String dry = "Kalaburagi, Vijayapura, Bagalkot, Raichur, Ballari, Koppal, Chitradurga, Tumakuru";
        snake("Indian Cobra", "ನಾಗರಹಾವು", "Naja naja", true,
                "A medium to large snake that spreads a hood when alarmed. Brown, grey or black, sometimes with a spectacle mark on the hood.",
                "Fields, villages, farmland, rocky areas and forest edges. Often near rat holes and water.", "Statewide");
        snake("King Cobra", "ಕಾಳಿಂಗ ಸರ್ಪ", "Ophiophagus hannah", true,
                "The longest venomous snake in the world. Olive, brown or black with pale bands, and a tall hood. Eats other snakes.",
                "Dense evergreen and moist forests of the Western Ghats.", western);
        snake("Common Krait", "ಕಟ್ಟು ಹಾವು", "Bungarus caeruleus", true,
                "A slim, shiny black or blue-black snake with thin white bands. Active at night, often found indoors in villages.",
                "Farmland, village homes, dry fields and near water. Rests in dark corners during the day.", "Statewide");
        snake("Russell's Viper", "ಕೊಳಕು ಮಂಡಲ", "Daboia russelii", true,
                "A heavy-bodied snake with a pattern of large dark rings along a brown back. Hisses loudly when disturbed.",
                "Open grassland, farms, scrub and rocky ground. Often found in crop fields.", "Statewide");
        snake("Saw-scaled Viper", "ಗರಗಸ ಮಂಡಲ", "Echis carinatus", true,
                "A small, thin snake, sandy or brown with a pale zig-zag line. Rubs its scales together with a rasping sound.",
                "Dry, sandy and rocky country. Hides under stones and in loose soil.", dry);
        snake("Malabar Pit Viper", "ಮಲಬಾರ್ ಪಿಟ್ ವೈಪರ್", "Trimeresurus malabaricus", true,
                "A small green, yellow or brown viper with a triangular head. Often rests on low branches.",
                "Forests, coffee and tea plantations and bushes in the Western Ghats.", western);
        snake("Indian Rat Snake", "ಕೇರೆ ಹಾವು", "Ptyas mucosa", false,
                "A long, fast snake, pale brown to black, with dark markings on the back half. Often mistaken for a cobra.",
                "Farms, villages, gardens, trees and rocky places.", "Statewide");
        snake("Indian Rock Python", "ಹೆಬ್ಬಾವು", "Python molurus", false,
                "A very large, heavy, non-venomous snake with brown blotches on a pale body. Squeezes its prey.",
                "Forests, grassland near water, rocky hills and farmland edges.",
                "Uttara Kannada, Shivamogga, Kodagu, Chikkamagaluru, Mysuru, Chamarajanagar");
        snake("Checkered Keelback", "ನೀರು ಹಾವು", "Fowlea piscator", false,
                "A common water snake with a checkered olive and dark pattern. Often seen in ponds and paddy fields.",
                "Ponds, lakes, streams, paddy fields and wet ground.", "Statewide");
        snake("Common Wolf Snake", "ವುಲ್ಫ್ ಹಾವು", "Lycodon aulicus", false,
                "A small, thin brown snake with pale bands that fade with age. Often found in houses at night, and looks like a krait.",
                "Houses, outbuildings, gardens, stone walls and rubbish heaps.", "Statewide");
        snake("Indian Sand Boa", "ಮಣ್ಣುಮುಕ್ಕ ಹಾವು", "Eryx johnii", false,
                "A short, thick snake with a blunt tail that looks like its head. Brown with darker patches. Spends most time under loose soil.",
                "Dry, sandy and rocky ground and farmland.", dry);
        snake("Green Vine Snake", "ಹಸಿರು ಹಾವು", "Ahaetulla nasuta", true,
                "A very thin, bright green snake with a long pointed head. Hard to see in leaves. Has mild venom, but any bite should still be treated as an emergency.",
                "Bushes, hedges, gardens and forest edges.", "Statewide");
    }

    private void snake(String en, String kn, String scientific, boolean venomous, String description, String habitat,
            String districts) {
        Snake s = new Snake();
        s.nameEn = en;
        s.nameKn = kn;
        s.scientificName = scientific;
        s.venomous = venomous;
        s.description = description;
        s.habitat = habitat;
        s.districts = districts;
        s.imageUrl = "/img/snakes/" + en.toLowerCase().replace("'", "").replace(' ', '-') + ".jpg";
        snakes.save(s);
    }
}
