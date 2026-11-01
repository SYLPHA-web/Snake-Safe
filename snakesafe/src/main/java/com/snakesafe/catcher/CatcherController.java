package com.snakesafe.catcher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.snakesafe.auth.Phones;
import com.snakesafe.config.RateLimiter;
import com.snakesafe.config.Sanitizer;
import com.snakesafe.hospital.Geo;
import com.snakesafe.web.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
public class CatcherController {

    public record CatcherView(Long id, String name, String phone, String district, double lat, double lng,
            Double distanceKm) {
    }

    public record RescueCreate(@NotNull Long catcherId,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
            @Size(max = 500) String notes,
            @NotBlank @Size(max = 20) String phone) {
    }

    public record RescueView(String id, Long catcherId, String catcherName, String catcherPhone, double lat, double lng,
            String notes, RescueStatus status, Instant createdAt, Instant updatedAt, String reminder) {
    }

    private final CatcherRepository catchers;
    private final RescueRepository rescues;
    private final RateLimiter limiter;
    private final Clock clock;

    public CatcherController(CatcherRepository catchers, RescueRepository rescues, RateLimiter limiter, Clock clock) {
        this.catchers = catchers;
        this.rescues = rescues;
        this.limiter = limiter;
        this.clock = clock;
    }

    @GetMapping("/api/catchers")
    public List<CatcherView> list(@RequestParam(required = false) String district,
            @RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng) {
        boolean located = lat != null && lng != null;
        String d = district == null ? "" : district.strip().toLowerCase(Locale.ROOT);
        Comparator<CatcherView> order = located
                ? Comparator.comparing(CatcherView::distanceKm)
                : Comparator.comparing(CatcherView::name);
        return catchers.findByActiveTrue().stream()
                .filter(c -> d.isEmpty() || c.district.toLowerCase(Locale.ROOT).contains(d))
                .map(c -> toView(c, located ? lat : null, located ? lng : null))
                .sorted(order)
                .toList();
    }

    @GetMapping("/api/catchers/{id}")
    public CatcherView get(@PathVariable long id, @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        Catcher c = catchers.findById(id).filter(x -> x.active)
                .orElseThrow(() -> ApiException.notFound("CATCHER_NOT_FOUND", "Snake catcher not found."));
        return toView(c, lat, lng);
    }

    @PostMapping("/api/rescues")
    @ResponseStatus(HttpStatus.CREATED)
    public RescueView request(@Valid @RequestBody RescueCreate req, HttpServletRequest http) {
        limiter.check("rescue:" + http.getRemoteAddr(), 5, Duration.ofMinutes(10));
        Catcher c = catchers.findById(req.catcherId()).filter(x -> x.active)
                .orElseThrow(() -> ApiException.notFound("CATCHER_NOT_FOUND", "Snake catcher not found."));
        Rescue r = new Rescue();
        r.id = UUID.randomUUID().toString();
        r.catcherId = c.id;
        r.lat = req.lat();
        r.lng = req.lng();
        r.notes = Sanitizer.cleanOrNull(req.notes());
        r.phone = Phones.normalize(req.phone());
        r.status = RescueStatus.REQUESTED;
        r.createdAt = clock.instant();
        r.updatedAt = r.createdAt;
        return toView(rescues.save(r), c);
    }

    @GetMapping("/api/rescues/{id}")
    public RescueView rescue(@PathVariable String id) {
        Rescue r = rescues.findById(id)
                .orElseThrow(() -> ApiException.notFound("RESCUE_NOT_FOUND", "Rescue request not found."));
        return toView(r, catchers.findById(r.catcherId).orElse(null));
    }

    public static RescueView toView(Rescue r, Catcher c) {
        return new RescueView(r.id, r.catcherId, c == null ? null : c.name, c == null ? null : c.phone, r.lat, r.lng,
                r.notes, r.status, r.createdAt, r.updatedAt, "Snake catchers do not give medical help. If someone is bitten, call 108.");
    }

    private static CatcherView toView(Catcher c, Double lat, Double lng) {
        Double km = lat != null && lng != null ? Geo.round1(Geo.km(lat, lng, c.lat, c.lng)) : null;
        return new CatcherView(c.id, c.name, c.phone, c.district, c.lat, c.lng, km);
    }
}
