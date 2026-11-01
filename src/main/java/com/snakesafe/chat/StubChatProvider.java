package com.snakesafe.chat;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.chat-mode", havingValue = "STUB", matchIfMissing = true)
public class StubChatProvider implements ChatProvider {

    private final CannedReplies canned;

    public StubChatProvider(CannedReplies canned) {
        this.canned = canned;
    }

    @Override
    public String reply(String message, String language, List<Turn> history) {
        return canned.reply(message, language);
    }
}
