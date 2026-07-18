package io.mofazzal.sentinel.agent.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentTranscriptEntryRepository extends JpaRepository<AgentTranscriptEntry, UUID> {
    List<AgentTranscriptEntry> findByRunIdOrderBySequenceNumber(UUID runId);
}
