package com.snakesafe.snake;

import java.util.List;
import java.util.Locale;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.snakesafe.web.ApiException;

@RestController
public class SnakeController {

    private final SnakeRepository snakes;

    public SnakeController(SnakeRepository snakes) {
        this.snakes = snakes;
    }

    @GetMapping("/api/snakes")
    @Transactional(readOnly = true)
    public List<SnakeView> list(@RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean venomous,
            @RequestParam(required = false) String district) {
        String q = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        String d = district == null ? "" : district.strip().toLowerCase(Locale.ROOT);
        return snakes.findAll().stream()
                .filter(s -> venomous == null || s.venomous == venomous)
                .filter(s -> q.isEmpty() || contains(s.nameEn, q) || contains(s.scientificName, q)
                        || (s.nameKn != null && s.nameKn.contains(search.strip())))
                .filter(s -> d.isEmpty() || contains(s.districts, d) || contains(s.districts, "statewide"))
                .sorted((a, b) -> a.nameEn.compareToIgnoreCase(b.nameEn))
                .map(SnakeView::of)
                .toList();
    }

    @GetMapping("/api/snakes/{id}")
    public SnakeView get(@PathVariable long id) {
        return snakes.findById(id).map(SnakeView::of)
                .orElseThrow(() -> ApiException.notFound("SNAKE_NOT_FOUND", "Snake not found."));
    }

    private static boolean contains(String haystack, String needleLower) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needleLower);
    }
}
