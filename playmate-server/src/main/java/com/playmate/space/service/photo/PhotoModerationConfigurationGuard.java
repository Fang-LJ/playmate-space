package com.playmate.space.service.photo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.Arrays;

/** Makes an accidental non-local MOCK moderation configuration impossible to miss in logs. */
@Component
public class PhotoModerationConfigurationGuard {
    private static final Logger log = LoggerFactory.getLogger(PhotoModerationConfigurationGuard.class);
    @Bean
    ApplicationRunner photoModerationProviderGuard(PhotoProperties properties, Environment environment) {
        return args -> {
            boolean localOrTest = Arrays.stream(environment.getActiveProfiles()).anyMatch(profile -> "local".equals(profile) || "test".equals(profile));
            if ("MOCK".equalsIgnoreCase(properties.getModerationProvider()) && !localOrTest) {
                log.error("PHOTO MODERATION IS CONFIGURED AS MOCK OUTSIDE local/test. Production must configure a real WeChat moderation provider before enabling photo wall.");
            }
        };
    }
}
