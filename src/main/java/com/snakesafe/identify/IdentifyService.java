package com.snakesafe.identify;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.snakesafe.config.AppProperties;
import com.snakesafe.snake.Snake;
import com.snakesafe.snake.SnakeRepository;

@Service
public class IdentifyService {

    public static final String DISCLAIMER = "This may be wrong. Treat every bite as an emergency.";

    public enum Venomous { YES, NO, UNSURE }

    public record Guess(String scientificName, String nameEn, String nameKn, double confidence, Venomous venomous,
            Long snakeId) {
    }

    public record Result(List<Guess> guesses, Venomous venomous, String disclaimer, String callToAction,
            String emergencyNumber, String mode, boolean photoKept) {
    }

    private static final double SURE_ABOVE = 0.5;
    private static final double WORRY_ABOVE = 0.2;

    private final SnakeIdentifier identifier;
    private final SnakeRepository snakes;
    private final PhotoService photos;
    private final AppProperties props;

    public IdentifyService(SnakeIdentifier identifier, SnakeRepository snakes, PhotoService photos, AppProperties props) {
        this.identifier = identifier;
        this.snakes = snakes;
        this.photos = photos;
        this.props = props;
    }

    /** The photo is analysed in memory and dropped, unless the user opted in to keep it for training. */
    public Result identify(MultipartFile file, boolean keep) {
        ImageFiles.Checked image = ImageFiles.check(file);
        List<Guess> guesses = new ArrayList<>();
        for (SnakeIdentifier.RawGuess raw : identifier.identify(image.bytes(), image.mediaType())) {
            guesses.add(match(raw));
            if (guesses.size() == 3) {
                break;
            }
        }
        boolean kept = false;
        if (keep && !guesses.isEmpty()) {
            Guess top = guesses.get(0);
            photos.store(image, top.nameEn() != null ? top.nameEn() : top.scientificName(), "identify", true);
            kept = true;
        }
        return new Result(guesses, overall(guesses), DISCLAIMER, "Call " + props.emergencyNumber(),
                props.emergencyNumber(), identifier.mode(), kept);
    }

    /** Venomous status comes from our own snake list, never from what the AI says. */
    private Guess match(SnakeIdentifier.RawGuess raw) {
        double confidence = Math.round(Math.max(0, Math.min(1, raw.confidence())) * 100) / 100.0;
        Snake s = raw.scientificName() == null ? null
                : snakes.findFirstByScientificNameIgnoreCase(raw.scientificName().strip()).orElse(null);
        if (s == null) {
            String name = raw.commonName() == null || raw.commonName().isBlank() ? raw.scientificName() : raw.commonName();
            return new Guess(raw.scientificName(), name, null, confidence, Venomous.UNSURE, null);
        }
        return new Guess(s.scientificName, s.nameEn, s.nameKn, confidence, s.venomous ? Venomous.YES : Venomous.NO, s.id);
    }

    /** Cautious: only "no" when the top guess is confident and no venomous species is a real possibility. */
    static Venomous overall(List<Guess> guesses) {
        if (guesses.isEmpty()) {
            return Venomous.UNSURE;
        }
        Guess top = guesses.get(0);
        if (top.confidence() < SURE_ABOVE || top.venomous() == Venomous.UNSURE) {
            return Venomous.UNSURE;
        }
        boolean otherWorry = guesses.stream().skip(1)
                .anyMatch(g -> g.venomous() != Venomous.NO && g.confidence() >= WORRY_ABOVE);
        if (top.venomous() == Venomous.YES) {
            return Venomous.YES;
        }
        return otherWorry ? Venomous.UNSURE : Venomous.NO;
    }

    static String newFileName(String extension) {
        return UUID.randomUUID() + "." + extension;
    }
}
