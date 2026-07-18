package io.mofazzal.sentinel.agent.persistence;

import io.mofazzal.sentinel.agent.application.TranscriptRecorder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

@Component
public class PersistentTranscriptRecorder implements TranscriptRecorder {

    private static final int MAX_CONTENT_LENGTH = 32_000;
    private final AgentRunRepository runs;
    private final AgentTranscriptEntryRepository entries;
    private final Clock clock;

    public PersistentTranscriptRecorder(AgentRunRepository runs,
                                        AgentTranscriptEntryRepository entries,
                                        Clock clock) {
        this.runs = runs;
        this.entries = entries;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID incidentId, EntryType type, int iteration, String content) {
        Objects.requireNonNull(incidentId, "incidentId");
        AgentRun run = runs.findRunningForUpdate(incidentId)
                .orElseThrow(() -> new IllegalStateException("No running agent run for incident " + incidentId));
        String boundedContent = requireBoundedContent(content);
        entries.save(new AgentTranscriptEntry(run, run.nextTranscriptSequence(), type,
                iteration, boundedContent, clock.instant()));
    }

    private static String requireBoundedContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("content must not exceed 32000 characters");
        }
        return content;
    }
}
