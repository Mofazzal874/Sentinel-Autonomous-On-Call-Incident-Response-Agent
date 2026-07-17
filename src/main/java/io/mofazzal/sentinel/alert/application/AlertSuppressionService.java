package io.mofazzal.sentinel.alert.application;

import io.mofazzal.sentinel.alert.config.AlertProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class AlertSuppressionService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 200;
    private static final String KEY_PREFIX = "sentinel:{alerts}:";

    private static final DefaultRedisScript<Long> CLAIM_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then
                return 0
            end
            redis.call('SET', KEYS[1], '1', 'PX', ARGV[1])
            if KEYS[2] ~= KEYS[1] then
                redis.call('SET', KEYS[2], '1', 'PX', ARGV[1])
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            local ttl = redis.call('PTTL', KEYS[2])
            if ttl > 0 then
                redis.call('PEXPIRE', KEYS[1], ttl)
            else
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            return redis.call('DEL', KEYS[1], KEYS[2], KEYS[3])
            """, Long.class);

    private final StringRedisTemplate redis;
    private final long windowMillis;

    public AlertSuppressionService(StringRedisTemplate redis, AlertProperties properties) {
        this.redis = redis;
        this.windowMillis = properties.dedupWindow().toMillis();
    }

    public SuppressionDecision claim(String fingerprint, String idempotencyKey) {
        String seenKey = seenKey(fingerprint);
        String clientKey = clientKey(idempotencyKey, seenKey);
        Long claimed = redis.execute(
                CLAIM_SCRIPT,
                List.of(seenKey, clientKey),
                Long.toString(windowMillis)
        );
        if (Long.valueOf(1L).equals(claimed)) {
            return SuppressionDecision.first();
        }

        Long count = redis.execute(
                INCREMENT_SCRIPT,
                List.of(countKey(fingerprint), seenKey),
                Long.toString(windowMillis)
        );
        return SuppressionDecision.duplicate(count == null ? 0L : count);
    }

    public long suppressedCount(String fingerprint) {
        String value = redis.opsForValue().get(countKey(fingerprint));
        return value == null ? 0L : Long.parseLong(value);
    }

    public void release(String fingerprint, String idempotencyKey) {
        String seenKey = seenKey(fingerprint);
        redis.execute(
                RELEASE_SCRIPT,
                List.of(seenKey, clientKey(idempotencyKey, seenKey), countKey(fingerprint))
        );
    }

    private static String seenKey(String fingerprint) {
        return KEY_PREFIX + "seen:" + fingerprint;
    }

    private static String countKey(String fingerprint) {
        return KEY_PREFIX + "suppressed:" + fingerprint;
    }

    private static String clientKey(String idempotencyKey, String fallback) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return fallback;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException("Idempotency-Key must not exceed 200 characters");
        }
        return KEY_PREFIX + "client:" + sha256(normalized);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }

    public record SuppressionDecision(boolean firstOccurrence, long suppressedCount) {

        static SuppressionDecision first() {
            return new SuppressionDecision(true, 0);
        }

        static SuppressionDecision duplicate(long count) {
            return new SuppressionDecision(false, count);
        }

        public static SuppressionDecision bypassed() {
            return new SuppressionDecision(true, 0);
        }
    }
}
