package io.mofazzal.sentinel.alert.application;

import io.mofazzal.sentinel.alert.config.AlertProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class AlertSuppressionServiceIntegrationTest {

    private static final Duration WINDOW = Duration.ofMinutes(10);

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4.9-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private static AlertSuppressionService suppressionService;

    @BeforeAll
    static void connectToRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        suppressionService = new AlertSuppressionService(redis, new AlertProperties(WINDOW));
    }

    @AfterAll
    static void closeRedisConnection() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void oneBurstProducesOneClaimAndFortyNineSuppressionCounts() {
        String fingerprint = "burst-fingerprint";

        assertThat(suppressionService.claim(fingerprint, null).firstOccurrence()).isTrue();
        for (int duplicate = 1; duplicate <= 49; duplicate++) {
            AlertSuppressionService.SuppressionDecision decision =
                    suppressionService.claim(fingerprint, null);
            assertThat(decision.firstOccurrence()).isFalse();
            assertThat(decision.suppressedCount()).isEqualTo(duplicate);
        }

        assertThat(suppressionService.suppressedCount(fingerprint)).isEqualTo(49);
        assertThat(redis.getExpire("sentinel:{alerts}:seen:" + fingerprint))
                .isPositive()
                .isLessThanOrEqualTo(WINDOW.toSeconds());
        assertThat(redis.getExpire("sentinel:{alerts}:suppressed:" + fingerprint))
                .isPositive()
                .isLessThanOrEqualTo(WINDOW.toSeconds());
    }

    @Test
    void reusedClientKeySuppressesEvenWhenSemanticFingerprintChanges() {
        assertThat(suppressionService.claim("semantic-a", "client-request-42").firstOccurrence()).isTrue();

        AlertSuppressionService.SuppressionDecision repeatedClient =
                suppressionService.claim("semantic-b", "client-request-42");

        assertThat(repeatedClient.firstOccurrence()).isFalse();
        assertThat(repeatedClient.suppressedCount()).isOne();
    }

    @Test
    void rejectsUnboundedClientKeyBeforeWritingToRedis() {
        assertThatThrownBy(() -> suppressionService.claim("semantic-c", "x".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200");
    }
}
