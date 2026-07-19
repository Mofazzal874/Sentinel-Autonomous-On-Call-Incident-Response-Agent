CREATE TABLE demo_live_submission (
    public_id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES demo_scenario_template(id),
    fingerprint VARCHAR(128) NOT NULL UNIQUE,
    client_hash CHAR(64) NOT NULL,
    idempotency_key_hash CHAR(64) NOT NULL,
    state VARCHAR(20) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    failure_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_demo_submission_client_idempotency UNIQUE (client_hash, idempotency_key_hash),
    CONSTRAINT ck_demo_submission_state CHECK (state IN ('ACCEPTED', 'QUEUED', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_demo_submission_completion CHECK (
        (state IN ('ACCEPTED', 'QUEUED') AND completed_at IS NULL)
        OR (state IN ('COMPLETED', 'FAILED') AND completed_at IS NOT NULL)
    )
);

CREATE INDEX ix_demo_submission_state_time ON demo_live_submission(state, submitted_at);
