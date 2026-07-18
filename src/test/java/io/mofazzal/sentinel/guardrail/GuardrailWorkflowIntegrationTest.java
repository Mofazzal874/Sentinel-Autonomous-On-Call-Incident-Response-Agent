package io.mofazzal.sentinel.guardrail;

import io.mofazzal.sentinel.agent.application.AgentRunLifecycleService;
import io.mofazzal.sentinel.agent.domain.RemediationProposal;
import io.mofazzal.sentinel.agent.domain.TriageOutcome;
import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.fleet.repository.FleetServiceRepository;
import io.mofazzal.sentinel.fleet.repository.RunbookRepository;
import io.mofazzal.sentinel.incident.domain.Incident;
import io.mofazzal.sentinel.incident.domain.IncidentSeverity;
import io.mofazzal.sentinel.incident.domain.IncidentStatus;
import io.mofazzal.sentinel.incident.repository.IncidentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
class GuardrailWorkflowIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "pgvector/pgvector:0.8.2-pg17-bookworm");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private AgentRunLifecycleService lifecycle;

    @Autowired
    private RemediationDecisionCoordinator coordinator;

    @Autowired
    private RemediationApprovalService approvals;

    @Autowired
    private RemediationReviewService reviews;

    @Autowired
    private RemediationRecoveryService recovery;

    @Autowired
    private KillSwitchAdministrationService killSwitch;

    @Autowired
    private IncidentRepository incidents;

    @Autowired
    private FleetServiceRepository services;

    @Autowired
    private RunbookRepository runbooks;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void disengageKillSwitch() {
        jdbc.update("update safety_control set engaged = false, updated_at = now(), updated_by = 'TEST'");
        redis.delete(DatabaseBackedKillSwitch.REDIS_KEY);
    }

    @Test
    void lowRiskAllowlistedRestartExecutesOnceAndResolvesIncident() {
        Incident incident = proposed("guardrail-low-risk", "catalog-api",
                "Restart an unhealthy service instance", RemediationActionType.RESTART_SERVICE, 0.95);

        GateDecision decision = coordinator.processProposal(incident.getId());

        assertThat(decision.type()).isEqualTo(GateDecisionType.AUTO_EXECUTE);
        assertThat(incidents.findById(incident.getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.RESOLVED);
        assertThat(jdbc.queryForList("""
                select event_type from action_ledger where incident_id = ? order by recorded_at
                """, String.class, incident.getId()))
                .containsExactly("DECIDED", "IN_PROGRESS", "APPLIED");
        assertThat(jdbc.queryForObject("select count(*) from action_claim where incident_id = ?",
                Integer.class, incident.getId())).isOne();
    }

    @Test
    @WithMockUser(username = "human-sre", roles = "SRE_APPROVER")
    void highRiskRollbackWaitsForHumanThenReentersGateAndExecutes() {
        Incident incident = proposed("guardrail-high-risk", "payments-api",
                "Rollback a faulty service deployment", RemediationActionType.ROLLBACK_DEPLOYMENT, 0.60);

        GateDecision initial = coordinator.processProposal(incident.getId());
        assertThat(initial.type()).isEqualTo(GateDecisionType.REQUIRE_APPROVAL);
        assertThat(incidents.findById(incident.getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.AWAITING_APPROVAL);
        RemediationReview review = reviews.get(incident.getId());
        assertThat(review.groundedRunbook()).isEqualTo("Rollback a faulty service deployment");
        assertThat(review.authoritativeRiskScore()).isGreaterThan(6);
        assertThat(review.authoritativeRiskBreakdown()).contains("confidencePenalty=3");

        ApprovalResponse response = approvals.decide(incident.getId(),
                new ApprovalRequest(ApprovalDecision.APPROVE, "Evidence and rollback scope reviewed"),
                "human-sre");

        assertThat(response.outcome()).isEqualTo(GateDecisionType.APPROVED_EXECUTE.name());
        assertThat(incidents.findById(incident.getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.RESOLVED);
        assertThat(jdbc.queryForList("""
                select event_type from action_ledger where incident_id = ? order by recorded_at
                """, String.class, incident.getId()))
                .containsExactly("APPROVAL_REQUESTED", "APPROVED", "DECIDED", "IN_PROGRESS", "APPLIED");
    }

    @Test
    @WithMockUser(username = "agent-service", roles = "AGENT")
    void agentCannotApproveItsOwnHighRiskProposal() {
        Incident incident = proposed("guardrail-agent-self-approval", "payments-api",
                "Rollback a faulty service deployment", RemediationActionType.ROLLBACK_DEPLOYMENT, 0.60);
        assertThat(coordinator.processProposal(incident.getId()).type())
                .isEqualTo(GateDecisionType.REQUIRE_APPROVAL);

        assertThatThrownBy(() -> approvals.decide(incident.getId(),
                new ApprovalRequest(ApprovalDecision.APPROVE, "agent tries to approve itself"),
                "agent-service"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(jdbc.queryForObject("select count(*) from action_claim where incident_id = ?",
                Integer.class, incident.getId())).isZero();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void engagedKillSwitchEscalatesBeforeAllowlistRiskOrExecution() {
        Incident incident = proposed("guardrail-kill-switch", "catalog-api",
                "Restart an unhealthy service instance", RemediationActionType.RESTART_SERVICE, 0.95);
        killSwitch.setEngaged(true, "admin");

        GateDecision decision = coordinator.processProposal(incident.getId());

        assertThat(decision.type()).isEqualTo(GateDecisionType.ESCALATE);
        assertThat(jdbc.queryForObject("select count(*) from action_claim where incident_id = ?",
                Integer.class, incident.getId())).isZero();
        assertThat(incidents.findById(incident.getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.ESCALATED);
    }

    @Test
    void actionOutsidePersistedServiceAllowlistIsRefused() {
        Incident incident = proposed("guardrail-refused", "catalog-api",
                "Rollback a faulty service deployment", RemediationActionType.ROLLBACK_DEPLOYMENT, 0.95);

        GateDecision decision = coordinator.processProposal(incident.getId());

        assertThat(decision.type()).isEqualTo(GateDecisionType.REFUSE);
        assertThat(jdbc.queryForObject("select count(*) from action_claim where incident_id = ?",
                Integer.class, incident.getId())).isZero();
    }

    @Test
    void expiredApprovalIsRejectedAndNeverExecutes() {
        Incident incident = proposed("guardrail-expired", "payments-api",
                "Rollback a faulty service deployment", RemediationActionType.ROLLBACK_DEPLOYMENT, 0.60);
        assertThat(coordinator.processProposal(incident.getId()).type())
                .isEqualTo(GateDecisionType.REQUIRE_APPROVAL);
        jdbc.update("""
                update remediation_request
                set created_at = now() - interval '1 hour',
                    approval_expires_at = now() - interval '1 second'
                where incident_id = ?
                """,
                incident.getId());

        approvals.escalateExpiredApprovals();

        assertThat(jdbc.queryForObject("select status from remediation_request where incident_id = ?",
                String.class, incident.getId())).isEqualTo("REJECTED");
        assertThat(incidents.findById(incident.getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.ESCALATED);
        assertThat(jdbc.queryForObject("select count(*) from action_claim where incident_id = ?",
                Integer.class, incident.getId())).isZero();
    }

    @Test
    void recoveryProcessesADurablePendingDecisionAfterCoordinatorCrashWindow() {
        Incident incident = proposed("guardrail-pending-recovery", "catalog-api",
                "Restart an unhealthy service instance", RemediationActionType.RESTART_SERVICE, 0.95);

        recovery.recover();

        assertThat(incidents.findById(incident.getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.RESOLVED);
        assertThat(jdbc.queryForObject("select status from remediation_request where incident_id = ?",
                String.class, incident.getId())).isEqualTo("COMPLETED");
    }

    @Test
    void staleUncertainExecutionEscalatesWithoutRetryingItsEffect() {
        Incident incident = proposed("guardrail-stale-execution", "catalog-api",
                "Restart an unhealthy service instance", RemediationActionType.RESTART_SERVICE, 0.95);
        jdbc.update("""
                update remediation_request
                set status = 'EXECUTING',
                    created_at = now() - interval '20 minutes',
                    updated_at = now() - interval '10 minutes'
                where incident_id = ?
                """, incident.getId());
        jdbc.update("update incident set status = 'REMEDIATING', updated_at = created_at where id = ?",
                incident.getId());

        recovery.recover();

        assertThat(incidents.findById(incident.getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.ESCALATED);
        assertThat(jdbc.queryForObject("select count(*) from simulated_action_effect where service_id = ?",
                Integer.class, incident.getService().getId())).isZero();
    }

    @Test
    void persistenceBoundaryRejectsUngroundedOrMismatchedProposals() {
        var service = services.findByName("catalog-api").orElseThrow();
        var restartRunbook = runbooks.findByTitle("Restart an unhealthy service instance").orElseThrow();
        Incident lowSimilarity = incidents.saveAndFlush(new Incident(
                "guardrail-ungrounded", service, IncidentSeverity.SEV2, Instant.now()));
        UUID lowSimilarityRun = lifecycle.begin(lowSimilarity.getId());
        RemediationProposal restart = new RemediationProposal(
                RemediationActionType.RESTART_SERVICE, restartRunbook.getTitle(),
                List.of("Verify", "Restart"), "Test grounding boundary", "Deterministic risk follows");

        assertThatThrownBy(() -> lifecycle.complete(lowSimilarityRun, new TriageOutcome(
                TriageOutcome.Decision.PROPOSED, restart, restartRunbook.getId(), 0.59,
                "Malformed internal outcome", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("below");

        Incident mismatch = incidents.saveAndFlush(new Incident(
                "guardrail-runbook-mismatch", service, IncidentSeverity.SEV2, Instant.now()));
        UUID mismatchRun = lifecycle.begin(mismatch.getId());
        RemediationProposal mismatchedAction = new RemediationProposal(
                RemediationActionType.SCALE_OUT, restartRunbook.getTitle(),
                List.of("Verify", "Scale"), "Test identity boundary", "Deterministic risk follows");

        assertThatThrownBy(() -> lifecycle.complete(mismatchRun, new TriageOutcome(
                TriageOutcome.Decision.PROPOSED, mismatchedAction, restartRunbook.getId(), 0.95,
                "Malformed internal outcome", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("do not match");
        assertThat(jdbc.queryForObject("select count(*) from remediation_request where incident_id in (?, ?)",
                Integer.class, lowSimilarity.getId(), mismatch.getId())).isZero();
    }

    private Incident proposed(String fingerprint,
                              String serviceName,
                              String runbookTitle,
                              RemediationActionType actionType,
                              double similarity) {
        var service = services.findByName(serviceName).orElseThrow();
        var runbook = runbooks.findByTitle(runbookTitle).orElseThrow();
        Incident incident = incidents.saveAndFlush(new Incident(
                fingerprint, service, IncidentSeverity.SEV2, Instant.now()));
        UUID runId = lifecycle.begin(incident.getId());
        RemediationProposal proposal = new RemediationProposal(
                actionType, runbookTitle, List.of("Verify evidence", "Apply one bounded change"),
                "Deterministic integration scenario", "Authoritative risk is computed in Java");
        lifecycle.complete(runId, new TriageOutcome(
                TriageOutcome.Decision.PROPOSED, proposal, runbook.getId(), similarity,
                "Grounded proposal passed evaluation", 1));
        return incident;
    }
}
