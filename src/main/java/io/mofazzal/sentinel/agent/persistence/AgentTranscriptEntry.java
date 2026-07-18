package io.mofazzal.sentinel.agent.persistence;

import io.mofazzal.sentinel.agent.application.TranscriptRecorder.EntryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "agent_transcript_entry")
public class AgentTranscriptEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private AgentRun run;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private EntryType entryType;

    @Column(nullable = false)
    private int iteration;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected AgentTranscriptEntry() {
    }

    public AgentTranscriptEntry(AgentRun run, int sequenceNumber, EntryType entryType,
                                int iteration, String content, Instant recordedAt) {
        this.run = Objects.requireNonNull(run, "run");
        this.sequenceNumber = sequenceNumber;
        this.entryType = Objects.requireNonNull(entryType, "entryType");
        if (iteration < 0 || iteration > 3) {
            throw new IllegalArgumentException("iteration must be between 0 and 3");
        }
        this.iteration = iteration;
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        this.content = content;
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
    }
}
