package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.domain.Classification;
import io.mofazzal.sentinel.agent.domain.EvidenceBundle;
import io.mofazzal.sentinel.agent.domain.TriageRequest;

public interface EvidenceCollector {
    EvidenceBundle collect(TriageRequest request, Classification classification);
}
