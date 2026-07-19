package io.mofazzal.sentinel.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Component
@Profile("demo")
public class DemoScenarioEvidenceSeeder {

    private final JdbcTemplate jdbc;

    public DemoScenarioEvidenceSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void seed(UUID runId, ScenarioTemplate template, Instant firedAt) {
        if (template.getScenarioType() == ScenarioTemplate.ScenarioType.BAD_DEPLOY) {
            jdbc.update("""
                    INSERT INTO deployment (id, service_id, version, git_sha, deployed_at, deployed_by, status)
                    VALUES (?, ?, ?, ?, ?, 'demo-release-bot', 'SUCCEEDED') ON CONFLICT DO NOTHING
                    """, stable(runId, "deployment"), template.getService().getId(),
                    "live-" + runId.toString().substring(0, 8), "live-" + runId,
                    Timestamp.from(firedAt.minusSeconds(300)));
        }

        jdbc.update("""
                INSERT INTO metric_sample (id, service_id, metric_name, value, recorded_at)
                SELECT md5(? || ':metric:' || metric.name || ':' || sample.n)::uuid, ?, metric.name,
                       CASE metric.name
                           WHEN 'error_rate' THEN CASE WHEN sample.n < 9 THEN 0.01 ELSE
                               CASE ? WHEN 'BAD_DEPLOY' THEN 0.19 WHEN 'DEPENDENCY_TIMEOUT' THEN 0.12 ELSE 0.04 END END
                           WHEN 'latency_p99_ms' THEN CASE WHEN sample.n < 9 THEN 220 ELSE
                               CASE ? WHEN 'CAPACITY_SATURATION' THEN 2100 WHEN 'DEPENDENCY_TIMEOUT' THEN 1750 ELSE 980 END END
                           WHEN 'cpu_saturation' THEN CASE WHEN sample.n < 9 THEN 0.42 ELSE
                               CASE ? WHEN 'CAPACITY_SATURATION' THEN 0.94 ELSE 0.61 END END
                           WHEN 'queue_depth' THEN CASE WHEN sample.n < 9 THEN 12 ELSE
                               CASE ? WHEN 'CAPACITY_SATURATION' THEN 380 ELSE 75 END END
                           ELSE CASE WHEN sample.n < 9 THEN 0.999 ELSE 0.965 END
                       END,
                       ?::timestamptz + (sample.n - 12) * interval '1 minute'
                FROM (VALUES ('error_rate'), ('latency_p99_ms'), ('cpu_saturation'),
                             ('queue_depth'), ('availability')) metric(name)
                CROSS JOIN generate_series(1, 12) sample(n)
                ON CONFLICT DO NOTHING
                """, runId.toString(), template.getService().getId(), template.getScenarioType().name(),
                template.getScenarioType().name(), template.getScenarioType().name(),
                template.getScenarioType().name(), Timestamp.from(firedAt));

        jdbc.update("""
                INSERT INTO log_event (id, service_id, level, message, occurred_at, trace_id)
                SELECT md5(? || ':log:' || sample.n)::uuid, ?,
                       CASE WHEN sample.n >= 6 THEN 'ERROR' ELSE 'WARN' END,
                       CASE ?
                           WHEN 'BAD_DEPLOY' THEN 'request failed after release; circuit breaker opened'
                           WHEN 'DEPENDENCY_TIMEOUT' THEN 'required upstream dependency timed out'
                           WHEN 'CAPACITY_SATURATION' THEN 'worker queue saturated; request deadline exceeded'
                           ELSE 'cache version lagged behind source-of-truth revision'
                       END,
                       ?::timestamptz + (sample.n - 8) * interval '40 seconds',
                       'live-' || substring(? from 1 for 8) || '-' || sample.n
                FROM generate_series(1, 8) sample(n)
                ON CONFLICT DO NOTHING
                """, runId.toString(), template.getService().getId(), template.getScenarioType().name(),
                Timestamp.from(firedAt), runId.toString());
    }

    private static UUID stable(UUID runId, String suffix) {
        return UUID.nameUUIDFromBytes((runId + ":" + suffix).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
