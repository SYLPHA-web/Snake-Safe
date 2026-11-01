package com.snakesafe.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class AlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertScheduler.class);

    private final AlertService alerts;

    public AlertScheduler(AlertService alerts) {
        this.alerts = alerts;
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        try {
            alerts.processTimeouts();
        } catch (RuntimeException e) {
            // e.g. a staff reply landed at the same moment: the next tick simply tries again
            log.warn("Alert timeout check will retry: {}", e.getClass().getSimpleName());
        }
    }
}
