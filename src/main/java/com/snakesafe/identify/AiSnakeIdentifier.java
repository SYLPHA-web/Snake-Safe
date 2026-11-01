package com.snakesafe.identify;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snakesafe.config.AiClient;
import com.snakesafe.snake.Snake;
import com.snakesafe.snake.SnakeRepository;
import com.snakesafe.web.ApiException;

/** Sends the photo to Claude (key from AI_API_KEY, server side only) and asks for JSON guesses. */
@Component
@ConditionalOnProperty(name = "app.identify-mode", havingValue = "AI")
public class AiSnakeIdentifier implements SnakeIdentifier {

    private static final Logger log = LoggerFactory.getLogger(AiSnakeIdentifier.class);

    private final AiClient ai;
    private final SnakeRepository snakes;
    private final ObjectMapper json;
    private final String prompt;

    public AiSnakeIdentifier(AiClient ai, SnakeRepository snakes, ObjectMapper json) throws IOException {
        this.ai = ai;
        this.snakes = snakes;
        this.json = json;
        try (InputStream in = new ClassPathResource("identify-prompt.txt").getInputStream()) {
            this.prompt = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<RawGuess> identify(byte[] image, String mediaType) {
        String species = snakes.findAll().stream()
                .map(s -> s.scientificName + " (" + s.nameEn + ")")
                .collect(Collectors.joining("\n"));
        Base64ImageSource.MediaType type = "image/png".equals(mediaType)
                ? Base64ImageSource.MediaType.IMAGE_PNG : Base64ImageSource.MediaType.IMAGE_JPEG;
        MessageCreateParams params = MessageCreateParams.builder()
                .model(ai.model())
                .maxTokens(4000L)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                .system(prompt)
                .addUserMessageOfBlockParams(List.of(
                        ContentBlockParam.ofImage(ImageBlockParam.builder()
                                .source(Base64ImageSource.builder()
                                        .data(Base64.getEncoder().encodeToString(image))
                                        .mediaType(type)
                                        .build())
                                .build()),
                        ContentBlockParam.ofText(TextBlockParam.builder()
                                .text("Species we know about:\n" + species + "\n\nReturn the JSON now.")
                                .build())))
                .build();
        try {
            Message response = ai.get().messages().create(params);
            if (response.stopReason().map(StopReason.REFUSAL::equals).orElse(false)) {
                return List.of();
            }
            String text = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(t -> t.text())
                    .collect(Collectors.joining());
            return parse(text);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Photo identification call failed: {}", e.getClass().getSimpleName());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_ERROR",
                    "Photo identification is not available right now. If someone was bitten, call 108.");
        }
    }

    private List<RawGuess> parse(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return List.of();
        }
        try {
            JsonNode root = json.readTree(text.substring(start, end + 1));
            if (root.path("notASnake").asBoolean(false)) {
                return List.of();
            }
            List<RawGuess> guesses = new ArrayList<>();
            for (JsonNode g : root.path("guesses")) {
                guesses.add(new RawGuess(g.path("scientificName").asText(""), g.path("commonName").asText(""),
                        g.path("confidence").asDouble(0)));
            }
            return guesses;
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public String mode() {
        return "AI";
    }
}
