package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.domain.Classification;
import io.mofazzal.sentinel.agent.domain.EvidenceBundle;
import io.mofazzal.sentinel.agent.domain.ProposalEvaluation;
import io.mofazzal.sentinel.agent.domain.RemediationProposal;
import io.mofazzal.sentinel.agent.domain.TriageOutcome;
import io.mofazzal.sentinel.agent.domain.TriageRequest;

import java.util.Objects;

import static io.mofazzal.sentinel.agent.application.TranscriptRecorder.EntryType;

public final class TriageWorkflow {

    private final IncidentRouter router;
    private final EvidenceCollector evidenceCollector;
    private final ProposalGenerator proposalGenerator;
    private final ProposalEvaluator proposalEvaluator;
    private final TranscriptRecorder transcript;
    private final int maxAttempts;

    public TriageWorkflow(IncidentRouter router, EvidenceCollector evidenceCollector,
                          ProposalGenerator proposalGenerator, ProposalEvaluator proposalEvaluator,
                          TranscriptRecorder transcript, int maxAttempts) {
        this.router = Objects.requireNonNull(router, "router");
        this.evidenceCollector = Objects.requireNonNull(evidenceCollector, "evidenceCollector");
        this.proposalGenerator = Objects.requireNonNull(proposalGenerator, "proposalGenerator");
        this.proposalEvaluator = Objects.requireNonNull(proposalEvaluator, "proposalEvaluator");
        this.transcript = Objects.requireNonNull(transcript, "transcript");
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and 3");
        }
        this.maxAttempts = maxAttempts;
    }

    public TriageOutcome triage(TriageRequest request) {
        Objects.requireNonNull(request, "request");
        Classification classification = router.classify(request);
        transcript.record(request.incidentId(), EntryType.CLASSIFICATION, 0, classification.toString());

        EvidenceBundle evidence = evidenceCollector.collect(request, classification);
        transcript.record(request.incidentId(), EntryType.EVIDENCE, 0, evidence.toString());
        if (evidence.runbooks().isEmpty()) {
            return escalate(request, "No relevant runbook was retrieved", 0);
        }

        String feedback = "No prior critique";
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            RemediationProposal proposal = proposalGenerator.propose(
                    request, classification, evidence, feedback);
            transcript.record(request.incidentId(), EntryType.PROPOSAL, attempt, proposal.toString());

            if (!evidence.containsRunbook(proposal.runbookTitle())) {
                feedback = "Proposal cites a runbook that was not retrieved";
                transcript.record(request.incidentId(), EntryType.CRITIQUE, attempt, feedback);
                continue;
            }

            ProposalEvaluation evaluation = proposalEvaluator.evaluate(request, evidence, proposal);
            transcript.record(request.incidentId(), EntryType.CRITIQUE, attempt, evaluation.toString());
            if (evaluation.passed()) {
                TriageOutcome outcome = new TriageOutcome(TriageOutcome.Decision.PROPOSED, proposal,
                        "Grounded proposal passed evaluation", attempt);
                transcript.record(request.incidentId(), EntryType.OUTCOME, attempt, outcome.toString());
                return outcome;
            }
            feedback = evaluation.feedback();
        }
        return escalate(request, "No grounded proposal passed evaluation within the attempt limit", maxAttempts);
    }

    private TriageOutcome escalate(TriageRequest request, String reason, int attempts) {
        TriageOutcome outcome = new TriageOutcome(TriageOutcome.Decision.ESCALATED, null, reason, attempts);
        transcript.record(request.incidentId(), EntryType.OUTCOME, attempts, outcome.toString());
        return outcome;
    }
}
