package io.mofazzal.sentinel.alert.messaging;

import io.mofazzal.sentinel.alert.config.AlertMessagingProperties;
import io.mofazzal.sentinel.alert.config.AlertMessagingTopology;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class TriageCommandPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final long confirmTimeoutMillis;

    public TriageCommandPublisher(RabbitTemplate rabbitTemplate, AlertMessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeoutMillis = properties.publisherConfirmTimeout().toMillis();
    }

    public void publish(TriageCommand command) {
        publishConfirmed(command, AlertMessagingTopology.ALERTS_EXCHANGE,
                AlertMessagingTopology.TRIAGE_ROUTING_KEY, 0);
    }

    public void publishRetry(TriageCommand command, int retryCount) {
        publishConfirmed(command, AlertMessagingTopology.RETRY_EXCHANGE,
                AlertMessagingTopology.RETRY_ROUTING_KEY, retryCount);
    }

    private void publishConfirmed(TriageCommand command, String exchange, String routingKey, int retryCount) {
        CorrelationData correlation = new CorrelationData(command.fingerprint() + ":" + retryCount);
        rabbitTemplate.convertAndSend(
                exchange,
                routingKey,
                command,
                message -> {
                    message.getMessageProperties().setMessageId(command.fingerprint());
                    message.getMessageProperties().setTimestamp(Date.from(command.receivedAt()));
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setHeader("schemaVersion", command.schemaVersion());
                    message.getMessageProperties().setHeader(
                            AlertMessagingTopology.RETRY_COUNT_HEADER, retryCount);
                    return message;
                },
                correlation
        );

        CorrelationData.Confirm confirm;
        try {
            confirm = correlation.getFuture().get(confirmTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AlertPublishException("Interrupted while waiting for RabbitMQ confirmation", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AlertPublishException("RabbitMQ did not confirm the alert publish", exception);
        }

        if (!confirm.ack()) {
            throw new AlertPublishException("RabbitMQ rejected the alert publish: " + confirm.reason());
        }
        if (correlation.getReturned() != null) {
            throw new AlertPublishException(
                    "RabbitMQ returned the unroutable alert: " + correlation.getReturned().getReplyText());
        }
    }
}
