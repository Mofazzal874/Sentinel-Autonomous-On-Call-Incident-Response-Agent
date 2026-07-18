package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.config.AgentProperties;
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

    public RedisModelCallBudget(StringRedisTemplate redis, AgentProperties properties) {
        this.redis = redis;
        this.maximumCalls = properties.maxModelCalls();
        this.windowMillis = properties.modelCallWindow().toMillis();
    }

    @Override
    public void acquire(UUID incidentId, String role) {
        if (incidentId == null || role == null || role.isBlank()) {
            throw new IllegalArgumentException("incidentId and role are required");
        }
        Long result = redis.execute(ACQUIRE_SCRIPT, List.of(KEY_PREFIX + incidentId),
                Integer.toString(maximumCalls), Long.toString(windowMillis));
        if (result == null || result == 0L) {
            throw new ModelCallBudgetExceededException(
                    "Model call budget exhausted for incident " + incidentId + " before role " + role);
        }
    }
}
