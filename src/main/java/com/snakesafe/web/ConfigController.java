package com.snakesafe.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.snakesafe.config.AppProperties;

@RestController
public class ConfigController {

    private final AppProperties props;

    public ConfigController(AppProperties props) {
        this.props = props;
    }

    public record PublicConfig(String name, String logo, String tagline, String emergencyNumber,
            String poisonHelpline, int verifiedHours, String defaultLanguage, String otpMode,
            int cancelWindowSeconds, int noReplyMinutes, String reminder) {
    }

    @GetMapping("/api/config")
    public PublicConfig config() {
        return new PublicConfig(props.name(), props.logo(), props.tagline(), props.emergencyNumber(),
                props.poisonHelpline(), props.verifiedHours(), props.defaultLanguage(), props.otpMode(),
                props.cancelWindowSeconds(), props.noReplyMinutes(), "Treat every bite as an emergency.");
    }
}
