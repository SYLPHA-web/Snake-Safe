package com.snakesafe.identify;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.snakesafe.config.AppProperties;
import com.snakesafe.identify.IdentifyService.Guess;
import com.snakesafe.identify.IdentifyService.Venomous;
import com.snakesafe.snake.Snake;
import com.snakesafe.snake.SnakeRepository;

class IdentifyServiceTest {

    private static final byte[] VALID_JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0 };

    private Map<String, Snake> snakeDb;
    private SnakeRepository snakeRepo;
    private List<SnakeIdentifier.RawGuess> fakeGuesses;
    private SnakeIdentifier identifier;
    private AtomicBoolean photoStored;
    private AtomicBoolean storageFails;
    private List<SnakePhoto> savedPhotos;
    private PhotoService photoService;
    private AppProperties props;
    private IdentifyService service;

    @BeforeEach
    void setUp() {
        snakeDb = new HashMap<>();
        fakeGuesses = new ArrayList<>();
        photoStored = new AtomicBoolean(false);
        storageFails = new AtomicBoolean(false);
        savedPhotos = new ArrayList<>();

        // Prepopulate snake DB with Cobra (venomous) and Rat Snake (non-venomous)
        Snake cobra = new Snake();
        cobra.id = 1L;
        cobra.scientificName = "Naja naja";
        cobra.nameEn = "Spectacled Cobra";
        cobra.nameKn = "ನಾಗರಹಾವು";
        cobra.venomous = true;
        snakeDb.put(cobra.scientificName.toLowerCase(), cobra);

        Snake ratSnake = new Snake();
        ratSnake.id = 2L;
        ratSnake.scientificName = "Ptyas mucosa";
        ratSnake.nameEn = "Indian Rat Snake";
        ratSnake.nameKn = "ಕರೆಹಾವು";
        ratSnake.venomous = false;
        snakeDb.put(ratSnake.scientificName.toLowerCase(), ratSnake);

        Snake viper = new Snake();
        viper.id = 3L;
        viper.scientificName = "Daboia russelii";
        viper.nameEn = "Russell's Viper";
        viper.nameKn = "ಕೊಳಕು ಮಂಡಲ";
        viper.venomous = true;
        snakeDb.put(viper.scientificName.toLowerCase(), viper);

        snakeRepo = (SnakeRepository) Proxy.newProxyInstance(
                SnakeRepository.class.getClassLoader(),
                new Class<?>[] { SnakeRepository.class },
                (proxy, method, args) -> {
                    if ("findFirstByScientificNameIgnoreCase".equals(method.getName())) {
                        String name = ((String) args[0]).toLowerCase();
                        return Optional.ofNullable(snakeDb.get(name));
                    }
                    return null;
                });

        identifier = new SnakeIdentifier() {
            @Override
            public List<RawGuess> identify(byte[] image, String mediaType) {
                return fakeGuesses;
            }

            @Override
            public String mode() {
                return "TEST_STUB";
            }
        };

        // PhotoStorage proxy to record storage calls
        PhotoStorage storage = (fileName, data, contentType) -> {
            if (storageFails.get()) {
                throw new com.snakesafe.web.ApiException(
                        org.springframework.http.HttpStatus.BAD_GATEWAY, "STORAGE_ERROR", "Could not save the photo.");
            }
            photoStored.set(true);
            return fileName;
        };

        SnakePhotoRepository photoRepo = (SnakePhotoRepository) Proxy.newProxyInstance(
                SnakePhotoRepository.class.getClassLoader(),
                new Class<?>[] { SnakePhotoRepository.class },
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) {
                        SnakePhoto p = (SnakePhoto) args[0];
                        p.id = 999L + savedPhotos.size();
                        savedPhotos.add(p);
                        return p;
                    }
                    return null;
                });

        photoService = new PhotoService(storage, photoRepo, java.time.Clock.systemUTC());

        props = new AppProperties(
                "SnakeSafe", "/img/logo.svg", "Fast help", "108", "1800-116-117",
                2, "en", "DEMO", "STUB", "STUB", 5, 2, false, true, "Asia/Kolkata",
                "LOCAL", "uploads", false, "secret", 24, null, null,
                new AppProperties.Limits(1000, 10, 10), false, true, "admin", "admin123", "staff123"
        );

        service = new IdentifyService(identifier, snakeRepo, photoService, props);
    }

    @Test
    void confidenceBelowHalfReturnsUnsure() {
        // Even for a venomous snake, low confidence (< 0.50) must return UNSURE
        Guess lowConf = new Guess("Naja naja", "Cobra", "ನಾಗರಹಾವು", 0.49, Venomous.YES, 1L);
        assertThat(IdentifyService.overall(List.of(lowConf))).isEqualTo(Venomous.UNSURE);

        // Low confidence on non-venomous also returns UNSURE
        Guess lowConfNonVen = new Guess("Ptyas mucosa", "Rat Snake", "ಕರೆಹಾವು", 0.35, Venomous.NO, 2L);
        assertThat(IdentifyService.overall(List.of(lowConfNonVen))).isEqualTo(Venomous.UNSURE);
    }

    @Test
    void nonVenomousTopGuessWithVenomousSecondaryGuessAtOrAboveTwentyPercentReturnsUnsure() {
        Guess top = new Guess("Ptyas mucosa", "Rat Snake", "ಕರೆಹಾವು", 0.75, Venomous.NO, 2L);
        Guess secondVenomous = new Guess("Daboia russelii", "Russell's Viper", "ಕೊಳಕು ಮಂಡಲ", 0.20, Venomous.YES, 3L);

        // 20% venomous alternative triggers safety downgrade to UNSURE
        assertThat(IdentifyService.overall(List.of(top, secondVenomous))).isEqualTo(Venomous.UNSURE);

        // Even higher confidence venomous secondary guess also returns UNSURE
        Guess secondHigherVenomous = new Guess("Daboia russelii", "Russell's Viper", "ಕೊಳಕು ಮಂಡಲ", 0.25, Venomous.YES, 3L);
        assertThat(IdentifyService.overall(List.of(top, secondHigherVenomous))).isEqualTo(Venomous.UNSURE);
    }

    @Test
    void verifiedNonVenomousTopGuessWithSufficientConfidenceAndNoQualifyingVenomousAlternativeReturnsNo() {
        Guess top = new Guess("Ptyas mucosa", "Rat Snake", "ಕರೆಹಾವು", 0.80, Venomous.NO, 2L);
        // Secondary venomous guess is below 0.20 threshold
        Guess secondLow = new Guess("Daboia russelii", "Russell's Viper", "ಕೊಳಕು ಮಂಡಲ", 0.15, Venomous.YES, 3L);

        assertThat(IdentifyService.overall(List.of(top, secondLow))).isEqualTo(Venomous.NO);

        // Secondary non-venomous guess above 0.20 also preserves NO
        Guess secondNonVen = new Guess("Ptyas mucosa", "Rat Snake", "ಕರೆಹಾವು", 0.25, Venomous.NO, 2L);
        assertThat(IdentifyService.overall(List.of(top, secondNonVen))).isEqualTo(Venomous.NO);
    }

    @Test
    void verifiedVenomousTopGuessWithSufficientConfidenceReturnsYes() {
        Guess top = new Guess("Naja naja", "Spectacled Cobra", "ನಾಗರಹಾವು", 0.65, Venomous.YES, 1L);
        Guess second = new Guess("Ptyas mucosa", "Rat Snake", "ಕರೆಹಾವು", 0.30, Venomous.NO, 2L);

        assertThat(IdentifyService.overall(List.of(top, second))).isEqualTo(Venomous.YES);
    }

    @Test
    void unknownSpeciesReturnsUnsure() {
        // Unknown species (not in snake repository)
        Guess unknown = new Guess("Unknown alien_species", "Unknown", null, 0.95, Venomous.UNSURE, null);
        assertThat(IdentifyService.overall(List.of(unknown))).isEqualTo(Venomous.UNSURE);

        // Empty guesses list
        assertThat(IdentifyService.overall(List.of())).isEqualTo(Venomous.UNSURE);
    }

    @Test
    void venomousStatusStrictlyComesFromDatabaseNotAiPrediction() {
        // AI returns a raw guess for Naja naja (database has venomous=true)
        fakeGuesses.add(new SnakeIdentifier.RawGuess("Naja naja", "Cobra", 0.88));

        MockMultipartFile file = new MockMultipartFile("file", "snake.jpg", "image/jpeg", VALID_JPEG);
        IdentifyService.Result result = service.identify(file, false);

        assertThat(result.guesses()).hasSize(1);
        Guess guess = result.guesses().get(0);
        assertThat(guess.scientificName()).isEqualTo("Naja naja");
        assertThat(guess.venomous()).isEqualTo(Venomous.YES);
        assertThat(result.venomous()).isEqualTo(Venomous.YES);
        assertThat(result.disclaimer()).isEqualTo(IdentifyService.DISCLAIMER);
        assertThat(result.emergencyNumber()).isEqualTo("108");
        assertThat(result.mode()).isEqualTo("TEST_STUB");
    }

    @Test
    void nonVenomousSpeciesFromDatabaseYieldsNoWhenConfident() {
        fakeGuesses.add(new SnakeIdentifier.RawGuess("Ptyas mucosa", "Rat Snake", 0.85));

        MockMultipartFile file = new MockMultipartFile("file", "snake.jpg", "image/jpeg", VALID_JPEG);
        IdentifyService.Result result = service.identify(file, false);

        assertThat(result.guesses()).hasSize(1);
        Guess guess = result.guesses().get(0);
        assertThat(guess.scientificName()).isEqualTo("Ptyas mucosa");
        assertThat(guess.venomous()).isEqualTo(Venomous.NO);
        assertThat(result.venomous()).isEqualTo(Venomous.NO);
    }

    @Test
    void photoRetentionRespectsKeepFlag() {
        fakeGuesses.add(new SnakeIdentifier.RawGuess("Naja naja", "Cobra", 0.80));
        MockMultipartFile file = new MockMultipartFile("file", "snake.jpg", "image/jpeg", VALID_JPEG);

        // When keep = false, photo is NOT stored (0 photos saved)
        IdentifyService.Result notKept = service.identify(file, false);
        assertThat(notKept.photoKept()).isFalse();
        assertThat(photoStored.get()).isFalse();
        assertThat(savedPhotos).isEmpty();

        // When keep = true, photo is stored exactly once with matching species name
        IdentifyService.Result kept = service.identify(file, true);
        assertThat(kept.photoKept()).isTrue();
        assertThat(photoStored.get()).isTrue();
        assertThat(savedPhotos).hasSize(1);
        SnakePhoto stored = savedPhotos.get(0);
        assertThat(stored.species).isEqualTo("Spectacled Cobra");
        assertThat(stored.source).isEqualTo("identify");
        assertThat(stored.consent).isTrue();
    }

    @Test
    void emptyGuessesNeverStorePhotoEvenWhenKeepIsTrue() {
        // AI returns no raw guesses (e.g., notASnake: true or AI refusal)
        fakeGuesses.clear();
        MockMultipartFile file = new MockMultipartFile("file", "non_snake.jpg", "image/jpeg", VALID_JPEG);

        // When keep = false, zero photos stored
        IdentifyService.Result notKept = service.identify(file, false);
        assertThat(notKept.photoKept()).isFalse();
        assertThat(savedPhotos).isEmpty();

        // Even when keep = true (consent given), non-snake / empty guesses MUST NOT be stored
        IdentifyService.Result kept = service.identify(file, true);
        assertThat(kept.photoKept()).isFalse();
        assertThat(savedPhotos).isEmpty();
        assertThat(photoStored.get()).isFalse();
    }

    @Test
    void validUncertainOrUnlistedSnakeGuessCanBeRetainedWithConsent() {
        // AI returns a guess for an unlisted or uncertain snake species
        fakeGuesses.clear();
        fakeGuesses.add(new SnakeIdentifier.RawGuess("Boiga trigonata", "Common Cat Snake", 0.45));
        MockMultipartFile file = new MockMultipartFile("file", "cat_snake.jpg", "image/jpeg", VALID_JPEG);

        // When keep = false, zero photos stored
        savedPhotos.clear();
        IdentifyService.Result notKept = service.identify(file, false);
        assertThat(notKept.photoKept()).isFalse();
        assertThat(savedPhotos).isEmpty();

        // When keep = true with valid guess, exactly one photo is retained with the species label
        savedPhotos.clear();
        IdentifyService.Result kept = service.identify(file, true);
        assertThat(kept.photoKept()).isTrue();
        assertThat(savedPhotos).hasSize(1);
        SnakePhoto stored = savedPhotos.get(0);
        assertThat(stored.species).isEqualTo("Common Cat Snake");
        assertThat(stored.source).isEqualTo("identify");
        assertThat(stored.consent).isTrue();
    }

    @Test
    void validUncertainSnakeGuessWithoutCommonNameUsesScientificName() {
        fakeGuesses.clear();
        fakeGuesses.add(new SnakeIdentifier.RawGuess("Colubridae sp.", null, 0.40));
        MockMultipartFile file = new MockMultipartFile("file", "colubrid.jpg", "image/jpeg", VALID_JPEG);

        savedPhotos.clear();
        IdentifyService.Result kept = service.identify(file, true);
        assertThat(kept.photoKept()).isTrue();
        assertThat(savedPhotos).hasSize(1);
        assertThat(savedPhotos.get(0).species).isEqualTo("Colubridae sp.");
    }

    @Test
    void photoStorageFailureDoesNotAbortIdentificationAndReturnsPhotoKeptFalse() {
        fakeGuesses.clear();
        fakeGuesses.add(new SnakeIdentifier.RawGuess("Naja naja", "Cobra", 0.85));
        MockMultipartFile file = new MockMultipartFile("file", "snake.jpg", "image/jpeg", VALID_JPEG);

        storageFails.set(true);
        savedPhotos.clear();

        // When storage throws an exception during consented retention, identify() must succeed
        IdentifyService.Result result = service.identify(file, true);

        // Identification results must be intact and fully returned
        assertThat(result.photoKept()).isFalse();
        assertThat(result.guesses()).hasSize(1);
        assertThat(result.guesses().get(0).nameEn()).isEqualTo("Spectacled Cobra");
        assertThat(result.venomous()).isEqualTo(Venomous.YES);
        assertThat(result.emergencyNumber()).isEqualTo("108");
        assertThat(result.disclaimer()).isEqualTo(IdentifyService.DISCLAIMER);
        assertThat(savedPhotos).isEmpty();
        assertThat(photoStored.get()).isFalse();
    }
}
