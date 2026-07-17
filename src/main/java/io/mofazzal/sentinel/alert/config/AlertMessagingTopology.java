package io.mofazzal.sentinel.alert.config;

public final class AlertMessagingTopology {

    public static final String ALERTS_EXCHANGE = "alerts.exchange";
    public static final String TRIAGE_QUEUE = "triage.queue";
    public static final String TRIAGE_ROUTING_KEY = "incident.alert";
    public static final String RETRY_EXCHANGE = "alerts.retry.exchange";
    public static final String RETRY_QUEUE = "triage.retry.queue";
    public static final String RETRY_ROUTING_KEY = "triage.retry";
    public static final String RETRY_COUNT_HEADER = "x-sentinel-retry-count";
    public static final String DEAD_LETTER_EXCHANGE = "alerts.dlx";
    public static final String DEAD_LETTER_QUEUE = "triage.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY = "triage.dead";

    private AlertMessagingTopology() {
    }
}
