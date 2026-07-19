package io.mofazzal.sentinel.alert.messaging;

import com.rabbitmq.client.Channel;
import io.mofazzal.sentinel.alert.config.AlertMessagingProperties;
import io.mofazzal.sentinel.alert.config.AlertMessagingTopology;
import io.mofazzal.sentinel.incident.application.IncidentCreationService;
import io.mofazzal.sentinel.agent.application.AgentDispatchInProgressException;
import io.mofazzal.sentinel.agent.application.IncidentAgentDispatcher;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import java.io.IOException;

@Component
public class TriageCommandConsumer {

    private final IncidentCreationService incidentCreationService;
    private final IncidentAgentDispatcher agentDispatcher;
    private final TriageCommandPublisher publisher;
    private final int maxAttempts;
    private final ObjectProvider<TriageCommandLifecycleListener> lifecycleListeners;

    public TriageCommandConsumer(IncidentCreationService incidentCreationService,
                                 IncidentAgentDispatcher agentDispatcher,
                                 TriageCommandPublisher publisher,
                                 AlertMessagingProperties properties,
                                 ObjectProvider<TriageCommandLifecycleListener> lifecycleListeners) {
        this.incidentCreationService = incidentCreationService;
        this.agentDispatcher = agentDispatcher;
        this.publisher = publisher;
        this.maxAttempts = properties.maxConsumerAttempts();
        this.lifecycleListeners = lifecycleListeners;
    }

    @RabbitListener(queues = AlertMessagingTopology.TRIAGE_QUEUE)
    public void consume(TriageCommand command, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            incidentCreationService.createIfAbsent(command);
            agentDispatcher.dispatchIfEnabled(command);
            lifecycleListeners.orderedStream().forEach(listener -> listener.completed(command));
            channel.basicAck(deliveryTag, false);
        } catch (TransientDataAccessException | CannotCreateTransactionException
                 | AgentDispatchInProgressException exception) {
            retryOrDeadLetter(command, message, channel, deliveryTag);
        } catch (RuntimeException exception) {
            notifyFailed(command, exception);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private void retryOrDeadLetter(TriageCommand command, Message message, Channel channel,
                                   long deliveryTag) throws IOException {
        int retries = retryCount(message);
        if (retries + 1 >= maxAttempts) {
            lifecycleListeners.orderedStream().forEach(listener ->
                    listener.failed(command, "Triage exhausted its bounded retry policy"));
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        try {
            publisher.publishRetry(command, retries + 1);
            channel.basicAck(deliveryTag, false);
        } catch (AlertPublishException exception) {
            notifyFailed(command, exception);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private void notifyFailed(TriageCommand command, RuntimeException failure) {
        String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        lifecycleListeners.orderedStream().forEach(listener -> listener.failed(command, reason));
    }

    private int retryCount(Message message) {
        Object value = message.getMessageProperties().getHeaders()
                .get(AlertMessagingTopology.RETRY_COUNT_HEADER);
        return value instanceof Number number ? number.intValue() : 0;
    }
}
