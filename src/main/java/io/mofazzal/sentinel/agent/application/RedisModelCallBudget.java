package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.config.AgentProperties;
import io.mofazzal.sentinel.observability.SentinelMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class RedisModelCallBudget implements ModelCallBudget {

    private static final String KEY_PREFIX = "sentinel:{agent-calls}:";
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            if count > tonumber(ARGV[1]) then
                return 0
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int maximumCalls;
    private final long windowMillis;
    private final SentinelMetrics metrics;

    public RedisModelCallBudget(StringRedisTemplate redis, AgentProperties properties) {
        this(redis, properties, null);
    }

    @Autowired
    public RedisModelCallBudget(StringRedisTemplate redis,
                                AgentProperties properties,
                                SentinelMetrics metrics) {
        this.redis = redis;
        this.maximumCalls = properties.maxModelCalls();
        this.windowMillis = properties.modelCallWindow().toMillis();
        this.metrics = metrics;
    }

    @Override
    public void acquire(UUID incidentId, String role) {
        if (incidentId == null || role == null || role.isBlank()) {
            throw new IllegalArgumentException("incidentId and role are required");
        }
        if (metrics != null) {
            metrics.recordModelCall(role);
        }
        Long result = redis.execute(ACQUIRE_SCRIPT, List.of(KEY_PREFIX + incidentId),
                Integer.toString(maximumCalls), Long.toString(windowMillis));
        if (result == null || result == 0L) {
            throw new ModelCallBudgetExceededException(
                    "Model call budget exhausted for incident " + incidentId + " before role " + role);
        }
    }
}
