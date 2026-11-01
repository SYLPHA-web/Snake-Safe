package com.snakesafe.chat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.snakesafe.config.AiClient;
import com.snakesafe.web.ApiException;

/** The system prompt lives in chat-system-prompt.txt, the one place to edit the assistant's rules. */
@Component
@ConditionalOnProperty(name = "app.chat-mode", havingValue = "AI")
public class AiChatProvider implements ChatProvider {

    private static final Logger log = LoggerFactory.getLogger(AiChatProvider.class);

    private final AiClient ai;
    private final String systemPrompt;

    public AiChatProvider(AiClient ai) throws IOException {
        this.ai = ai;
        try (InputStream in = new ClassPathResource("chat-system-prompt.txt").getInputStream()) {
            this.systemPrompt = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }

    @Override
    public String reply(String message, String language, List<Turn> history) {
        MessageCreateParams.Builder request = MessageCreateParams.builder()
                .model(ai.model())
                .maxTokens(2000L)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .system(systemPrompt + "\n\nThe user's language for this chat: "
                        + ("kn".equals(language) ? "Kannada (ಕನ್ನಡ). Reply in Kannada." : "English. Reply in English."));
        boolean started = false;
        for (Turn t : history) {
            if ("user".equals(t.role())) {
                request.addUserMessage(t.content());
                started = true;
            } else if (started) {
                request.addAssistantMessage(t.content());
            }
        }
        request.addUserMessage(message);
        try {
            Message response = ai.get().messages().create(request.build());
            if (response.stopReason().map(StopReason.REFUSAL::equals).orElse(false)) {
                return "";
            }
            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(t -> t.text())
                    .collect(Collectors.joining())
                    .strip();
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Chat call failed: {}: {}", e.getClass().getSimpleName(), String.valueOf(e.getMessage()).replaceAll("\\s+", " ").substring(0, Math.min(300, String.valueOf(e.getMessage()).length())));
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_ERROR",
                    "The assistant is not available right now. If someone was bitten, call 108.");
        }
    }
}
