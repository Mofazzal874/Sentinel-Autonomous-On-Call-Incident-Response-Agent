package io.mofazzal.sentinel.execution;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;

class SimulatedRemediationStrategy implements RemediationStrategy {

    private final RemediationActionType actionType;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    SimulatedRemediationStrategy(RemediationActionType actionType, JdbcTemplate jdbc, Clock clock) {
        this.actionType = actionType;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public RemediationActionType actionType() {
        return actionType;
    }

    @Override
    @Transactional
    public ExecutionResult execute(ActionContext context) {
        jdbc.update("""
                INSERT INTO simulated_remediation_state (service_id, updated_at)
                VALUES (?, ?)
                ON CONFLICT (service_id) DO NOTHING
                """, context.serviceId(), Timestamp.from(clock.instant()));
        int inserted = jdbc.update("""
                INSERT INTO simulated_action_effect (
                    claim_id, service_id, action_type, state, applied_at
                ) VALUES (?, ?, ?, 'APPLIED', ?)
                ON CONFLICT (claim_id) DO NOTHING
                """, context.claimId(), context.serviceId(), actionType.name(), Timestamp.from(clock.instant()));
        if (inserted == 0) {
            return new ExecutionResult("Simulated effect was already applied for this claim");
        }
        jdbc.update(executeSql(), Timestamp.from(clock.instant()), context.serviceId());
        return new ExecutionResult("Applied simulated " + actionType + " effect");
    }

    @Override
    @Transactional
    public ExecutionResult compensate(ActionContext context) {
        int changed = jdbc.update("""
                UPDATE simulated_action_effect
                SET state = 'COMPENSATED', compensated_at = ?
                WHERE claim_id = ? AND state = 'APPLIED'
                """, Timestamp.from(clock.instant()), context.claimId());
        if (changed == 0) {
            return new ExecutionResult("No applied simulated effect required compensation");
        }
        jdbc.update(compensationSql(), Timestamp.from(clock.instant()), context.serviceId());
        return new ExecutionResult("Compensated simulated " + actionType + " effect");
    }

    private String executeSql() {
        return switch (actionType) {
            case RESTART_SERVICE -> """
                    UPDATE simulated_remediation_state
                    SET restart_generation = restart_generation + 1, updated_at = ?
                    WHERE service_id = ?
                    """;
            case SCALE_OUT -> """
                    UPDATE simulated_remediation_state
                    SET replica_count = replica_count + 1, updated_at = ?
                    WHERE service_id = ?
                    """;
            case CLEAR_CACHE -> """
                    UPDATE simulated_remediation_state
                    SET cache_generation = cache_generation + 1, updated_at = ?
                    WHERE service_id = ?
                    """;
            case ROLLBACK_DEPLOYMENT -> """
                    UPDATE simulated_remediation_state
                    SET rollback_generation = rollback_generation + 1, updated_at = ?
                    WHERE service_id = ?
                    """;
        };
    }

    private String compensationSql() {
        return switch (actionType) {
            case RESTART_SERVICE -> """
                    UPDATE simulated_remediation_state
                    SET restart_generation = greatest(restart_generation - 1, 0), updated_at = ?
                    WHERE service_id = ?
                    """;
            case SCALE_OUT -> """
                    UPDATE simulated_remediation_state
                    SET replica_count = greatest(replica_count - 1, 1), updated_at = ?
                    WHERE service_id = ?
                    """;
            case CLEAR_CACHE -> """
                    UPDATE simulated_remediation_state
                    SET cache_generation = greatest(cache_generation - 1, 0), updated_at = ?
                    WHERE service_id = ?
                    """;
            case ROLLBACK_DEPLOYMENT -> """
                    UPDATE simulated_remediation_state
                    SET rollback_generation = greatest(rollback_generation - 1, 0), updated_at = ?
                    WHERE service_id = ?
                    """;
        };
    }
}
