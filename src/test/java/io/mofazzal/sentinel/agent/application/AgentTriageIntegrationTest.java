package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.domain.Classification;
import io.mofazzal.sentinel.agent.domain.EvidenceSignal;
import io.mofazzal.sentinel.agent.domain.IncidentType;
import io.mofazzal.sentinel.agent.domain.ProposalEvaluation;
import io.mofazzal.sentinel.agent.domain.RemediationProposal;
import io.mofazzal.sentinel.agent.domain.TriageOutcome;
import io.mofazzal.sentinel.agent.domain.TriageRequest;
import io.mofazzal.sentinel.agent.retrieval.RunbookEmbeddingIndexer;
import io.mofazzal.sentinel.agent.retrieval.TextEmbeddingGateway;
import io.mofazzal.sentinel.alert.api.AlertPayload;
import io.mofazzal.sentinel.alert.messaging.TriageCommand;
import io.mofazzal.sentinel.incident.application.IncidentCreationService;
import io.mofazzal.sentinel.incident.domain.IncidentSeverity;
import io.mofazzal.sentinel.incident.domain.IncidentStatus;
import io.mofazzal.sentinel.incident.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Import(AgentTriageIntegrationTest.AgentTestConfiguration.class)
@SpringBootTest(properties = {
        "spring.profiles.active=seed",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "sentinel.agent.enabled=true",
        "sentinel.agent.retrieval-mode=semantic",
        "sentinel.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "sentinel.security.webhook-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class AgentTriageIntegrationTest {

    private static final Instant INCIDENT_AT = Instant.parse("2026-07-15T12:05:00Z");

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
    private AgentTriageCoordinator coordinator;

    @Autowired
    private AgentRunLifecycleService lifecycle;

    @Autowired
    private RunbookEmbeddingIndexer indexer;

    @Autowired
    private IncidentCreationService incidentCreation;

    @Autowired
    private IncidentRepository incidents;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AtomicInteger generatedProposals;

    @Test
    void groundedBadDeployProposesWithoutExecutionAndPersistsFullTranscript() {
        indexer.indexAll();
        var incident = createIncident("agent-e2e-grounded",
                "Error rate spiked immediately after a bad release");

        TriageOutcome outcome = coordinator.triage(new TriageRequest(
                incident.getId(), "payments-api",
                "Error rate spiked immediately after a bad release", INCIDENT_AT));

        assertThat(outcome.decision()).isEqualTo(TriageOutcome.Decision.PROPOSED);
        assertThat(outcome.optionalProposal()).get()
                .extracting(RemediationProposal::runbookTitle)
                .isEqualTo("Rollback a faulty service deployment");
        assertThat(incidents.findById(incident.getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.AWAITING_APPROVAL);
        assertThat(jdbc.queryForObject(
                "select count(*) from agent_run where incident_id = ? and status = 'PROPOSED'",
                Integer.class, incident.getId())).isOne();
        assertThat(jdbc.queryForList("""
                select entry_type, content from agent_transcript_entry entry
                join agent_run run on run.id = entry.run_id
                where run.incident_id = ? order by sequence_number
                """, incident.getId()))
                .extracting(row -> row.get("entry_type"))
                .containsExactly("CLASSIFICATION", "EVIDENCE", "PROPOSAL", "CRITIQUE", "OUTCOME");
        assertThat(jdbc.queryForObject("""
                select count(*) from agent_transcript_entry entry
                join agent_run run on run.id = entry.run_id
                where run.incident_id = ? and entry.content like '%Rollback%'
                """, Integer.class, incident.getId())).isPositive();
    }

    @Test
    void unmatchedSymptomEscalatesBeforeProposalGeneration() {
        indexer.indexAll();
        int generatedBefore = generatedProposals.get();
        var incident = createIncident("agent-e2e-ungrounded",
                "Certificate authority mismatch in an unknown dependency");

        TriageOutcome outcome = coordinator.triage(new TriageRequest(
                incident.getId(), "payments-api",
                "Certificate authority mismatch in an unknown dependency", INCIDENT_AT));

        assertThat(outcome.decision()).isEqualTo(TriageOutcome.Decision.ESCALATED);
        assertThat(outcome.reason()).contains("No relevant runbook");
        assertThat(generatedProposals).hasValue(generatedBefore);
        assertThat(incidents.findById(incident.getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.ESCALATED);
    }

    @Test
    void concurrentStartsCreateOnlyOneRunningAgentRun() throws Exception {
        var incident = createIncident("agent-e2e-concurrent", "Concurrent start check");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> beginTogether(incident.getId(), ready, start));
            var second = executor.submit(() -> beginTogether(incident.getId(), ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .filteredOn(Result::started)
                    .hasSize(1);
        }
        assertThat(jdbc.queryForObject(
                "select count(*) from agent_run where incident_id = ? and status = 'RUNNING'",
                Integer.class, incident.getId())).isOne();
    }

    private Result beginTogether(UUID incidentId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await(10, TimeUnit.SECONDS);
            lifecycle.begin(incidentId);
            return new Result(true);
        } catch (Exception expectedLoser) {
            return new Result(false);
        }
    }

    private io.mofazzal.sentinel.incident.domain.Incident createIncident(String fingerprint, String summary) {
        var payload = new AlertPayload("payments-api", "AgentIntegration", IncidentSeverity.SEV2,
                INCIDENT_AT.minusSeconds(30), summary, Map.of("test", fingerprint));
        incidentCreation.createIfAbsent(TriageCommand.create(fingerprint, payload, INCIDENT_AT));
        return incidents.findByFingerprint(fingerprint).orElseThrow();
    }

    private record Result(boolean started) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AgentTestConfiguration {

        @Bean
        AtomicInteger generatedProposals() {
            return new AtomicInteger();
        }

        @Bean
        IncidentRouter scriptedRouter() {
            return request -> new Classification(
                    request.summary().toLowerCase(Locale.ROOT).contains("unknown")
                            ? IncidentType.UNKNOWN : IncidentType.BAD_DEPLOY,
                    List.of(EvidenceSignal.DEPLOYMENTS, EvidenceSignal.METRICS,
                            EvidenceSignal.LOGS, EvidenceSignal.RUNBOOKS),
                    "Test router selected bounded evidence");
        }

        @Bean
        ProposalGenerator scriptedGenerator(AtomicInteger generatedProposals) {
            return (request, classification, evidence, feedback) -> {
                generatedProposals.incrementAndGet();
                var runbook = evidence.runbooks().getFirst();
                return new RemediationProposal(runbook.actionType(), runbook.title(),
                        List.of("Confirm deployment correlation", "Propose guarded rollback"),
                        "The deployment and error-rate spike correlate",
                        "Critical service rollback requires deterministic scoring");
            };
        }

        @Bean
        ProposalEvaluator scriptedEvaluator() {
            return (request, evidence, proposal) ->
                    new ProposalEvaluation(true, "Proposal is grounded and bounded");
        }

        @Bean
        TextEmbeddingGateway deterministicEmbeddingGateway() {
            return new TextEmbeddingGateway() {
                @Override
                public float[] embed(String text) {
                    String normalized = text.toLowerCase(Locale.ROOT);
                    int axis;
                    if (normalized.contains("saturat") || normalized.contains("scale")
                            || normalized.contains("cpu")) {
                        axis = 2;
                    } else if (normalized.contains("restart") || normalized.contains("unhealthy")) {
                        axis = 1;
                    } else if (normalized.contains("rollback") || normalized.contains("bad release")
                            || normalized.contains("error rate") || normalized.contains("deployment")) {
                        axis = 0;
                    } else {
                        axis = 7;
                    }
                    float[] vector = new float[768];
                    vector[axis] = 1.0f;
                    return vector;
                }

                @Override
                public int dimensions() {
                    return 768;
                }
            };
        }
    }
}
