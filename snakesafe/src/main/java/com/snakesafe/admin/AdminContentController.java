package com.snakesafe.admin;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.snakesafe.auth.AuthPrincipal;
import com.snakesafe.auth.Phones;
import com.snakesafe.auth.Role;
import com.snakesafe.catcher.Catcher;
import com.snakesafe.catcher.CatcherRepository;
import com.snakesafe.config.Sanitizer;
import com.snakesafe.identify.SnakePhoto;
import com.snakesafe.identify.SnakePhotoRepository;
import com.snakesafe.snake.Snake;
import com.snakesafe.snake.SnakeRepository;
import com.snakesafe.snake.SnakeView;
import com.snakesafe.staff.StaffAuthService;
import com.snakesafe.web.ApiException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Admin: snakes, snake catchers and the training photos. */
@RestController
@RequestMapping("/api/admin")
public class AdminContentController {

    public record SnakeRequest(@NotBlank @Size(max = 100) String nameEn, @NotBlank @Size(max = 100) String nameKn,
            @NotBlank @Size(max = 100) String scientificName, boolean venomous, @Size(max = 2000) String description,
            @Size(max = 1000) String habitat, @Size(max = 40) List<@Size(max = 60) String> districts,
            @Size(max = 300) String imageUrl) {
    }

    public record CatcherRequest(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 20) String phone,
            @NotBlank @Size(max = 100) String district,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng, Boolean active) {
    }

    public record CatcherAdminView(Long id, String name, String phone, String district, double lat, double lng,
            boolean active) {
    }

    public record VerifyRequest(@Size(max = 150) String species) {
    }

    public record PhotoView(Long id, String storagePath, String species, String verifiedBy, String source,
            boolean consent, java.time.Instant createdAt) {
    }

    private final SnakeRepository snakes;
    private final CatcherRepository catchers;
    private final SnakePhotoRepository photos;
    private final StaffAuthService auth;

    public AdminContentController(SnakeRepository snakes, CatcherRepository catchers, SnakePhotoRepository photos,
            StaffAuthService auth) {
        this.snakes = snakes;
        this.catchers = catchers;
        this.photos = photos;
        this.auth = auth;
    }

    // ---- snakes ----

    @GetMapping("/snakes")
    public List<SnakeView> listSnakes() {
        return snakes.findAll().stream().map(SnakeView::of).toList();
    }

    @PostMapping("/snakes")
    @ResponseStatus(HttpStatus.CREATED)
    public SnakeView createSnake(@Valid @RequestBody SnakeRequest req) {
        return SnakeView.of(snakes.save(fill(new Snake(), req)));
    }

    @PutMapping("/snakes/{id}")
    public SnakeView updateSnake(@PathVariable long id, @Valid @RequestBody SnakeRequest req) {
        Snake s = snakes.findById(id).orElseThrow(() -> ApiException.notFound("SNAKE_NOT_FOUND", "Snake not found."));
        return SnakeView.of(snakes.save(fill(s, req)));
    }

    @DeleteMapping("/snakes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSnake(@PathVariable long id) {
        if (!snakes.existsById(id)) {
            throw ApiException.notFound("SNAKE_NOT_FOUND", "Snake not found.");
        }
        snakes.deleteById(id);
    }

    // ---- catchers ----

    @GetMapping("/catchers")
    public List<CatcherAdminView> listCatchers() {
        return catchers.findAll().stream().map(AdminContentController::toView).toList();
    }

    @PostMapping("/catchers")
    @ResponseStatus(HttpStatus.CREATED)
    public CatcherAdminView createCatcher(@Valid @RequestBody CatcherRequest req) {
        return toView(catchers.save(fill(new Catcher(), req)));
    }

    @PutMapping("/catchers/{id}")
    public CatcherAdminView updateCatcher(@PathVariable long id, @Valid @RequestBody CatcherRequest req) {
        Catcher c = catchers.findById(id)
                .orElseThrow(() -> ApiException.notFound("CATCHER_NOT_FOUND", "Snake catcher not found."));
        return toView(catchers.save(fill(c, req)));
    }

    /** Hidden, not erased, so old rescue requests keep their catcher. */
    @DeleteMapping("/catchers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hideCatcher(@PathVariable long id) {
        Catcher c = catchers.findById(id)
                .orElseThrow(() -> ApiException.notFound("CATCHER_NOT_FOUND", "Snake catcher not found."));
        c.active = false;
        catchers.save(c);
    }

    // ---- training photos ----

    @GetMapping("/snake-photos")
    public List<PhotoView> listPhotos() {
        return photos.findAllByOrderByCreatedAtDesc().stream().map(AdminContentController::toView).toList();
    }

    /** CSV with columns path,label, ready for a training script. Only photos shared with consent are listed. */
    @GetMapping("/snake-photos/export")
    public ResponseEntity<String> exportCsv() {
        StringBuilder csv = new StringBuilder("path,label\n");
        for (SnakePhoto p : photos.findAllByOrderByCreatedAtDesc()) {
            if (p.consent) {
                csv.append(csvCell(p.storagePath)).append(',').append(csvCell(p.species)).append('\n');
            }
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"snake-photos.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString());
    }

    /** An expert confirms the label (optionally correcting the species). */
    @PutMapping("/snake-photos/{id}/verify")
    public PhotoView verifyPhoto(@PathVariable long id, @AuthenticationPrincipal AuthPrincipal me,
            @Valid @RequestBody(required = false) VerifyRequest req) {
        SnakePhoto p = photos.findById(id)
                .orElseThrow(() -> ApiException.notFound("PHOTO_NOT_FOUND", "Photo not found."));
        if (req != null && req.species() != null && !req.species().isBlank()) {
            p.species = Sanitizer.clean(req.species());
        }
        p.verifiedBy = auth.require(me, Role.ADMIN).username;
        return toView(photos.save(p));
    }

    // ---- helpers ----

    private static String csvCell(String value) {
        String v = value == null ? "" : value;
        if (v.startsWith("=") || v.startsWith("+") || v.startsWith("-") || v.startsWith("@")) {
            v = "'" + v;   // stops spreadsheet programs treating the label as a formula
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private static Snake fill(Snake s, SnakeRequest r) {
        s.nameEn = Sanitizer.clean(r.nameEn());
        s.nameKn = Sanitizer.clean(r.nameKn());
        s.scientificName = Sanitizer.clean(r.scientificName());
        s.venomous = r.venomous();
        s.description = Sanitizer.cleanOrNull(r.description());
        s.habitat = Sanitizer.cleanOrNull(r.habitat());
        s.districts = r.districts() == null ? null
                : String.join(", ", r.districts().stream().map(Sanitizer::clean).filter(d -> !d.isEmpty()).toList());
        s.imageUrl = Sanitizer.cleanOrNull(r.imageUrl());
        return s;
    }

    private static Catcher fill(Catcher c, CatcherRequest r) {
        c.name = Sanitizer.clean(r.name());
        c.phone = Phones.normalize(r.phone());
        c.district = Sanitizer.clean(r.district());
        c.lat = r.lat();
        c.lng = r.lng();
        c.active = r.active() == null || r.active();
        return c;
    }

    private static CatcherAdminView toView(Catcher c) {
        return new CatcherAdminView(c.id, c.name, c.phone, c.district, c.lat, c.lng, c.active);
    }

    private static PhotoView toView(SnakePhoto p) {
        return new PhotoView(p.id, p.storagePath, p.species, p.verifiedBy, p.source, p.consent, p.createdAt);
    }
}
