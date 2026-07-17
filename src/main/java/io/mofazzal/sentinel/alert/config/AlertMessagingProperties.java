package io.mofazzal.sentinel.alert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("sentinel.messaging")
public record AlertMessagingProperties(
        Duration publisherConfirmTimeout,
        int maxConsumerAttempts,
        Duration retryDelay
) {

    public AlertMessagingProperties {
        if (publisherConfirmTimeout == null
                || publisherConfirmTimeout.isZero()
                || publisherConfirmTimeout.isNegative()) {
            throw new IllegalArgumentException("sentinel.messaging.publisher-confirm-timeout must be positive");
        }
        if (maxConsumerAttempts < 1) {
            throw new IllegalArgumentException("sentinel.messaging.max-consumer-attempts must be at least 1");
        }
        if (retryDelay == null || retryDelay.isZero() || retryDelay.isNegative()) {
            throw new IllegalArgumentException("sentinel.messaging.retry-delay must be positive");
        }
    }
}
