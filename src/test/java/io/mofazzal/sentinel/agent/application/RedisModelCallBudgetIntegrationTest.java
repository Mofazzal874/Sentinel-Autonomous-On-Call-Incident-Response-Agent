package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.config.AgentProperties;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RedisModelCallBudgetIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4.9-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private static RedisModelCallBudget budget;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        budget = new RedisModelCallBudget(redis,
                new AgentProperties(3, 3, Duration.ofMinutes(10), Duration.ofMinutes(10)));
    }

    @AfterAll
    static void close() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void atomicallyStopsCallsAtPerIncidentLimitAndSetsExpiry() {
        UUID incidentId = UUID.randomUUID();
        budget.acquire(incidentId, "router");
        budget.acquire(incidentId, "generator");
        budget.acquire(incidentId, "evaluator");

        assertThatThrownBy(() -> budget.acquire(incidentId, "generator"))
                .isInstanceOf(ModelCallBudgetExceededException.class)
                .hasMessageContaining("budget exhausted");
        assertThat(redis.getExpire("sentinel:{agent-calls}:" + incidentId))
                .isPositive()
                .isLessThanOrEqualTo(Duration.ofMinutes(10).toSeconds());

        budget.acquire(UUID.randomUUID(), "router");
    }
}
