package com.snakesafe.snake;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.snakesafe.config.AppProperties;

class FirstAidContentTest {

    private FirstAidController controller;

    @BeforeEach
    void setUp() throws Exception {
        AppProperties props = new AppProperties(
                "SnakeSafe", "/img/logo.svg", "Fast help", "108", "1800-116-117",
                2, "en", "DEMO", "STUB", "STUB", 5, 2, false, true, "Asia/Kolkata",
                "LOCAL", "target/test-photos", false, "secret123456789012345678901234567890", 24,
                null, null, new AppProperties.Limits(1000, 10, 10), false, true, "admin", "admin123", "staff123"
        );
        controller = new FirstAidController(new ObjectMapper(), props);
    }

    @Test
    void firstAidEndpointReturnsExpectedStructureAndNumbers() {
        ObjectNode res = controller.firstAid();
        assertThat(res.has("title")).isTrue();
        assertThat(res.has("reminder")).isTrue();
        assertThat(res.has("steps")).isTrue();
        assertThat(res.has("avoid")).isTrue();
        assertThat(res.has("numbers")).isTrue();

        assertThat(res.get("numbers").get("emergency").asText()).isEqualTo("108");
        assertThat(res.get("numbers").get("poisonHelpline").asText()).isEqualTo("1800-116-117");

        assertThat(res.get("title").get("en").asText()).isEqualTo("First aid for a snakebite");
        assertThat(res.get("title").get("kn").asText()).matches(".*[\\u0C80-\\u0CFF].*");

        assertThat(res.get("reminder").get("en").asText()).contains("Treat every bite as an emergency");
        assertThat(res.get("reminder").get("kn").asText()).matches(".*[\\u0C80-\\u0CFF].*");
    }

    @Test
    void allSevenDoStepsArePresentSafeAndBilingual() {
        ObjectNode res = controller.firstAid();
        JsonNode steps = res.get("steps");
        assertThat(steps.size()).isEqualTo(7);

        List<String> ids = new ArrayList<>();
        for (JsonNode s : steps) {
            String id = s.get("id").asText();
            ids.add(id);
            assertThat(s.get("en").get("title").asText()).isNotBlank();
            assertThat(s.get("en").get("text").asText()).isNotBlank();
            assertThat(s.get("kn").get("title").asText()).matches(".*[\\u0C80-\\u0CFF].*");
            assertThat(s.get("kn").get("text").asText()).matches(".*[\\u0C80-\\u0CFF].*");

            // Positive instructions must never affirmatively advise harmful practices
            String enText = s.get("en").get("text").asText().toLowerCase();
            for (String harmful : List.of("tourniquet", "suck", "incision", "cut", "ice", "dose", "dosage", "mg ", "ibuprofen")) {
                assertThat(enText).doesNotContain(harmful);
            }
        }

        assertThat(ids).containsExactly("calm", "move-away", "limb-still", "remove-tight", "call-108", "use-app", "note-time");

        // Verify limb immobilization instruction mentions loose splint and without restricting blood circulation
        JsonNode limbStill = null;
        for (JsonNode s : steps) {
            if ("limb-still".equals(s.get("id").asText())) {
                limbStill = s;
                break;
            }
        }
        assertThat(limbStill).isNotNull();
        String enLimb = limbStill.get("en").get("text").asText().toLowerCase();
        assertThat(enLimb).contains("splint", "without restricting blood circulation");
        assertThat(enLimb).doesNotContain("pressure bandage", "pressure immobilization");

        String knLimb = limbStill.get("kn").get("text").asText();
        assertThat(knLimb).contains("ಆಸರೆ", "ರಕ್ತ ಸಂಚಾರಕ್ಕೆ ಅಡ್ಡಿಯಾಗದಂತೆ");
    }

    @Test
    void avoidSectionCoversAllFiveNcdcWarningsInEnglishAndKannada() {
        ObjectNode res = controller.firstAid();
        JsonNode avoid = res.get("avoid");
        assertThat(avoid.size()).isGreaterThanOrEqualTo(6);

        List<String> ids = new ArrayList<>();
        StringBuilder enAll = new StringBuilder();
        StringBuilder knAll = new StringBuilder();
        for (JsonNode a : avoid) {
            ids.add(a.get("id").asText());
            enAll.append(a.get("en").asText().toLowerCase()).append(" ");
            knAll.append(a.get("kn").asText()).append(" ");
            assertThat(a.get("kn").asText()).matches(".*[\\u0C80-\\u0CFF].*");
        }

        assertThat(ids).contains("no-tourniquet", "no-cut-suck", "no-ice-heat", "no-home-remedies", "no-medicines", "no-delay");

        // 1. Tourniquet warning
        assertThat(enAll.toString()).contains("tourniquet", "tight band");
        assertThat(knAll.toString()).contains("ಟೂರ್ನಿಕೆಟ್", "ಹಗ್ಗ");

        // 2. Cut / scratch / squeeze / suck warning
        assertThat(enAll.toString()).contains("cut", "scratch", "squeeze", "suck");
        assertThat(knAll.toString()).contains("ಕುಯ್ಯಬೇಡಿ", "ಪರಚಬೇಡಿ", "ಹಿಂಡಬೇಡಿ", "ವಿಷ ಹೀರುವ");

        // 3. Ice / heat / chemicals warning
        assertThat(enAll.toString()).contains("ice", "heat", "chemicals");
        assertThat(knAll.toString()).contains("ಮಂಜುಗಡ್ಡೆ", "ಐಸ್", "ರಾಸಾಯನಿಕ");

        // 4. Traditional remedies & healer (nati vaidya)
        assertThat(enAll.toString()).contains("traditional remedies", "healer");
        assertThat(knAll.toString()).contains("ನಾಟಿ ವೈದ್ಯರು", "ಮನೆಮದ್ದು");

        // 5. Medicines without prescription
        assertThat(enAll.toString()).contains("medicines", "painkillers", "qualified medical professional");
        assertThat(knAll.toString()).contains("ಮಾತ್ರೆ", "ಔಷಧಿ");

        // 6. Delay warning
        assertThat(enAll.toString()).contains("delay");
        assertThat(knAll.toString()).contains("ತಡ ಮಾಡಬೇಡಿ");
    }

    @Test
    void serviceWorkerPreCachesFirstAidApi() throws Exception {
        try (InputStream in = new ClassPathResource("static/sw.js").getInputStream()) {
            String sw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sw).contains("'/api/first-aid'");
            assertThat(sw).contains("SHELL");
            assertThat(sw).contains("OFFLINE_DATA");
            assertThat(sw).contains("networkFirst");
        }
    }
}
