package io.mofazzal.sentinel.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
@Order(Ordered.LOWEST_PRECEDENCE)
public class DemoScenarioTemplateSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public DemoScenarioTemplateSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        jdbc.update("""
                INSERT INTO demo_scenario_template (
                    id, scenario_key, display_name, description, scenario_type, service_id,
                    severity, enabled, created_at, updated_at, archived_at, version
                ) VALUES
                    ('51000000-0000-0000-0000-000000000001', 'live-bad-deploy',
                     'Faulty payment release', 'Inject a release-correlated error and latency spike into the synthetic payments service.',
                     'BAD_DEPLOY', '20000000-0000-0000-0000-000000000001', 'SEV1', TRUE,
                     TIMESTAMPTZ '2026-07-19 00:00:00Z', TIMESTAMPTZ '2026-07-19 00:00:00Z', NULL, 0),
                    ('51000000-0000-0000-0000-000000000002', 'live-dependency-timeout',
                     'Checkout dependency timeout', 'Inject bounded upstream timeout evidence without inventing an infrastructure command.',
                     'DEPENDENCY_TIMEOUT', '20000000-0000-0000-0000-000000000002', 'SEV2', TRUE,
                     TIMESTAMPTZ '2026-07-19 00:00:00Z', TIMESTAMPTZ '2026-07-19 00:00:00Z', NULL, 0),
                    ('51000000-0000-0000-0000-000000000003', 'live-capacity-saturation',
                     'Catalog capacity saturation', 'Inject saturation, latency, and queue-depth evidence for a bounded scale-out investigation.',
                     'CAPACITY_SATURATION', '20000000-0000-0000-0000-000000000003', 'SEV2', TRUE,
                     TIMESTAMPTZ '2026-07-19 00:00:00Z', TIMESTAMPTZ '2026-07-19 00:00:00Z', NULL, 0),
                    ('51000000-0000-0000-0000-000000000004', 'live-cache-staleness',
                     'Stale search cache', 'Inject stale-read signals while the synthetic source-of-truth health remains normal.',
                     'CACHE_STALENESS', '20000000-0000-0000-0000-000000000011', 'SEV3', TRUE,
                     TIMESTAMPTZ '2026-07-19 00:00:00Z', TIMESTAMPTZ '2026-07-19 00:00:00Z', NULL, 0)
                ON CONFLICT DO NOTHING
                """);
    }
}
