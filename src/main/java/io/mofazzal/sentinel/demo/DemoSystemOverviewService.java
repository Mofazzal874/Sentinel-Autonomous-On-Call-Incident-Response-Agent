package io.mofazzal.sentinel.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Profile("demo")
public class DemoSystemOverviewService {

    private final JdbcTemplate jdbc;

    public DemoSystemOverviewService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public DemoSystemOverview read() {
        return jdbc.queryForObject("""
                SELECT
                    (SELECT count(*) FROM team WHERE archived_at IS NULL) AS teams,
                    (SELECT count(*) FROM fleet_service WHERE archived_at IS NULL) AS services,
                    (SELECT count(*) FROM service_dependency) AS dependencies,
                    (SELECT count(*) FROM deployment) AS deployments,
                    (SELECT count(*) FROM metric_sample) AS metric_samples,
                    (SELECT count(*) FROM log_event) AS log_events,
                    (SELECT count(*) FROM incident) AS incidents,
                    (SELECT count(*) FROM runbook WHERE archived_at IS NULL) AS runbooks,
                    (SELECT count(*) FROM demo_scenario_template
                        WHERE enabled = true AND archived_at IS NULL) AS public_scenarios,
                    (SELECT count(*) FROM demo_run WHERE source = 'LIVE') AS live_runs,
                    (SELECT count(*) FROM action_ledger) AS ledger_events
                """, (result, row) -> new DemoSystemOverview(
                result.getInt("teams"),
                result.getInt("services"),
                result.getInt("dependencies"),
                result.getInt("deployments"),
                result.getInt("metric_samples"),
                result.getInt("log_events"),
                result.getInt("incidents"),
                result.getInt("runbooks"),
                result.getInt("public_scenarios"),
                result.getInt("live_runs"),
                result.getInt("ledger_events"),
                "DRY_RUN",
                "PROPOSE_ONLY",
                Instant.now()));
    }
}
