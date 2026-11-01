package com.snakesafe.chat;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.snakesafe.config.AppProperties;
import com.snakesafe.config.RateLimiter;
import com.snakesafe.config.Sanitizer;
import com.snakesafe.web.ApiException;

@Service
public class ChatService {

    private static final int MAX_HISTORY = 10;
    private static final int MAX_TURN_LENGTH = 1000;
    private static final List<String> BITE_WORDS = List.of("bite", "bitten", "bit me", "bit my", "bit him", "bit her",
            "snakebite", "ಕಚ್ಚ", "ಕಡಿ");

    public record Turn(String role, String content) {
    }

    private final ChatProvider provider;
    private final ChatLogRepository logs;
    private final RateLimiter limiter;
    private final AppProperties props;
    private final Clock clock;

    public ChatService(ChatProvider provider, ChatLogRepository logs, RateLimiter limiter, AppProperties props,
            Clock clock) {
        this.provider = provider;
        this.logs = logs;
        this.limiter = limiter;
        this.props = props;
        this.clock = clock;
    }

    public String chat(String rawMessage, String language, List<Turn> history, String ip) {
        limiter.check("chat:" + ip, props.limits().chatPerMinute(), Duration.ofMinutes(1));
        String message = Sanitizer.clean(rawMessage);
        if (message == null || message.isEmpty()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "message must not be empty.");
        }
        if (message.length() > props.limits().chatMaxMessageLength()) {
            throw ApiException.badRequest("MESSAGE_TOO_LONG",
                    "Please keep your message under " + props.limits().chatMaxMessageLength() + " characters.");
        }
        String lang = "kn".equals(language) ? "kn" : "en";

        List<ChatProvider.Turn> turns = history == null ? List.of() : history.stream()
                .filter(t -> t != null && t.content() != null && ("user".equals(t.role()) || "assistant".equals(t.role())))
                .map(t -> new ChatProvider.Turn(t.role(), truncate(Sanitizer.clean(t.content()))))
                .filter(t -> !t.content().isEmpty())
                .skip(Math.max(0, history.size() - MAX_HISTORY))
                .toList();

        String reply = provider.reply(message, lang, turns);
        reply = reply == null || reply.isBlank() ? fallback(lang) : reply.strip();
        // Safety net that does not depend on the provider: a mentioned bite always starts with "Call 108 now"
        String callNow = "Call " + props.emergencyNumber() + " now.";
        if (mentionsBite(message) && !reply.startsWith(callNow)) {
            reply = callNow + " " + ("kn".equals(lang)
                    ? "ಈಗಲೇ " + props.emergencyNumber() + " ಗೆ ಕರೆ ಮಾಡಿ ಮತ್ತು ಹತ್ತಿರದ ಆಸ್ಪತ್ರೆ ಹುಡುಕಲು ಕೆಂಪು ತುರ್ತು ಬಟನ್ ಒತ್ತಿ. "
                    : "Tap the red Emergency button to find the nearest hospital with antivenom. ") + reply;
        }

        if (props.logChats()) {
            ChatLog entry = new ChatLog();
            entry.language = lang;
            entry.message = truncate(message);
            entry.reply = reply.length() > 4000 ? reply.substring(0, 4000) : reply;
            entry.createdAt = clock.instant();
            logs.save(entry);
        }
        return reply;
    }

    private String fallback(String lang) {
        return "kn".equals(lang)
                ? "ಕ್ಷಮಿಸಿ, ಈಗ ಉತ್ತರ ನೀಡಲು ಸಾಧ್ಯವಾಗುತ್ತಿಲ್ಲ. ಹಾವು ಕಡಿದಿದ್ದರೆ " + props.emergencyNumber() + " ಗೆ ಕರೆ ಮಾಡಿ."
                : "Sorry, I cannot answer that right now. If someone was bitten, call " + props.emergencyNumber() + " now.";
    }

    static boolean mentionsBite(String message) {
        String m = message.toLowerCase(Locale.ROOT);
        return BITE_WORDS.stream().anyMatch(m::contains);
    }

    private static String truncate(String s) {
        return s.length() > MAX_TURN_LENGTH ? s.substring(0, MAX_TURN_LENGTH) : s;
    }
}
