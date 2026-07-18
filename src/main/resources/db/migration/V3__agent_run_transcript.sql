CREATE TABLE agent_run (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incident(id),
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    outcome_reason TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_sequence INTEGER NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_agent_run_status CHECK (status IN ('RUNNING', 'PROPOSED', 'ESCALATED', 'FAILED')),
    CONSTRAINT ck_agent_run_attempt_count CHECK (attempt_count BETWEEN 0 AND 3),
    CONSTRAINT ck_agent_run_next_sequence CHECK (next_sequence >= 1),
    CONSTRAINT ck_agent_run_completion CHECK (
        (status = 'RUNNING' AND completed_at IS NULL)
        OR (status <> 'RUNNING' AND completed_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_agent_run_incident_running
    ON agent_run(incident_id)
    WHERE status = 'RUNNING';
CREATE INDEX ix_agent_run_incident_started ON agent_run(incident_id, started_at DESC);

CREATE TABLE agent_transcript_entry (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES agent_run(id),
    sequence_number INTEGER NOT NULL,
    entry_type VARCHAR(20) NOT NULL,
    iteration INTEGER NOT NULL,
    content TEXT NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_agent_transcript_run_sequence UNIQUE (run_id, sequence_number),
    CONSTRAINT ck_agent_transcript_type CHECK (
        entry_type IN ('CLASSIFICATION', 'EVIDENCE', 'PROPOSAL', 'CRITIQUE', 'OUTCOME')
    ),
    CONSTRAINT ck_agent_transcript_iteration CHECK (iteration BETWEEN 0 AND 3),
    CONSTRAINT ck_agent_transcript_content_not_blank CHECK (btrim(content) <> '')
);

CREATE INDEX ix_agent_transcript_run_sequence
    ON agent_transcript_entry(run_id, sequence_number);
