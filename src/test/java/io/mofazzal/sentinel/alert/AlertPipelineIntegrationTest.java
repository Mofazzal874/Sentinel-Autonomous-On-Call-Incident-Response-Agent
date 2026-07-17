package io.mofazzal.sentinel.alert;

import io.mofazzal.sentinel.alert.api.AlertAcknowledgement;
import io.mofazzal.sentinel.alert.api.AlertPayload;
import io.mofazzal.sentinel.alert.config.AlertMessagingTopology;
import io.mofazzal.sentinel.alert.messaging.TriageCommand;
import io.mofazzal.sentinel.alert.messaging.TriageCommandPublisher;
import io.mofazzal.sentinel.incident.domain.IncidentSeverity;
import io.mofazzal.sentinel.incident.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
class AlertPipelineIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "pgvector/pgvector:0.8.2-pg17-bookworm");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4.9-alpine")).withExposedPorts(6379);

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4.3.2-management-alpine"));

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
        registry.add("sentinel.messaging.retry-delay", () -> "100ms");
    }

    @Autowired
    private WebApplicationContext webContext;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private IncidentRepository incidents;

    @Autowired
    private TriageCommandPublisher publisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void createMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webContext).build();
    }

    @Test
    void fiftyIdenticalPostsQueueOnceSuppressFortyNineAndCreateOneIncident() throws Exception {
        String body = """
                {
                  "service": "payments-api",
                  "alertName": "Phase2BurstAlert",
                  "severity": "SEV2",
                  "firedAt": "2026-07-18T01:00:00Z",
                  "summary": "Error rate is above threshold",
                  "labels": {"environment": "phase2-integration"}
                }
                """;

        int queued = 0;
        int suppressed = 0;
        String fingerprint = null;
        for (int request = 0; request < 50; request++) {
            String response = mockMvc.perform(post("/api/v1/alerts")
                            .contentType(APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isAccepted())
                    .andReturn().getResponse().getContentAsString();
            AlertAcknowledgement acknowledgement = jsonMapper.readValue(
                    response, AlertAcknowledgement.class);
            fingerprint = acknowledgement.fingerprint();
            if (acknowledgement.status() == AlertAcknowledgement.Status.QUEUED) {
                queued++;
            } else {
                suppressed++;
            }
        }

        assertThat(queued).isOne();
        assertThat(suppressed).isEqualTo(49);
        String finalFingerprint = fingerprint;
        await(Duration.ofSeconds(10), () -> incidents.countByFingerprint(finalFingerprint) == 1);
        assertThat(incidents.countByFingerprint(fingerprint)).isOne();
    }

    @Test
    void redeliveryIsIdempotentAtThePostgresqlBoundary() {
        TriageCommand command = command(
                "pipeline-redelivery-fingerprint", "payments-api", "RedeliveryAlert");

        publisher.publish(command);
        publisher.publish(command);

        await(Duration.ofSeconds(10), () -> incidents.countByFingerprint(command.fingerprint()) == 1);
        assertThat(incidents.countByFingerprint(command.fingerprint())).isOne();
    }

    @Test
    void poisonCommandIsRejectedToDeadLetterQueueWithoutLooping() {
        TriageCommand poison = command(
                "pipeline-poison-fingerprint", "service-that-does-not-exist", "PoisonAlert");

        publisher.publish(poison);

        Message deadLetter = (Message) awaitValue(Duration.ofSeconds(10),
                () -> rabbitTemplate.receive(AlertMessagingTopology.DEAD_LETTER_QUEUE));
        assertThat(deadLetter.getMessageProperties().getHeaders()).containsKey("x-death");
        assertThat(rabbitTemplate.getMessageConverter().fromMessage(deadLetter)).isEqualTo(poison);
        assertThat(incidents.countByFingerprint(poison.fingerprint())).isZero();
    }

    private static TriageCommand command(String fingerprint, String service, String alertName) {
        Instant receivedAt = Instant.parse("2026-07-18T02:00:00Z");
        AlertPayload payload = new AlertPayload(
                service, alertName, IncidentSeverity.SEV2, receivedAt.minusSeconds(30),
                "Pipeline integration test", Map.of("environment", "test"));
        return TriageCommand.create(fingerprint, payload, receivedAt);
    }

    private static void await(Duration timeout, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleepBriefly();
        }
        throw new AssertionError("Condition was not met within " + timeout);
    }

    private static Object awaitValue(Duration timeout, java.util.function.Supplier<Object> supplier) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Object value = supplier.get();
            if (value != null) {
                return value;
            }
            sleepBriefly();
        }
        throw new AssertionError("Value was not available within " + timeout);
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while awaiting asynchronous delivery", exception);
        }
    }
}
