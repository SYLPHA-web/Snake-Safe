package com.snakesafe.snake;

import java.util.Arrays;
import java.util.List;

public record SnakeView(Long id, String nameEn, String nameKn, String scientificName, boolean venomous,
        String description, String habitat, List<String> districts, String imageUrl) {

    public static SnakeView of(Snake s) {
        List<String> districts = s.districts == null || s.districts.isBlank() ? List.of()
                : Arrays.stream(s.districts.split(",")).map(String::trim).filter(d -> !d.isEmpty()).toList();
        return new SnakeView(s.id, s.nameEn, s.nameKn, s.scientificName, s.venomous, s.description, s.habitat,
                districts, s.imageUrl);
    }
}
