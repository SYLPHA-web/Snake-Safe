package com.snakesafe.chat;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
public class ChatController {

    public record HistoryItem(@Pattern(regexp = "user|assistant") String role, @Size(max = 2000) String content) {
    }

    public record ChatRequest(@NotBlank @Size(max = 2000) String message,
            @Pattern(regexp = "en|kn", message = "must be en or kn") String language,
            @Size(max = 30) List<@Valid HistoryItem> history) {
    }

    public record ChatResponse(String reply) {
    }

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping("/api/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest req, HttpServletRequest http) {
        List<ChatService.Turn> history = req.history() == null ? List.of()
                : req.history().stream().map(h -> new ChatService.Turn(h.role(), h.content())).toList();
        return new ChatResponse(chat.chat(req.message(), req.language(), history, http.getRemoteAddr()));
    }
}
