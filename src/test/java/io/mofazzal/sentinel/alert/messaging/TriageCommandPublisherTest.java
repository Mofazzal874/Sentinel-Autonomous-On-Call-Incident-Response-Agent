package io.mofazzal.sentinel.alert.messaging;

import io.mofazzal.sentinel.alert.api.AlertPayload;
import io.mofazzal.sentinel.alert.config.AlertMessagingProperties;
import io.mofazzal.sentinel.incident.domain.IncidentSeverity;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class TriageCommandPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final TriageCommandPublisher publisher = new TriageCommandPublisher(
            rabbitTemplate, new AlertMessagingProperties(
                    Duration.ofSeconds(1), 3, Duration.ofSeconds(2)));

    @Test
    void acceptsPositiveBrokerConfirmation() {
        TriageCommand command = command();
        completeConfirmation(command, new CorrelationData.Confirm(true, null));

        publisher.publish(command);
    }

    @Test
    void rejectsNegativeBrokerConfirmation() {
        TriageCommand command = command();
        completeConfirmation(command, new CorrelationData.Confirm(false, "broker nack"));

        assertThatThrownBy(() -> publisher.publish(command))
                .isInstanceOf(AlertPublishException.class)
                .hasMessageContaining("broker nack");
    }

    private void completeConfirmation(TriageCommand command, CorrelationData.Confirm confirm) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(confirm);
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq("alerts.exchange"),
                eq("incident.alert"),
                eq(command),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );
    }

    private static TriageCommand command() {
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        AlertPayload payload = new AlertPayload(
                "payments-api",
                "HighErrorRate",
                IncidentSeverity.SEV2,
                now.minusSeconds(30),
                "Error rate is above threshold",
                Map.of("environment", "production")
        );
        return TriageCommand.create("fingerprint-1", payload, now);
    }
}
