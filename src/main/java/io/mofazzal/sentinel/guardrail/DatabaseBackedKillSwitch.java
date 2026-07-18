package io.mofazzal.sentinel.guardrail;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DatabaseBackedKillSwitch implements KillSwitch {

    static final String REDIS_KEY = "sentinel:safety:kill-switch:engaged";
    private final StringRedisTemplate redis;
    private final SafetyControlRepository controls;

    public DatabaseBackedKillSwitch(StringRedisTemplate redis, SafetyControlRepository controls) {
        this.redis = redis;
        this.controls = controls;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEngaged() {
        try {
            if (Boolean.TRUE.equals(redis.hasKey(REDIS_KEY))) {
                return true;
            }
        } catch (RuntimeException redisUnavailable) {
            // PostgreSQL remains authoritative; failure there propagates and the gate closes.
        }
        return controls.findById((short) 1)
                .orElseThrow(() -> new IllegalStateException("kill switch row is missing"))
                .isEngaged();
    }
}
