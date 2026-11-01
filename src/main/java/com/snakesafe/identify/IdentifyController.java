package com.snakesafe.identify;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.snakesafe.config.AppProperties;
import com.snakesafe.config.RateLimiter;
import com.snakesafe.config.Sanitizer;
import com.snakesafe.web.ApiException;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class IdentifyController {

    public record PhotoSaved(long id, String species) {
    }

    private final IdentifyService identify;
    private final PhotoService photos;
    private final RateLimiter limiter;
    private final AppProperties props;

    public IdentifyController(IdentifyService identify, PhotoService photos, RateLimiter limiter, AppProperties props) {
        this.identify = identify;
        this.photos = photos;
        this.limiter = limiter;
        this.props = props;
    }

    /** Multipart: file (jpg/png, max 5 MB). Add keep=true only if the user agrees to share the photo for training. */
    @PostMapping(value = "/api/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IdentifyService.Result identify(@RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean keep, HttpServletRequest http) {
        limiter.check("identify:" + http.getRemoteAddr(), props.limits().identifyPerMinute(), Duration.ofMinutes(1));
        return identify.identify(file, keep);
    }

    /** Training data: multipart file + species + source, and consent must be true. */
    @PostMapping(value = "/api/snake-photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public PhotoSaved contribute(@RequestParam("file") MultipartFile file, @RequestParam("species") String species,
            @RequestParam(defaultValue = "user") String source, @RequestParam(defaultValue = "false") boolean consent,
            HttpServletRequest http) {
        if (!consent) {
            throw ApiException.badRequest("CONSENT_REQUIRED", "The photo can only be saved if you agree to share it.");
        }
        String cleanSpecies = Sanitizer.clean(species);
        String cleanSource = Sanitizer.clean(source);
        if (cleanSpecies == null || cleanSpecies.isBlank() || cleanSpecies.length() > 150) {
            throw ApiException.badRequest("VALIDATION_ERROR", "species is required (up to 150 characters).");
        }
        if (cleanSource == null || cleanSource.isBlank() || cleanSource.length() > 50) {
            throw ApiException.badRequest("VALIDATION_ERROR", "source must be 1 to 50 characters.");
        }
        limiter.check("photo:" + http.getRemoteAddr(), 10, Duration.ofMinutes(10));
        SnakePhoto saved = photos.store(ImageFiles.check(file), cleanSpecies, cleanSource, true);
        return new PhotoSaved(saved.id, saved.species);
    }
}
