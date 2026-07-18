package io.mofazzal.sentinel.agent.application;

import java.util.UUID;

public interface TranscriptRecorder {
    void record(UUID incidentId, EntryType type, int iteration, String content);

    enum EntryType {
        CLASSIFICATION,
        EVIDENCE,
        PROPOSAL,
        CRITIQUE,
        OUTCOME
    }
}
