package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.domain.Classification;
import io.mofazzal.sentinel.agent.domain.TriageRequest;

public interface IncidentRouter {
    Classification classify(TriageRequest request);
}
