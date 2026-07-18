package io.mofazzal.sentinel.execution;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.fleet.repository.FleetServiceRepository;
import io.mofazzal.sentinel.guardrail.GateDecision;
import io.mofazzal.sentinel.guardrail.GateDecisionType;
import io.mofazzal.sentinel.guardrail.GateEvaluation;
import io.mofazzal.sentinel.guardrail.GateRequest;
import io.mofazzal.sentinel.guardrail.GuardrailGate;
import io.mofazzal.sentinel.guardrail.ExecutionAuthorization;
import io.mofazzal.sentinel.guardrail.RiskFacts;
import io.mofazzal.sentinel.guardrail.TestGateAuthorizationFactory;
import io.mofazzal.sentinel.guardrail.RiskBreakdown;
import io.mofazzal.sentinel.incident.domain.Incident;
import io.mofazzal.sentinel.incident.domain.IncidentSeverity;
import io.mofazzal.sentinel.incident.repository.IncidentRepository;
import io.mofazzal.sentinel.ledger.ActionExecutionRequest;
import io.mofazzal.sentinel.ledger.ActionReservationService;
import io.mofazzal.sentinel.ledger.ActionResultRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
        "spring.profiles.active=seed",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "sentinel.remediation.dry-run=false",
        "sentinel.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "sentinel.security.webhook-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class RemediationExecutionIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "pgvector/pgvector:0.8.2-pg17-bookworm");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private RemediationExecutor executor;

    @Autowired
    private GuardrailGate gate;

    @Autowired
    private ActionReservationService reservations;

    @Autowired
    private ActionResultRecorder results;

    @Autowired
    private FleetServiceRepository services;

    @Autowired
    private IncidentRepository incidents;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @Test
    void concurrentDuplicateExecutionProducesOneAppliedEffectAndImmutableHistory() throws Exception {
        Incident incident = incident("execution-race");
        AuthorizedRequest authorized = request(incident, RemediationActionType.RESTART_SERVICE);

        List<RemediationExecutor.ExecutionOutcome> outcomes;
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Callable<RemediationExecutor.ExecutionOutcome>> calls = List.of(
                            () -> executor.execute(authorized.request(), authorized.authorization()),
                            () -> executor.execute(authorized.request(), authorized.authorization()));
            outcomes = pool.invokeAll(calls)
                    .stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception failure) {
                            throw new RuntimeException(failure);
                        }
                    })
                    .toList();
        }

        assertThat(outcomes).filteredOn(RemediationExecutor.ExecutionOutcome::applied).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from action_claim where fingerprint = ?",
                Integer.class, incident.getFingerprint())).isOne();
        assertThat(jdbc.queryForList("""
                select event_type from action_ledger where incident_id = ? order by recorded_at
                """, String.class, incident.getId())).containsExactly("IN_PROGRESS", "APPLIED");
        assertThat(jdbc.queryForObject("""
                select restart_generation from simulated_remediation_state where service_id = ?
                """, Integer.class, incident.getService().getId())).isOne();

        UUID ledgerId = jdbc.queryForObject("select id from action_ledger limit 1", UUID.class);
        assertThatThrownBy(() -> jdbc.update(
                "update action_ledger set details = 'rewritten' where id = ?", ledgerId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void failureAfterAnAppliedStepRunsCompensationAndRecordsNewFacts() {
        Incident incident = incident("execution-compensation");
        AuthorizedRequest authorized = request(incident, RemediationActionType.SCALE_OUT);
        SimulatedRemediationStrategy delegate = new SimulatedRemediationStrategy(
                RemediationActionType.SCALE_OUT, jdbc, clock);
        RemediationStrategy failsAfterFirstStep = new RemediationStrategy() {
            @Override
            public RemediationActionType actionType() {
                return RemediationActionType.SCALE_OUT;
            }

            @Override
            public ExecutionResult execute(ActionContext context) {
                delegate.execute(context);
                throw new IllegalStateException("induced second-step failure");
            }

            @Override
            public ExecutionResult compensate(ActionContext context) {
                return delegate.compensate(context);
            }
        };
        RemediationExecutor failingExecutor = new RemediationExecutor(
                reservations, results, new RemediationStrategyRegistry(List.of(failsAfterFirstStep)));

        assertThatThrownBy(() -> failingExecutor.execute(authorized.request(), authorized.authorization()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("second-step");

        assertThat(jdbc.queryForObject("""
                select replica_count from simulated_remediation_state where service_id = ?
                """, Integer.class, incident.getService().getId())).isOne();
        assertThat(jdbc.queryForObject("""
                select state from action_claim where fingerprint = ?
                """, String.class, incident.getFingerprint())).isEqualTo("COMPENSATED");
        assertThat(jdbc.queryForList("""
                select event_type from action_ledger where incident_id = ? order by recorded_at
                """, String.class, incident.getId()))
                .containsExactly("IN_PROGRESS", "FAILED", "COMPENSATION_STARTED", "COMPENSATED");
    }

    private Incident incident(String fingerprint) {
        var service = services.findByName("payments-api").orElseThrow();
        return incidents.saveAndFlush(new Incident(
                fingerprint, service, IncidentSeverity.SEV2, Instant.parse("2026-07-19T00:00:00Z")));
    }

    @Test
    void executorRejectsCallsWithoutTheGateIssuedCapability() {
        Incident incident = incident("execution-no-capability");
        AuthorizedRequest authorized = request(incident, RemediationActionType.RESTART_SERVICE);

        assertThatThrownBy(() -> executor.execute(authorized.request(), null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("GuardrailGate");
        assertThat(jdbc.queryForObject("select count(*) from action_claim where incident_id = ?",
                Integer.class, incident.getId())).isZero();
    }

    private AuthorizedRequest request(Incident incident, RemediationActionType actionType) {
        GateEvaluation evaluation = TestGateAuthorizationFactory.evaluate(gate, new GateRequest(
                incident.getId(), incident.getService().getId(), incident.getFingerprint(),
                new RiskFacts(actionType, incident.getService().getTier(), 0, 1.0, false), false));
        ActionExecutionRequest request = new ActionExecutionRequest(
                incident.getId(), incident.getService().getId(), incident.getFingerprint(), actionType,
                evaluation.decision(), "AGENT");
        return new AuthorizedRequest(request, evaluation.executionAuthorization().orElseThrow());
    }

    private record AuthorizedRequest(
            ActionExecutionRequest request,
            ExecutionAuthorization authorization
    ) {
    }
}
