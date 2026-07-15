package com.scb.trade.lcdocchecker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Provides an injectable {@link Clock} so date validation is deterministic and testable
 * (no hidden {@code LocalDate.now()} / system-timezone dependency). Bean name is explicit so
 * other clocks may coexist without type-resolution ambiguity.
 */
@Configuration
public class ValidationConfig {

    @Bean
    public Clock validationClock(ValidationProperties props) {
        return Clock.system(ZoneId.of(props.zoneId()));
    }
}
