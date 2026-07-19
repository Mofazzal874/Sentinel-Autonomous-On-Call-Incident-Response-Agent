package io.mofazzal.sentinel.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Component
@Profile("demo")
public class DemoSandboxRateLimiter {

    private static final String PREFIX = "sentinel:{demo-sandbox}:";
    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            local minute = redis.call('INCR', KEYS[1])
            if minute == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[4]) end
            if minute > tonumber(ARGV[1]) then return 1 end
            local daily = tonumber(redis.call('GET', KEYS[2]) or '0')
            if daily >= tonumber(ARGV[2]) then return 2 end
            local inflight = tonumber(redis.call('GET', KEYS[3]) or '0')
            if inflight >= tonumber(ARGV[3]) then return 3 end
            redis.call('INCR', KEYS[2])
            redis.call('PEXPIRE', KEYS[2], ARGV[5])
            redis.call('INCR', KEYS[3])
            redis.call('PEXPIRE', KEYS[3], ARGV[6])
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            if current <= 1 then return redis.call('DEL', KEYS[1]) end
            return redis.call('DECR', KEYS[1])
            """, Long.class);

    private final StringRedisTemplate redis;
    private final DemoSandboxProperties properties;
    private final Clock clock;

    public DemoSandboxRateLimiter(StringRedisTemplate redis, DemoSandboxProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    public void acquire(String clientHash) {
        try {
            Long result = redis.execute(ACQUIRE,
                    List.of(PREFIX + "client:" + clientHash,
                            PREFIX + "daily:" + LocalDate.now(clock.withZone(ZoneOffset.UTC)),
                            PREFIX + "inflight"),
                    Integer.toString(properties.perClientPerMinute()),
                    Integer.toString(properties.dailyAccepted()),
                    Integer.toString(properties.globalConcurrency()),
                    "60000", "90000000", Long.toString(properties.leaseTimeout().toMillis()));
            if (Long.valueOf(1).equals(result)) {
                throw new DemoSandboxLimitException("CLIENT_RATE_LIMIT", "Try again in one minute.");
            }
            if (Long.valueOf(2).equals(result)) {
                throw new DemoSandboxLimitException("DAILY_LIMIT", "The public sandbox has reached today's run limit.");
            }
            if (Long.valueOf(3).equals(result)) {
                throw new DemoSandboxLimitException("SANDBOX_BUSY",
                        "The public sandbox is at its concurrency limit. Try again shortly.");
            }
            if (!Long.valueOf(0).equals(result)) {
                throw new DemoSandboxLimitException("LIMITER_UNAVAILABLE", "The public sandbox limiter is unavailable.");
            }
        } catch (DataAccessException exception) {
            throw new DemoSandboxLimitException("LIMITER_UNAVAILABLE",
                    "The public sandbox is temporarily unavailable because its safety limiter cannot be verified.");
        }
    }

    public void release() {
        try {
            redis.execute(RELEASE, List.of(PREFIX + "inflight"));
        } catch (DataAccessException ignored) {
            // The lease TTL is the crash-safe fallback. A failed release never opens capacity early.
        }
    }
}
