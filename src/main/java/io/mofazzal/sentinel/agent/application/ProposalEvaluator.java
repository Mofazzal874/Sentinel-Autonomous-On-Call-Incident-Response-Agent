package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.domain.EvidenceBundle;
import io.mofazzal.sentinel.agent.domain.ProposalEvaluation;
import io.mofazzal.sentinel.agent.domain.RemediationProposal;
import io.mofazzal.sentinel.agent.domain.TriageRequest;

public interface ProposalEvaluator {
    ProposalEvaluation evaluate(TriageRequest request, EvidenceBundle evidence,
                                RemediationProposal proposal);
}
