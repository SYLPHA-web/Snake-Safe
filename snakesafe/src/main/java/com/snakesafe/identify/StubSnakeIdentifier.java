package com.snakesafe.identify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.snakesafe.snake.Snake;
import com.snakesafe.snake.SnakeRepository;

import org.springframework.transaction.annotation.Transactional;

/** Demo identifier: picks three species from the same photo every time, always with low confidence. */
@Component
@ConditionalOnProperty(name = "app.identify-mode", havingValue = "STUB", matchIfMissing = true)
public class StubSnakeIdentifier implements SnakeIdentifier {

    private static final double[] CONFIDENCE = { 0.34, 0.22, 0.12 };

    private final SnakeRepository snakes;

    public StubSnakeIdentifier(SnakeRepository snakes) {
        this.snakes = snakes;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RawGuess> identify(byte[] image, String mediaType) {
        List<Snake> all = snakes.findAll();
        if (all.isEmpty()) {
            return List.of();
        }
        int start = Math.floorMod(Arrays.hashCode(image), all.size());
        List<RawGuess> guesses = new ArrayList<>();
        for (int i = 0; i < Math.min(3, all.size()); i++) {
            Snake s = all.get((start + i) % all.size());
            guesses.add(new RawGuess(s.scientificName, s.nameEn, CONFIDENCE[i]));
        }
        return guesses;
    }

    @Override
    public String mode() {
        return "STUB";
    }
}
