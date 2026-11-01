package com.snakesafe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String name,
        String logo,
        String tagline,
        String emergencyNumber,
        String poisonHelpline,
        int verifiedHours,
        String defaultLanguage,
        String otpMode,
        String identifyMode,
        String chatMode,
        int cancelWindowSeconds,
        int noReplyMinutes,
        boolean logChats,
        boolean seedSampleData,
        String timezone,
        String photoStorage,
        String photoDir,
        boolean schedulerEnabled,
        String jwtSecret,
        int jwtHours,
        Ai ai,
        Supabase supabase,
        Limits limits,
        boolean trustProxy,
        boolean seedReferenceData,
        String adminUsername,
        String adminPassword,
        String seedStaffPassword) {

    public record Ai(String apiKey, String model, String baseUrl) {
    }

    public record Supabase(String url, String serviceKey, String bucket) {
    }

    public record Limits(int chatMaxMessageLength, int chatPerMinute, int identifyPerMinute) {
    }

    public boolean demoOtp() {
        return "DEMO".equalsIgnoreCase(otpMode);
    }
}
