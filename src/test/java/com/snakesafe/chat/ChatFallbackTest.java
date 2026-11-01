package com.snakesafe.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import com.snakesafe.web.ApiException;

import org.springframework.http.HttpStatus;

/** The AI assistant is made to fail: people must still get a safe answer, and a bite still gets "Call 108 now". */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:chatfallback-${random.uuid};DB_CLOSE_DELAY=-1",
        "app.scheduler-enabled=false",
        "app.photo-dir=target/test-photos",
})
@Import(ChatFallbackTest.Broken.class)
class ChatFallbackTest {

    @TestConfiguration
    static class Broken {
        @Bean
        @Primary
        ChatProvider brokenProvider() {
            return (message, language, history) -> {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_ERROR", "simulated outage");
            };
        }
    }

    @Autowired
    ChatService chat;

    @Test
    void anOutageGivesAFallbackAnswerNotAnError() {
        var a = chat.chat("How do I find a hospital?", "en", List.of(), "198.51.100.1");
        assertThat(a.fallback()).isTrue();
        assertThat(a.reply()).containsIgnoringCase("Hospitals");
    }

    @Test
    void aBiteDuringAnOutageStillStartsWithCall108Now() {
        var en = chat.chat("My mother was bitten by a snake", "en", List.of(), "198.51.100.2");
        assertThat(en.fallback()).isTrue();
        assertThat(en.reply()).startsWith("Call 108 now");
        var kn = chat.chat("ಹಾವು ಕಚ್ಚಿದೆ", "kn", List.of(), "198.51.100.3");
        assertThat(kn.reply()).startsWith("Call 108 now");
    }
}
