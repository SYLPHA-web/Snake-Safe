package com.snakesafe.admin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snakesafe.config.Sanitizer;
import com.snakesafe.hospital.Hospital;
import com.snakesafe.hospital.HospitalRepository;
import com.snakesafe.hospital.HospitalService;
import com.snakesafe.snake.Snake;
import com.snakesafe.snake.SnakeRepository;
import com.snakesafe.web.ApiException;

/**
 * Loads snakes or hospitals from a CSV file. Column names are matched loosely (case, spaces and underscores are
 * ignored), so common public snake datasets such as the ones on Kaggle can be imported as they are.
 */
@Service
public class CsvImportService {

    public static final int MAX_ROWS = 2000;
    private static final int MAX_ERRORS = 25;

    public record Result(int created, int updated, int skipped, List<String> errors) {
    }

    private static final Set<String> SCI = Set.of("scientificname", "scientific", "species", "binomial", "latinname", "sciname", "speciesname", "taxon");
    private static final Set<String> EN = Set.of("commonname", "common", "name", "englishname", "vernacularname", "english", "snakename");
    private static final Set<String> KN = Set.of("kannadaname", "namekn", "kannada", "namekannada");
    private static final Set<String> VENOM = Set.of("venomous", "venom", "isvenomous", "poisonous", "venomstatus", "toxicity", "danger", "dangerlevel", "venomtype");
    private static final Set<String> DESC = Set.of("description", "notes", "details", "about", "remarks", "summary");
    private static final Set<String> HABITAT = Set.of("habitat", "habitattype");
    private static final Set<String> DISTRICTS = Set.of("districts", "distribution", "range", "region", "location", "locations", "geographicrange");
    private static final Set<String> IMAGE = Set.of("image", "imageurl", "img", "photo", "picture", "imagepath");

    private static final Set<String> NAME = Set.of("name", "hospitalname", "hospital");
    private static final Set<String> DISTRICT = Set.of("district");
    private static final Set<String> ADDRESS = Set.of("address", "location");
    private static final Set<String> PHONE = Set.of("phone", "phonenumber", "contact", "telephone", "mobile");
    private static final Set<String> LAT = Set.of("lat", "latitude");
    private static final Set<String> LNG = Set.of("lng", "lon", "long", "longitude");
    private static final Set<String> VIALS = Set.of("vials", "stock", "antivenomvials", "antivenom", "asvvials");

    private final SnakeRepository snakes;
    private final HospitalRepository hospitals;
    private final HospitalService hospitalService;

    public CsvImportService(SnakeRepository snakes, HospitalRepository hospitals, HospitalService hospitalService) {
        this.snakes = snakes;
        this.hospitals = hospitals;
        this.hospitalService = hospitalService;
    }

    @Transactional
    public Result importSnakes(String csv) {
        Table t = Table.of(csv);
        int created = 0; int updated = 0; int skipped = 0; List<String> errors = new ArrayList<>();
        int iSci = t.col(SCI); int iEn = t.col(EN); int iKn = t.col(KN); int iVenom = t.col(VENOM); int iDesc = t.col(DESC);
        int iHab = t.col(HABITAT); int iDist = t.col(DISTRICTS); int iImg = t.col(IMAGE);
        if (iSci < 0) {
            throw ApiException.badRequest("CSV_COLUMNS", "The file needs a scientific name column (for example scientific_name or species).");
        }
        for (int r = 1; r < t.rows.size(); r++) {
            List<String> row = t.rows.get(r);
            String sci = clip(Sanitizer.clean(t.get(row, iSci)), 100);
            if (sci == null || sci.isBlank()) { skipped++; continue; }
            Boolean venom = iVenom < 0 ? null : parseVenom(t.get(row, iVenom));
            Snake s = snakes.findFirstByScientificNameIgnoreCase(sci).orElse(null);
            boolean isNew = s == null;
            if (isNew) {
                if (venom == null) { skipped++; addError(errors, "Row " + (r + 1) + " (" + sci + "): venom status missing or unclear"); continue; }
                s = new Snake();
                s.scientificName = sci;
            }
            String en = clip(Sanitizer.cleanOrNull(t.get(row, iEn)), 100);
            String kn = clip(Sanitizer.cleanOrNull(t.get(row, iKn)), 100);
            if (en != null) s.nameEn = en; else if (isNew) s.nameEn = sci;
            if (kn != null) s.nameKn = kn; else if (isNew) s.nameKn = s.nameEn;
            if (venom != null) s.venomous = venom;
            String d = clip(Sanitizer.cleanOrNull(t.get(row, iDesc)), 2000); if (d != null) s.description = d;
            String h = clip(Sanitizer.cleanOrNull(t.get(row, iHab)), 1000); if (h != null) s.habitat = h;
            String di = clip(Sanitizer.cleanOrNull(t.get(row, iDist)), 1000); if (di != null) s.districts = di;
            String im = clip(Sanitizer.cleanOrNull(t.get(row, iImg)), 300); if (im != null) s.imageUrl = im;
            snakes.save(s);
            if (isNew) created++; else updated++;
        }
        return new Result(created, updated, skipped, errors);
    }

    @Transactional
    public Result importHospitals(String csv) {
        Table t = Table.of(csv);
        int created = 0; int updated = 0; int skipped = 0; List<String> errors = new ArrayList<>();
        int iName = t.col(NAME); int iDist = t.col(DISTRICT); int iAddr = t.col(ADDRESS); int iPhone = t.col(PHONE);
        int iLat = t.col(LAT); int iLng = t.col(LNG); int iVials = t.col(VIALS);
        if (iName < 0 || iLat < 0 || iLng < 0) {
            throw ApiException.badRequest("CSV_COLUMNS", "The file needs name, lat and lng columns (and district, address, phone).");
        }
        Map<String, Hospital> byName = new HashMap<>();
        for (Hospital h : hospitals.findAll()) byName.put(h.name.toLowerCase(Locale.ROOT), h);
        for (int r = 1; r < t.rows.size(); r++) {
            List<String> row = t.rows.get(r);
            String name = clip(Sanitizer.clean(t.get(row, iName)), 150);
            if (name == null || name.isBlank()) { skipped++; continue; }
            Double lat = parseDouble(t.get(row, iLat)); Double lng = parseDouble(t.get(row, iLng));
            if (lat == null || lng == null || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
                skipped++; addError(errors, "Row " + (r + 1) + " (" + name + "): latitude or longitude is not valid"); continue;
            }
            Hospital h = byName.get(name.toLowerCase(Locale.ROOT));
            boolean isNew = h == null;
            if (isNew) {
                String phone = clip(Sanitizer.cleanOrNull(t.get(row, iPhone)), 20);
                String district = clip(Sanitizer.cleanOrNull(t.get(row, iDist)), 100);
                String address = clip(Sanitizer.cleanOrNull(t.get(row, iAddr)), 300);
                if (phone == null || district == null || address == null) {
                    skipped++; addError(errors, "Row " + (r + 1) + " (" + name + "): district, address and phone are needed for a new hospital"); continue;
                }
                h = new Hospital(); h.name = name; h.phone = phone; h.district = district; h.address = address;
            } else {
                String phone = clip(Sanitizer.cleanOrNull(t.get(row, iPhone)), 20); if (phone != null) h.phone = phone;
                String district = clip(Sanitizer.cleanOrNull(t.get(row, iDist)), 100); if (district != null) h.district = district;
                String address = clip(Sanitizer.cleanOrNull(t.get(row, iAddr)), 300); if (address != null) h.address = address;
            }
            h.lat = lat; h.lng = lng; h.active = true;
            h = hospitals.save(h);
            byName.put(name.toLowerCase(Locale.ROOT), h);
            Double vials = iVials < 0 ? null : parseDouble(t.get(row, iVials));
            if (vials != null && vials >= 0 && vials <= 10000) hospitalService.updateStock(h.id, (int) Math.round(vials));
            if (isNew) created++; else updated++;
        }
        return new Result(created, updated, skipped, errors);
    }

    // ---- helpers ----

    /** true = venomous (any degree), false = not venomous, null = cannot tell. */
    static Boolean parseVenom(String raw) {
        if (raw == null) return null;
        String v = raw.strip().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) return null;
        if (v.equals("0") || v.equals("no") || v.equals("false") || v.equals("n") || v.contains("non") || v.contains("harmless") || v.startsWith("not ") || v.contains("no venom")) return false;
        if (v.equals("1") || v.equals("yes") || v.equals("true") || v.equals("y") || v.contains("venom") || v.contains("poison") || v.contains("mild") || v.contains("high") || v.contains("danger") || v.contains("deadly") || v.contains("lethal") || v.contains("toxic")) return true;
        return null;
    }

    private static Double parseDouble(String s) {
        try { return s == null || s.isBlank() ? null : Double.valueOf(s.strip()); } catch (NumberFormatException e) { return null; }
    }

    private static String clip(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }

    private static void addError(List<String> errors, String msg) {
        if (errors.size() < MAX_ERRORS) errors.add(msg);
    }

    private static final class Table {
        final List<List<String>> rows;
        final List<String> keys;

        private Table(List<List<String>> rows) {
            this.rows = rows;
            this.keys = rows.get(0).stream().map(h -> h.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "")).toList();
        }

        static Table of(String csv) {
            List<List<String>> rows = Csv.parse(csv);
            if (rows.size() < 2) throw ApiException.badRequest("CSV_EMPTY", "The file has no data rows.");
            if (rows.size() - 1 > MAX_ROWS) throw ApiException.badRequest("CSV_TOO_BIG", "Please import at most " + MAX_ROWS + " rows at a time.");
            return new Table(rows);
        }

        int col(Set<String> names) {
            for (int i = 0; i < keys.size(); i++) if (names.contains(keys.get(i))) return i;
            return -1;
        }

        String get(List<String> row, int i) {
            return i < 0 || i >= row.size() ? null : row.get(i);
        }
    }
}
