package com.snakesafe.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Placeholder until a real SMS provider exists: add another SmsSender and mark it @Primary. */
@Component
public class StubSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(StubSmsSender.class);

    @Override
    public void send(String phone, String text) {
        log.info("[SMS stub] message for ***{} not sent (no SMS provider configured)",
                phone.substring(Math.max(0, phone.length() - 4)));
    }
}
