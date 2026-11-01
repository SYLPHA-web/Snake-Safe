package com.snakesafe.chat;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.snakesafe.config.AppProperties;
import com.snakesafe.config.RateLimiter;
import com.snakesafe.config.Sanitizer;
import com.snakesafe.web.ApiException;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_HISTORY = 10;
    private static final int MAX_TURN_LENGTH = 1000;
    private static final List<String> BITE_WORDS = List.of("bite", "bitten", "bit me", "bit my", "bit him", "bit her",
            "snakebite", "ಕಚ್ಚ", "ಕಡಿ");

    public record Turn(String role, String content) {
    }

    /** fallback = the AI assistant could not answer, so a fixed safe answer was used instead. */
    public record Answer(String reply, boolean fallback) {
    }

    private final ChatProvider provider;
    private final CannedReplies canned;
    private final ChatLogRepository logs;
    private final RateLimiter limiter;
    private final AppProperties props;
    private final Clock clock;

    public ChatService(ChatProvider provider, CannedReplies canned, ChatLogRepository logs, RateLimiter limiter,
            AppProperties props, Clock clock) {
        this.provider = provider;
        this.canned = canned;
        this.logs = logs;
        this.limiter = limiter;
        this.props = props;
        this.clock = clock;
    }

    public Answer chat(String rawMessage, String language, List<Turn> history, String ip) {
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

        String reply = null;
        boolean fallback = false;
        try {
            reply = provider.reply(message, lang, turns);
        } catch (RuntimeException e) {
            String why = e.getMessage() == null ? "" : e.getMessage().replaceAll("\\s+", " ");
            log.warn("Chat assistant failed ({}: {}). Using the built-in answer.", e.getClass().getSimpleName(),
                    why.length() > 300 ? why.substring(0, 300) : why);
        }
        if (reply == null || reply.isBlank()) {
            reply = canned.reply(message, lang);
            fallback = true;
        }
        reply = reply.strip();
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
        return new Answer(reply, fallback);
    }

    static boolean mentionsBite(String message) {
        String m = message.toLowerCase(Locale.ROOT);
        return BITE_WORDS.stream().anyMatch(m::contains);
    }

    private static String truncate(String s) {
        return s.length() > MAX_TURN_LENGTH ? s.substring(0, MAX_TURN_LENGTH) : s;
    }
}
