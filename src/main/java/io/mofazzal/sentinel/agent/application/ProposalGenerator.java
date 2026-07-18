package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.domain.Classification;
import io.mofazzal.sentinel.agent.domain.EvidenceBundle;
import io.mofazzal.sentinel.agent.domain.RemediationProposal;
import io.mofazzal.sentinel.agent.domain.TriageRequest;

public interface ProposalGenerator {
    RemediationProposal propose(TriageRequest request, Classification classification,
                                EvidenceBundle evidence, String previousFeedback);
}
