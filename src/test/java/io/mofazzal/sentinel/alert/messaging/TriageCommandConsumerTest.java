package io.mofazzal.sentinel.alert.messaging;

import com.rabbitmq.client.Channel;
import io.mofazzal.sentinel.alert.api.AlertPayload;
import io.mofazzal.sentinel.alert.config.AlertMessagingProperties;
import io.mofazzal.sentinel.alert.config.AlertMessagingTopology;
import io.mofazzal.sentinel.incident.application.IncidentCreationService;
import io.mofazzal.sentinel.agent.application.IncidentAgentDispatcher;
import io.mofazzal.sentinel.incident.domain.IncidentSeverity;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.TransientDataAccessResourceException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TriageCommandConsumerTest {

    private final IncidentCreationService incidents = mock(IncidentCreationService.class);
    private final TriageCommandPublisher publisher = mock(TriageCommandPublisher.class);
    private final IncidentAgentDispatcher dispatcher = mock(IncidentAgentDispatcher.class);
    private final Channel channel = mock(Channel.class);
    private final TriageCommandConsumer consumer = new TriageCommandConsumer(
            incidents, dispatcher, publisher,
            new AlertMessagingProperties(Duration.ofSeconds(1), 3, Duration.ofSeconds(2)));

    @Test
    void acknowledgesOnlyAfterDatabaseOperationReturns() throws Exception {
        TriageCommand command = command();
        Message message = message(41L, 0);

        consumer.consume(command, message, channel);

        var ordered = org.mockito.Mockito.inOrder(incidents, dispatcher, channel);
        ordered.verify(incidents).createIfAbsent(command);
        ordered.verify(dispatcher).dispatchIfEnabled(command);
        ordered.verify(channel).basicAck(41L, false);
    }

    @Test
    void republishesTransientFailureThenAcknowledgesOriginal() throws Exception {
        TriageCommand command = command();
        Message message = message(42L, 0);
        doThrow(new TransientDataAccessResourceException("database unavailable"))
                .when(incidents).createIfAbsent(command);

        consumer.consume(command, message, channel);

        var ordered = org.mockito.Mockito.inOrder(publisher, channel);
        ordered.verify(publisher).publishRetry(command, 1);
        ordered.verify(channel).basicAck(42L, false);
        verify(channel, never()).basicNack(42L, false, false);
    }

    @Test
    void deadLettersAfterBoundedNumberOfAttempts() throws Exception {
        TriageCommand command = command();
        Message message = message(43L, 2);
        doThrow(new TransientDataAccessResourceException("still unavailable"))
                .when(incidents).createIfAbsent(command);

        consumer.consume(command, message, channel);

        verify(channel).basicNack(43L, false, false);
        verify(publisher, never()).publishRetry(command, 3);
    }

    @Test
    void deadLettersPermanentFailureWithoutRetry() throws Exception {
        TriageCommand command = command();
        Message message = message(44L, 0);
        doThrow(new IllegalArgumentException("poison command"))
                .when(incidents).createIfAbsent(command);

        consumer.consume(command, message, channel);

        verify(channel).basicNack(44L, false, false);
        verify(publisher, never()).publishRetry(command, 1);
    }

    @Test
    void deadLettersOriginalIfConfirmedRetryPublishFails() throws Exception {
        TriageCommand command = command();
        Message message = message(45L, 0);
        doThrow(new TransientDataAccessResourceException("database unavailable"))
                .when(incidents).createIfAbsent(command);
        doThrow(new AlertPublishException("broker did not confirm retry"))
                .when(publisher).publishRetry(command, 1);

        consumer.consume(command, message, channel);

        verify(channel).basicNack(45L, false, false);
        verify(channel, never()).basicAck(45L, false);
    }

    private static Message message(long deliveryTag, int retryCount) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        properties.setHeader(AlertMessagingTopology.RETRY_COUNT_HEADER, retryCount);
        return new Message(new byte[0], properties);
    }

    private static TriageCommand command() {
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        AlertPayload payload = new AlertPayload(
                "payments-api", "HighErrorRate", IncidentSeverity.SEV2,
                now.minusSeconds(30), "Error rate is above threshold",
                Map.of("environment", "production"));
        return TriageCommand.create("fingerprint-1", payload, now);
    }
}
