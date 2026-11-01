package com.snakesafe.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.snakesafe.config.AppProperties;

/** The install manifest is built from the app: config, so renaming the app needs no extra file edit. */
@RestController
public class ManifestController {

    private final AppProperties props;

    public ManifestController(AppProperties props) {
        this.props = props;
    }

    @GetMapping(value = "/manifest.webmanifest", produces = "application/manifest+json")
    public ResponseEntity<Map<String, Object>> manifest() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", props.name() + " - snakebite help");
        m.put("short_name", props.name());
        m.put("description", props.tagline());
        m.put("start_url", "/#/");
        m.put("scope", "/");
        m.put("display", "standalone");
        m.put("orientation", "portrait");
        m.put("background_color", "#f8f9ff");
        m.put("theme_color", "#003d1f");
        m.put("lang", props.defaultLanguage());
        m.put("icons", List.of(
                        Map.of("src", "/icons/icon-192.png", "sizes", "192x192", "type", "image/png", "purpose", "any maskable"),
                        Map.of("src", "/icons/icon-512.png", "sizes", "512x512", "type", "image/png", "purpose", "any maskable"),
                        Map.of("src", props.logo(), "sizes", "any", "type", "image/svg+xml", "purpose", "any")));
        m.put("shortcuts", List.of(
                Map.of("name", "Emergency", "url", "/#/emergency"),
                Map.of("name", "First aid", "url", "/#/first-aid"),
                Map.of("name", "Hospitals", "url", "/#/hospitals")));
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).contentType(MediaType.parseMediaType("application/manifest+json")).body(m);
    }
}
