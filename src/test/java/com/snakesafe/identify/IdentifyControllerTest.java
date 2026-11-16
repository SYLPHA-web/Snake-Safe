package com.snakesafe.identify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import com.snakesafe.config.AppProperties;
import com.snakesafe.config.RateLimiter;
import com.snakesafe.snake.Snake;
import com.snakesafe.snake.SnakeRepository;
import com.snakesafe.web.ApiException;

class IdentifyControllerTest {

    private static final byte[] VALID_JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0 };
    private static final byte[] VALID_PNG = { (byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G', 0x0D, 0x0A, 0x1A, 0x0A };

    private IdentifyController controller;
    private AppProperties props;
    private RateLimiter limiter;
    private AtomicLong photoIdGen;
    private List<SnakePhoto> savedPhotos;

    @BeforeEach
    void setUp() {
        photoIdGen = new AtomicLong(100);
        savedPhotos = new ArrayList<>();

        Map<String, Snake> snakeDb = new HashMap<>();
        Snake cobra = new Snake();
        cobra.id = 1L;
        cobra.scientificName = "Naja naja";
        cobra.nameEn = "Spectacled Cobra";
        cobra.nameKn = "ನಾಗರಹಾವು";
        cobra.venomous = true;
        snakeDb.put(cobra.scientificName.toLowerCase(), cobra);

        SnakeRepository snakeRepo = (SnakeRepository) Proxy.newProxyInstance(
                SnakeRepository.class.getClassLoader(),
                new Class<?>[] { SnakeRepository.class },
                (proxy, method, args) -> {
                    if ("findFirstByScientificNameIgnoreCase".equals(method.getName())) {
                        String name = ((String) args[0]).toLowerCase();
                        return Optional.ofNullable(snakeDb.get(name));
                    }
                    if ("findAll".equals(method.getName())) {
                        return new ArrayList<>(snakeDb.values());
                    }
                    return null;
                });

        SnakeIdentifier stubIdentifier = new StubSnakeIdentifier(snakeRepo);

        PhotoStorage storage = (fileName, data, contentType) -> fileName;
        SnakePhotoRepository photoRepo = (SnakePhotoRepository) Proxy.newProxyInstance(
                SnakePhotoRepository.class.getClassLoader(),
                new Class<?>[] { SnakePhotoRepository.class },
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) {
                        SnakePhoto p = (SnakePhoto) args[0];
                        p.id = photoIdGen.incrementAndGet();
                        savedPhotos.add(p);
                        return p;
                    }
                    return null;
                });

        PhotoService photoService = new PhotoService(storage, photoRepo, Clock.systemUTC());

        props = new AppProperties(
                "SnakeSafe", "/img/logo.svg", "Fast help", "108", "1800-116-117",
                2, "en", "DEMO", "STUB", "STUB", 5, 2, false, true, "Asia/Kolkata",
                "LOCAL", "uploads", false, "secret", 24, null, null,
                new AppProperties.Limits(1000, 10, 10), false, true, "admin", "admin123", "staff123"
        );

        limiter = new RateLimiter(Clock.systemUTC());
        IdentifyService identifyService = new IdentifyService(stubIdentifier, snakeRepo, photoService, props);
        controller = new IdentifyController(identifyService, photoService, limiter, props);
    }

    @Test
    void identifiesValidJpegAndPngSuccessfully() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        MockMultipartFile jpeg = new MockMultipartFile("file", "snake.jpg", "image/jpeg", VALID_JPEG);
        IdentifyService.Result resJpeg = controller.identify(jpeg, false, request);

        assertThat(resJpeg).isNotNull();
        assertThat(resJpeg.disclaimer()).isEqualTo("This may be wrong. Treat every bite as an emergency.");
        assertThat(resJpeg.mode()).isEqualTo("STUB");
        assertThat(resJpeg.emergencyNumber()).isEqualTo("108");
        assertThat(resJpeg.callToAction()).isEqualTo("Call 108");

        MockMultipartFile png = new MockMultipartFile("file", "snake.png", "image/png", VALID_PNG);
        IdentifyService.Result resPng = controller.identify(png, false, request);
        assertThat(resPng).isNotNull();
        assertThat(resPng.guesses()).isNotEmpty();
    }

    @Test
    void rejectsEmptyOversizedAndUnsupportedImages() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");

        // 1. Empty image
        MockMultipartFile empty = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> controller.identify(empty, false, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ae = (ApiException) e;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ae.getCode()).isEqualTo("NO_FILE");
                });

        // 2. Oversized image (> 5 MB)
        byte[] oversized = new byte[(int) ImageFiles.MAX_BYTES + 1];
        System.arraycopy(VALID_JPEG, 0, oversized, 0, VALID_JPEG.length);
        MockMultipartFile over = new MockMultipartFile("file", "large.jpg", "image/jpeg", oversized);
        assertThatThrownBy(() -> controller.identify(over, false, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ae = (ApiException) e;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                    assertThat(ae.getCode()).isEqualTo("FILE_TOO_LARGE");
                });

        // 3. Unsupported image (plain text / invalid magic bytes)
        MockMultipartFile unsupported = new MockMultipartFile("file", "text.jpg", "image/jpeg", "not a snake".getBytes());
        assertThatThrownBy(() -> controller.identify(unsupported, false, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ae = (ApiException) e;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                    assertThat(ae.getCode()).isEqualTo("UNSUPPORTED_IMAGE");
                });
    }

    @Test
    void rateLimitingTriggersWhenRequestsExceedLimit() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.50");
        MockMultipartFile jpeg = new MockMultipartFile("file", "snake.jpg", "image/jpeg", VALID_JPEG);

        // Limit is 10 calls per minute
        for (int i = 0; i < 10; i++) {
            IdentifyService.Result r = controller.identify(jpeg, false, request);
            assertThat(r).isNotNull();
        }

        // 11th call must trigger RATE_LIMITED
        assertThatThrownBy(() -> controller.identify(jpeg, false, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ae = (ApiException) e;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ae.getCode()).isEqualTo("RATE_LIMITED");
                });
    }

    @Test
    void photoContributeEnforcesConsentRequirement() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.3");
        MockMultipartFile jpeg = new MockMultipartFile("file", "training.jpg", "image/jpeg", VALID_JPEG);

        // Missing consent (false) must be rejected with 400 CONSENT_REQUIRED
        assertThatThrownBy(() -> controller.contribute(jpeg, "Naja naja", "user", false, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ae = (ApiException) e;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ae.getCode()).isEqualTo("CONSENT_REQUIRED");
                });

        // Explicit consent (true) succeeds
        IdentifyController.PhotoSaved saved = controller.contribute(jpeg, "Naja naja", "user", true, request);
        assertThat(saved).isNotNull();
        assertThat(saved.id()).isPositive();
        assertThat(saved.species()).isEqualTo("Naja naja");
    }

    @Test
    void photoContributeValidatesAndSanitizesInput() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.4");
        MockMultipartFile jpeg = new MockMultipartFile("file", "training.jpg", "image/jpeg", VALID_JPEG);

        // Blank species
        assertThatThrownBy(() -> controller.contribute(jpeg, "   ", "user", true, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("VALIDATION_ERROR"));

        // HTML tags without text sanitized to empty string, triggering validation error
        assertThatThrownBy(() -> controller.contribute(jpeg, "<script></script>", "user", true, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("VALIDATION_ERROR"));

        // HTML tags around text are stripped clean and stored safely
        IdentifyController.PhotoSaved sanitizedSaved = controller.contribute(jpeg, "<b>Cobra</b>", "<em>field</em>", true, request);
        assertThat(sanitizedSaved.species()).isEqualTo("Cobra");

        // Species exceeds 150 characters
        String longSpecies = "A".repeat(151);
        assertThatThrownBy(() -> controller.contribute(jpeg, longSpecies, "user", true, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("VALIDATION_ERROR"));

        // Source exceeds 50 characters
        String longSource = "S".repeat(51);
        assertThatThrownBy(() -> controller.contribute(jpeg, "Cobra", longSource, true, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("VALIDATION_ERROR"));

        // Invalid file format for photo contribute
        MockMultipartFile badFile = new MockMultipartFile("file", "bad.gif", "image/gif", "corrupt".getBytes());
        assertThatThrownBy(() -> controller.contribute(badFile, "Cobra", "user", true, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("UNSUPPORTED_IMAGE"));
    }

    @Test
    void identifyConsentChoicesAreEnforced() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        MockMultipartFile jpeg = new MockMultipartFile("file", "snake.jpg", "image/jpeg", VALID_JPEG);

        // 1. Both false: zero photos stored
        savedPhotos.clear();
        IdentifyService.Result withoutConsent = controller.identify(jpeg, false, false, request);
        assertThat(withoutConsent.photoKept()).isFalse();
        assertThat(savedPhotos).isEmpty();

        // 2. keep=true, consent=false: zero photos stored
        savedPhotos.clear();
        IdentifyService.Result keepNoConsent = controller.identify(jpeg, true, false, request);
        assertThat(keepNoConsent.photoKept()).isFalse();
        assertThat(savedPhotos).isEmpty();

        // 3. keep=false, consent=true: zero photos stored
        savedPhotos.clear();
        IdentifyService.Result consentNoKeep = controller.identify(jpeg, false, true, request);
        assertThat(consentNoKeep.photoKept()).isFalse();
        assertThat(savedPhotos).isEmpty();

        // 4. Legacy 3-arg overload with keep=true: cannot bypass consent, zero photos stored
        savedPhotos.clear();
        IdentifyService.Result legacyWithKeep = controller.identify(jpeg, true, request);
        assertThat(legacyWithKeep.photoKept()).isFalse();
        assertThat(savedPhotos).isEmpty();

        // 5. Legacy 3-arg overload with keep=false: zero photos stored
        savedPhotos.clear();
        IdentifyService.Result legacyWithoutKeep = controller.identify(jpeg, false, request);
        assertThat(legacyWithoutKeep.photoKept()).isFalse();
        assertThat(savedPhotos).isEmpty();

        // 6. BOTH keep=true AND consent=true: photo is kept and stored exactly once
        savedPhotos.clear();
        IdentifyService.Result withConsentAndKeep = controller.identify(jpeg, true, true, request);
        assertThat(withConsentAndKeep.photoKept()).isTrue();
        assertThat(savedPhotos).hasSize(1);
        assertThat(savedPhotos.get(0).species).isEqualTo("Spectacled Cobra");
        assertThat(savedPhotos.get(0).source).isEqualTo("identify");
        assertThat(savedPhotos.get(0).consent).isTrue();
    }

    @Test
    void singleOptInActionStoresAtMostOnePhoto() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.6");
        MockMultipartFile jpeg = new MockMultipartFile("file", "snake.jpg", "image/jpeg", VALID_JPEG);

        savedPhotos.clear();

        // Single identification request with consent opts in
        IdentifyService.Result result = controller.identify(jpeg, true, true, request);

        assertThat(result.photoKept()).isTrue();
        // Crucial requirement: stores at most one photo (never duplicated)
        assertThat(savedPhotos).hasSize(1);
        assertThat(savedPhotos.get(0).species).isEqualTo("Spectacled Cobra");
        assertThat(savedPhotos.get(0).source).isEqualTo("identify");
        assertThat(savedPhotos.get(0).consent).isTrue();
    }

    @Test
    void snakeIdentificationConsentBilingualLabelsAndUIRequirements() throws Exception {
        // 1. Verify i18n translations for consent
        Path i18nPath = Path.of("src/main/resources/static/js/i18n.js");
        assertThat(Files.exists(i18nPath)).isTrue();
        String i18nContent = Files.readString(i18nPath, StandardCharsets.UTF_8);

        // Both English keys must be present
        assertThat(i18nContent).contains("'id.consentLabel'");
        assertThat(i18nContent).contains("'id.consentSub'");

        // English explains opting in allows storing photo for improving Karnataka snake identification model
        assertThat(i18nContent).contains("Karnataka snake identification model");
        assertThat(i18nContent).contains("Participation is optional");
        assertThat(i18nContent).contains("not stored unless you explicitly opt in");

        // Requirement 7: Do not claim personal information is removed
        assertThat(i18nContent).doesNotContain("personal information is removed");
        assertThat(i18nContent).doesNotContain("personal data is removed");
        assertThat(i18nContent).doesNotContain("anonymized");

        // Kannada keys must contain authentic Kannada text
        assertThat(i18nContent).contains("ಕರ್ನಾಟಕ ಹಾವು ಗುರುತಿಸುವಿಕೆ ಮಾದರಿ");
        assertThat(i18nContent).contains("ಐಚ್ಛಿಕ");
        assertThat(i18nContent).contains("ಒಪ್ಪಿಗೆ ನೀಡದಿದ್ದರೆ ಫೋಟೋ ಉಳಿಸುವುದಿಲ್ಲ");

        // 2. Verify identify.js UI
        Path identifyJsPath = Path.of("src/main/resources/static/js/pages/identify.js");
        assertThat(Files.exists(identifyJsPath)).isTrue();
        String identifyJs = Files.readString(identifyJsPath, StandardCharsets.UTF_8);

        // Checkbox must be unchecked by default (no checked attribute on #keep)
        assertThat(identifyJs).contains("<input type=\"checkbox\" id=\"keep\"");
        assertThat(identifyJs).doesNotContain("<input type=\"checkbox\" id=\"keep\" checked");

        // Must display bilingual translations
        assertThat(identifyJs).contains("${t('id.consentLabel')}");
        assertThat(identifyJs).contains("${t('id.consentSub')}");

        // Single-request approach: /api/identify is used, sending keep and consent when checked
        assertThat(identifyJs).contains("api('/api/identify'");
        assertThat(identifyJs).contains("fd.append('keep', 'true')");
        assertThat(identifyJs).contains("fd.append('consent', 'true')");

        // Redundant second endpoint must NOT be called from identify.js
        assertThat(identifyJs).doesNotContain("/api/snake-photos");
        assertThat(identifyJs).doesNotContain("api('/api/snake-photos'");
    }

    @Test
    void offlineAvailabilityWarningBilingualLabels() throws Exception {
        Path i18nPath = Path.of("src/main/resources/static/js/i18n.js");
        assertThat(Files.exists(i18nPath)).isTrue();
        String i18nContent = Files.readString(i18nPath, StandardCharsets.UTF_8);

        // English label must explain that internet connection is required
        assertThat(i18nContent).contains("'id.offlineWarning': 'Offline — Internet connection required to identify a snake.'");

        // Kannada label must provide natural, authentic Kannada phrasing
        assertThat(i18nContent).contains("'id.offlineWarning': 'ಆಫ್‌ಲೈನ್ — ಹಾವು ಗುರುತಿಸಲು ಇಂಟರ್ನೆಟ್ ಸಂಪರ್ಕ ಅಗತ್ಯವಿದೆ.'");

        // Must not imply offline AI identification is available
        assertThat(i18nContent.toLowerCase()).doesNotContain("offline ai");
        assertThat(i18nContent.toLowerCase()).doesNotContain("offline model");
    }

    @Test
    void offlineAvailabilityConnectivityHandlingAndUI() throws Exception {
        Path identifyJsPath = Path.of("src/main/resources/static/js/pages/identify.js");
        assertThat(Files.exists(identifyJsPath)).isTrue();
        String identifyJs = Files.readString(identifyJsPath, StandardCharsets.UTF_8);

        // 1. Connectivity detection via navigator.onLine
        assertThat(identifyJs).contains("navigator.onLine");

        // 2. Amber warning replaces green ready status when offline
        assertThat(identifyJs).contains("chip-green");
        assertThat(identifyJs).contains("chip-amber");
        assertThat(identifyJs).contains("t('id.offlineWarning')");

        // 3. Online and offline event listeners update status automatically without reload
        assertThat(identifyJs).contains("window.addEventListener('online'");
        assertThat(identifyJs).contains("window.addEventListener('offline'");
        assertThat(identifyJs).contains("window.removeEventListener('online'");
        assertThat(identifyJs).contains("window.removeEventListener('offline'");

        // 4. Disables or prevents action when offline
        assertThat(identifyJs).contains("toast(t('id.offlineWarning'), 'err')");
        assertThat(identifyJs).contains("disabled style=\"opacity:0.5;cursor:not-allowed\"");

        // 5. Preserves emergency banner and Call 108
        assertThat(identifyJs).contains("${t('id.bannerTitle')}");
        assertThat(identifyJs).contains("cfg.emergencyNumber");
        assertThat(identifyJs).contains("${t('id.safetyTitle')}");
    }
}
