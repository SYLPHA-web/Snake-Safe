package com.snakesafe.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** One start-up line that says how chat and photo identification are set up (the key itself is never printed). */
@Component
public class AiStartupLog implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiStartupLog.class);

    private final AppProperties props;

    public AiStartupLog(AppProperties props) {
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean key = props.ai().apiKey() != null && !props.ai().apiKey().isBlank();
        log.info("Chat mode: {} | Identify mode: {} | OTP mode: {} | AI key present: {} | AI model: {} | profile photo storage: {}",
                props.chatMode(), props.identifyMode(), props.otpMode(), key ? "yes" : "no", props.ai().model(), props.photoStorage());
        boolean wantsAi = "AI".equalsIgnoreCase(props.chatMode()) || "AI".equalsIgnoreCase(props.identifyMode());
        if (wantsAi && !key) {
            log.warn("CHAT_MODE or IDENTIFY_MODE is AI but AI_API_KEY is empty: those features will answer with the built-in fallback");
        }
        if ("DEMO".equalsIgnoreCase(props.otpMode()) && props.trustProxy()) {
            log.warn("OTP_MODE=DEMO: anyone can sign in to any phone number with code 123456. Fine for a showcase, not for real users.");
        }
    }
}
