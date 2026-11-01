package com.snakesafe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// No user-name/password login exists (OTP and JWT only), so skip Spring's generated default user
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class SnakeSafeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SnakeSafeApplication.class, args);
    }
}
