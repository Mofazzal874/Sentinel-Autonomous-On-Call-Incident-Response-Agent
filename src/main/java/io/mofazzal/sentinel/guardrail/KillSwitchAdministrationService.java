package io.mofazzal.sentinel.guardrail;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;

@Service
public class KillSwitchAdministrationService {

    private final SafetyControlRepository controls;
    private final StringRedisTemplate redis;
    private final Clock clock;

    public KillSwitchAdministrationService(SafetyControlRepository controls,
                                           StringRedisTemplate redis,
                                           Clock clock) {
        this.controls = controls;
        this.redis = redis;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public boolean setEngaged(boolean engaged, String actor) {
        SafetyControl control = controls.findForUpdate()
                .orElseThrow(() -> new IllegalStateException("kill switch row is missing"));
        control.setEngaged(engaged, actor, clock.instant());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    if (engaged) {
                        redis.opsForValue().set(DatabaseBackedKillSwitch.REDIS_KEY, "true");
                    } else {
                        redis.delete(DatabaseBackedKillSwitch.REDIS_KEY);
                    }
                } catch (RuntimeException redisUnavailable) {
                    // PostgreSQL is authoritative; the next gate still reads it.
                }
            }
        });
        return engaged;
    }
}
