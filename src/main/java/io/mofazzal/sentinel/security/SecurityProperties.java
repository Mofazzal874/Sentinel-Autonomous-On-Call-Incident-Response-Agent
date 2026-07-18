package io.mofazzal.sentinel.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.Base64;

@ConfigurationProperties("sentinel.security")
public record SecurityProperties(
        String jwtSecret,
        String jwtIssuer,
        String jwtAudience,
        String webhookSecret,
        Duration webhookTolerance,
        DataSize maxWebhookBodySize
) {
    public SecurityProperties {
        requireBase64Secret(jwtSecret, "sentinel.security.jwt-secret");
        requireText(jwtIssuer, "sentinel.security.jwt-issuer");
        requireText(jwtAudience, "sentinel.security.jwt-audience");
        requireBase64Secret(webhookSecret, "sentinel.security.webhook-secret");
        if (webhookTolerance == null || webhookTolerance.isZero() || webhookTolerance.isNegative()) {
            throw new IllegalArgumentException("sentinel.security.webhook-tolerance must be positive");
        }
        if (maxWebhookBodySize == null || maxWebhookBodySize.toBytes() < 1) {
            throw new IllegalArgumentException("sentinel.security.max-webhook-body-size must be positive");
        }
    }

    public byte[] jwtSecretBytes() {
        return Base64.getDecoder().decode(jwtSecret);
    }

    public byte[] webhookSecretBytes() {
        return Base64.getDecoder().decode(webhookSecret);
    }

    private static void requireBase64Secret(String value, String property) {
        requireText(value, property);
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(property + " must be valid Base64", exception);
        }
        if (decoded.length < 32) {
            throw new IllegalArgumentException(property + " must contain at least 256 bits");
        }
    }

    private static void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(property + " must not be blank");
        }
    }
}
