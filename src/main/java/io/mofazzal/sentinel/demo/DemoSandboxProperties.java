package io.mofazzal.sentinel.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("sentinel.demo.sandbox")
public record DemoSandboxProperties(int perClientPerMinute, int globalConcurrency,
                                    int dailyAccepted, Duration leaseTimeout) {
    public DemoSandboxProperties {
        if (perClientPerMinute < 1 || globalConcurrency < 1 || dailyAccepted < 1) {
            throw new IllegalArgumentException("demo sandbox limits must be positive");
        }
        if (leaseTimeout == null || leaseTimeout.isZero() || leaseTimeout.isNegative()) {
            throw new IllegalArgumentException("demo sandbox lease timeout must be positive");
        }
    }
}
