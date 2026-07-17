package io.mofazzal.sentinel.alert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("sentinel.alerts")
public record AlertProperties(Duration dedupWindow) {

    public AlertProperties {
        if (dedupWindow == null || dedupWindow.isZero() || dedupWindow.isNegative()) {
            throw new IllegalArgumentException("sentinel.alerts.dedup-window must be positive");
        }
    }
}
