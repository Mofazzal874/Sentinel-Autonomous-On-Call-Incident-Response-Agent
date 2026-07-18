package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.domain.Classification;
import io.mofazzal.sentinel.agent.domain.EvidenceBundle;
import io.mofazzal.sentinel.agent.domain.ProposalEvaluation;
import io.mofazzal.sentinel.agent.domain.RemediationProposal;
import io.mofazzal.sentinel.agent.domain.TriageOutcome;
import io.mofazzal.sentinel.agent.domain.TriageRequest;
import io.mofazzal.sentinel.observability.SentinelObservations;
import io.micrometer.observation.ObservationRegistry;

import java.util.Objects;

import static io.mofazzal.sentinel.agent.application.TranscriptRecorder.EntryType;

public final class TriageWorkflow {

    private final IncidentRouter router;
    private final EvidenceCollector evidenceCollector;
    private final ProposalGenerator proposalGenerator;
    private final ProposalEvaluator proposalEvaluator;
    private final TranscriptRecorder transcript;
    private final SentinelObservations observations;
    private final int maxAttempts;

    public TriageWorkflow(IncidentRouter router, EvidenceCollector evidenceCollector,
                          ProposalGenerator proposalGenerator, ProposalEvaluator proposalEvaluator,
                          TranscriptRecorder transcript, int maxAttempts) {
        this(router, evidenceCollector, proposalGenerator, proposalEvaluator, transcript, maxAttempts,
                new SentinelObservations(ObservationRegistry.NOOP));
    }

    public TriageWorkflow(IncidentRouter router, EvidenceCollector evidenceCollector,
                          ProposalGenerator proposalGenerator, ProposalEvaluator proposalEvaluator,
                          TranscriptRecorder transcript, int maxAttempts,
                          SentinelObservations observations) {
        this.router = Objects.requireNonNull(router, "router");
        this.evidenceCollector = Objects.requireNonNull(evidenceCollector, "evidenceCollector");
        this.proposalGenerator = Objects.requireNonNull(proposalGenerator, "proposalGenerator");
        this.proposalEvaluator = Objects.requireNonNull(proposalEvaluator, "proposalEvaluator");
        this.transcript = Objects.requireNonNull(transcript, "transcript");
        this.observations = Objects.requireNonNull(observations, "observations");
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and 3");
        }
        this.maxAttempts = maxAttempts;
    }

    public TriageOutcome triage(TriageRequest request) {
        Objects.requireNonNull(request, "request");
        Classification classification = observations.observe("sentinel.agent.classify",
                () -> router.classify(request));
        transcript.record(request.incidentId(), EntryType.CLASSIFICATION, 0, classification.toString());

        EvidenceBundle evidence = observations.observe("sentinel.agent.gather",
                () -> evidenceCollector.collect(request, classification),
                "incident.type", classification.type().name());
        transcript.record(request.incidentId(), EntryType.EVIDENCE, 0, evidence.toString());
        if (evidence.runbooks().isEmpty()) {
            return escalate(request, "No relevant runbook was retrieved", 0);
        }

        String feedback = "No prior critique";
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String critique = feedback;
            RemediationProposal proposal = observations.observe("sentinel.agent.propose",
                    () -> proposalGenerator.propose(request, classification, evidence, critique),
                    "attempt", Integer.toString(attempt));
            transcript.record(request.incidentId(), EntryType.PROPOSAL, attempt, proposal.toString());

            if (!evidence.containsRunbook(proposal.runbookTitle())) {
                feedback = "Proposal cites a runbook that was not retrieved";
                transcript.record(request.incidentId(), EntryType.CRITIQUE, attempt, feedback);
                continue;
            }

            ProposalEvaluation evaluation = observations.observe("sentinel.agent.evaluate",
                    () -> proposalEvaluator.evaluate(request, evidence, proposal),
                    "attempt", Integer.toString(attempt));
            transcript.record(request.incidentId(), EntryType.CRITIQUE, attempt, evaluation.toString());
            if (evaluation.passed()) {
                var groundedRunbook = evidence.runbooks().stream()
                        .filter(runbook -> runbook.title().equals(proposal.runbookTitle()))
                        .findFirst()
                        .orElseThrow();
                TriageOutcome outcome = new TriageOutcome(TriageOutcome.Decision.PROPOSED, proposal,
                        groundedRunbook.id(), groundedRunbook.similarity(),
                        "Grounded proposal passed evaluation", attempt);
                transcript.record(request.incidentId(), EntryType.OUTCOME, attempt, outcome.toString());
                return outcome;
            }
            feedback = evaluation.feedback();
        }
        return escalate(request, "No grounded proposal passed evaluation within the attempt limit", maxAttempts);
    }

    private TriageOutcome escalate(TriageRequest request, String reason, int attempts) {
        TriageOutcome outcome = new TriageOutcome(
                TriageOutcome.Decision.ESCALATED, null, null, 0.0, reason, attempts);
        transcript.record(request.incidentId(), EntryType.OUTCOME, attempts, outcome.toString());
        return outcome;
    }
}
