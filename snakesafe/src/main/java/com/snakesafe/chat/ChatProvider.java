package com.snakesafe.chat;

import java.util.List;

/** Produces a reply. STUB answers from fixed text; AI calls Claude. The safety checks run outside, in ChatService. */
public interface ChatProvider {

    record Turn(String role, String content) {
    }

    String reply(String message, String language, List<Turn> history);
}
