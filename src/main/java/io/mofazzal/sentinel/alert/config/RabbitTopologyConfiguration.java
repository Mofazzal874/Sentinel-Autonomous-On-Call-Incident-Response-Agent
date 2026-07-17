package io.mofazzal.sentinel.alert.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class RabbitTopologyConfiguration {

    @Bean
    TopicExchange alertsExchange() {
        return new TopicExchange(AlertMessagingTopology.ALERTS_EXCHANGE, true, false);
    }

    @Bean
    Queue triageQueue() {
        return QueueBuilder.durable(AlertMessagingTopology.TRIAGE_QUEUE)
                .deadLetterExchange(AlertMessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(AlertMessagingTopology.DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    DirectExchange retryExchange() {
        return new DirectExchange(AlertMessagingTopology.RETRY_EXCHANGE, true, false);
    }

    @Bean
    Queue triageRetryQueue(AlertMessagingProperties properties) {
        return QueueBuilder.durable(AlertMessagingTopology.RETRY_QUEUE)
                .ttl((int) properties.retryDelay().toMillis())
                .deadLetterExchange(AlertMessagingTopology.ALERTS_EXCHANGE)
                .deadLetterRoutingKey(AlertMessagingTopology.TRIAGE_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding triageRetryBinding(Queue triageRetryQueue, DirectExchange retryExchange) {
        return BindingBuilder.bind(triageRetryQueue)
                .to(retryExchange)
                .with(AlertMessagingTopology.RETRY_ROUTING_KEY);
    }

    @Bean
    Binding triageBinding(Queue triageQueue, TopicExchange alertsExchange) {
        return BindingBuilder.bind(triageQueue)
                .to(alertsExchange)
                .with("incident.*");
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(AlertMessagingTopology.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue triageDeadLetterQueue() {
        return QueueBuilder.durable(AlertMessagingTopology.DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding deadLetterBinding(Queue triageDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(triageDeadLetterQueue)
                .to(deadLetterExchange)
                .with(AlertMessagingTopology.DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    MessageConverter rabbitJsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(
                jsonMapper,
                "io.mofazzal.sentinel.alert.messaging",
                "io.mofazzal.sentinel.alert.api"
        );
    }

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }
}
