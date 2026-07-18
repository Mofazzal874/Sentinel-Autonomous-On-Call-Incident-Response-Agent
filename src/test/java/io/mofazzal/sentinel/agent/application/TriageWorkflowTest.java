package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.domain.Classification;
import io.mofazzal.sentinel.agent.domain.EvidenceBundle;
import io.mofazzal.sentinel.agent.domain.EvidenceSignal;
import io.mofazzal.sentinel.agent.domain.IncidentType;
import io.mofazzal.sentinel.agent.domain.ProposalEvaluation;
import io.mofazzal.sentinel.agent.domain.RemediationProposal;
import io.mofazzal.sentinel.agent.domain.TriageOutcome;
import io.mofazzal.sentinel.agent.domain.TriageRequest;
import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.tools.RunbookRetrieveTool.RunbookSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TriageWorkflowTest {

    private static final TriageRequest REQUEST = new TriageRequest(
            UUID.fromString("40000000-0000-0000-0000-000000000001"),
            "payments-api", "Error rate rose after deployment", Instant.parse("2026-07-18T01:00:00Z"));
    private static final Classification BAD_DEPLOY = new Classification(
            IncidentType.BAD_DEPLOY,
            List.of(EvidenceSignal.DEPLOYMENTS, EvidenceSignal.METRICS, EvidenceSignal.RUNBOOKS),
            "The error increase immediately follows a deployment");
    private static final RunbookSummary ROLLBACK_RUNBOOK = new RunbookSummary(
            "Rollback a faulty service deployment",
            "Error rate or latency rises immediately after a deployment.",
            "Confirm correlation; roll back; verify recovery.",
            RemediationActionType.ROLLBACK_DEPLOYMENT);

    @Test
    void refinesProposalWithinBoundAndReturnsGroundedProposal() {
        AtomicInteger generation = new AtomicInteger();
        RecordingTranscript transcript = new RecordingTranscript();
        TriageWorkflow workflow = new TriageWorkflow(
                request -> BAD_DEPLOY,
                (request, classification) -> evidenceWith(ROLLBACK_RUNBOOK),
                (request, classification, evidence, feedback) -> {
                    int attempt = generation.incrementAndGet();
                    return proposal(ROLLBACK_RUNBOOK.title(), attempt == 1
                            ? "Rollback immediately"
                            : "Confirm the deployment correlation, then roll back");
                },
                (request, evidence, proposal) -> proposal.rationale().startsWith("Confirm")
                        ? new ProposalEvaluation(true, "Grounded and sequenced safely")
                        : new ProposalEvaluation(false, "Verify correlation before proposing rollback"),
                transcript,
                3);

        TriageOutcome outcome = workflow.triage(REQUEST);

        assertThat(outcome.decision()).isEqualTo(TriageOutcome.Decision.PROPOSED);
        assertThat(outcome.attempts()).isEqualTo(2);
        assertThat(outcome.optionalProposal()).get()
                .extracting(RemediationProposal::runbookTitle)
                .isEqualTo(ROLLBACK_RUNBOOK.title());
        assertThat(generation).hasValue(2);
        assertThat(transcript.types()).containsExactly(
                TranscriptRecorder.EntryType.CLASSIFICATION,
                TranscriptRecorder.EntryType.EVIDENCE,
                TranscriptRecorder.EntryType.PROPOSAL,
                TranscriptRecorder.EntryType.CRITIQUE,
                TranscriptRecorder.EntryType.PROPOSAL,
                TranscriptRecorder.EntryType.CRITIQUE,
                TranscriptRecorder.EntryType.OUTCOME);
    }

    @Test
    void escalatesBeforeGenerationWhenRetrievalIsEmpty() {
        AtomicInteger generations = new AtomicInteger();
        RecordingTranscript transcript = new RecordingTranscript();
        TriageWorkflow workflow = new TriageWorkflow(
                request -> BAD_DEPLOY,
                (request, classification) -> evidenceWith(),
                (request, classification, evidence, feedback) -> {
                    generations.incrementAndGet();
                    return proposal(ROLLBACK_RUNBOOK.title(), "Should not be called");
                },
                (request, evidence, proposal) -> new ProposalEvaluation(true, "Should not be called"),
                transcript,
                3);

        TriageOutcome outcome = workflow.triage(REQUEST);

        assertThat(outcome.decision()).isEqualTo(TriageOutcome.Decision.ESCALATED);
        assertThat(outcome.reason()).contains("No relevant runbook");
        assertThat(outcome.attempts()).isZero();
        assertThat(generations).hasValue(0);
        assertThat(transcript.types()).containsExactly(
                TranscriptRecorder.EntryType.CLASSIFICATION,
                TranscriptRecorder.EntryType.EVIDENCE,
                TranscriptRecorder.EntryType.OUTCOME);
    }

    @Test
    void neverAcceptsHallucinatedRunbookAndStopsAtThreeAttempts() {
        AtomicInteger evaluations = new AtomicInteger();
        RecordingTranscript transcript = new RecordingTranscript();
        TriageWorkflow workflow = new TriageWorkflow(
                request -> BAD_DEPLOY,
                (request, classification) -> evidenceWith(ROLLBACK_RUNBOOK),
                (request, classification, evidence, feedback) -> proposal(
                        "Invented emergency runbook", "Use a runbook that retrieval did not return"),
                (request, evidence, proposal) -> {
                    evaluations.incrementAndGet();
                    return new ProposalEvaluation(true, "Model evaluator would accept it");
                },
                transcript,
                3);

        TriageOutcome outcome = workflow.triage(REQUEST);

        assertThat(outcome.decision()).isEqualTo(TriageOutcome.Decision.ESCALATED);
        assertThat(outcome.attempts()).isEqualTo(3);
        assertThat(evaluations).hasValue(0);
        assertThat(transcript.types()).filteredOn(type -> type == TranscriptRecorder.EntryType.PROPOSAL)
                .hasSize(3);
    }

    private static EvidenceBundle evidenceWith(RunbookSummary... runbooks) {
        return new EvidenceBundle(List.of(), List.of(), List.of(), List.of(runbooks));
    }

    private static RemediationProposal proposal(String runbookTitle, String rationale) {
        return new RemediationProposal(
                RemediationActionType.ROLLBACK_DEPLOYMENT,
                runbookTitle,
                List.of("Confirm the correlated deployment", "Roll back through the future guardrail gate"),
                rationale,
                "Rollback can affect all instances and requires deterministic risk evaluation");
    }

    private static final class RecordingTranscript implements TranscriptRecorder {
        private final List<EntryType> entries = new ArrayList<>();

        @Override
        public void record(UUID incidentId, EntryType type, int iteration, String content) {
            entries.add(type);
        }

        List<EntryType> types() {
            return List.copyOf(entries);
        }
    }
}
