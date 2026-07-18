CREATE TABLE safety_control (
    id SMALLINT PRIMARY KEY,
    engaged BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_safety_control_singleton CHECK (id = 1),
    CONSTRAINT ck_safety_control_actor_not_blank CHECK (btrim(updated_by) <> '')
);

INSERT INTO safety_control (id, engaged, updated_at, updated_by)
VALUES (1, FALSE, CURRENT_TIMESTAMP, 'SYSTEM');

CREATE TABLE remediation_request (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL UNIQUE REFERENCES incident(id),
    runbook_id UUID NOT NULL REFERENCES runbook(id),
    action_type VARCHAR(40) NOT NULL,
    steps TEXT NOT NULL,
    rationale TEXT NOT NULL,
    risk_notes TEXT NOT NULL,
    grounding_similarity NUMERIC(6, 5) NOT NULL,
    affected_dependents INTEGER NOT NULL DEFAULT 0,
    peak_traffic_window BOOLEAN NOT NULL,
    risk_score INTEGER,
    risk_breakdown TEXT,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    approval_expires_at TIMESTAMPTZ NOT NULL,
    decided_by VARCHAR(120),
    decision_note TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_remediation_request_action CHECK (
        action_type IN ('RESTART_SERVICE', 'ROLLBACK_DEPLOYMENT', 'SCALE_OUT', 'CLEAR_CACHE')
    ),
    CONSTRAINT ck_remediation_request_steps_not_blank CHECK (btrim(steps) <> ''),
    CONSTRAINT ck_remediation_request_rationale_not_blank CHECK (btrim(rationale) <> ''),
    CONSTRAINT ck_remediation_request_risk_notes_not_blank CHECK (btrim(risk_notes) <> ''),
    CONSTRAINT ck_remediation_request_similarity CHECK (grounding_similarity BETWEEN 0 AND 1),
    CONSTRAINT ck_remediation_request_dependents CHECK (affected_dependents >= 0),
    CONSTRAINT ck_remediation_request_risk CHECK (risk_score IS NULL OR risk_score BETWEEN 0 AND 100),
    CONSTRAINT ck_remediation_request_status CHECK (
        status IN (
            'PENDING_DECISION', 'AWAITING_APPROVAL', 'EXECUTING', 'COMPLETED',
            'DRY_RUN', 'REFUSED', 'ESCALATED', 'SKIPPED', 'REJECTED'
        )
    ),
    CONSTRAINT ck_remediation_request_time CHECK (
        updated_at >= created_at AND approval_expires_at > created_at
    )
);

CREATE INDEX ix_remediation_request_status_expiry
    ON remediation_request(status, approval_expires_at);

CREATE TABLE action_claim (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incident(id),
    fingerprint VARCHAR(128) NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    state VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    result TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_action_claim_fingerprint_action UNIQUE (fingerprint, action_type),
    CONSTRAINT ck_action_claim_fingerprint_not_blank CHECK (btrim(fingerprint) <> ''),
    CONSTRAINT ck_action_claim_action CHECK (
        action_type IN ('RESTART_SERVICE', 'ROLLBACK_DEPLOYMENT', 'SCALE_OUT', 'CLEAR_CACHE')
    ),
    CONSTRAINT ck_action_claim_state CHECK (
        state IN ('IN_PROGRESS', 'APPLIED', 'FAILED', 'COMPENSATED', 'COMPENSATION_FAILED')
    ),
    CONSTRAINT ck_action_claim_completion CHECK (
        (state = 'IN_PROGRESS' AND completed_at IS NULL)
        OR (state <> 'IN_PROGRESS' AND completed_at IS NOT NULL)
    )
);

CREATE INDEX ix_action_claim_incident ON action_claim(incident_id, created_at DESC);

CREATE TABLE action_ledger (
    id UUID PRIMARY KEY,
    claim_id UUID REFERENCES action_claim(id),
    incident_id UUID NOT NULL REFERENCES incident(id),
    fingerprint VARCHAR(128) NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    decision VARCHAR(30),
    risk_score INTEGER,
    risk_breakdown TEXT,
    mode VARCHAR(20) NOT NULL,
    actor VARCHAR(120) NOT NULL,
    details TEXT NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    compensation_of UUID REFERENCES action_ledger(id),
    CONSTRAINT ck_action_ledger_fingerprint_not_blank CHECK (btrim(fingerprint) <> ''),
    CONSTRAINT ck_action_ledger_action CHECK (
        action_type IN ('RESTART_SERVICE', 'ROLLBACK_DEPLOYMENT', 'SCALE_OUT', 'CLEAR_CACHE')
    ),
    CONSTRAINT ck_action_ledger_event CHECK (
        event_type IN (
            'DECIDED', 'IN_PROGRESS', 'APPLIED', 'FAILED', 'DRY_RUN', 'SKIPPED',
            'REFUSED', 'ESCALATED', 'APPROVAL_REQUESTED', 'APPROVED', 'REJECTED',
            'COMPENSATION_STARTED', 'COMPENSATED', 'COMPENSATION_FAILED'
        )
    ),
    CONSTRAINT ck_action_ledger_mode CHECK (mode IN ('AUTOMATIC', 'HUMAN_APPROVED', 'DRY_RUN', 'NONE')),
    CONSTRAINT ck_action_ledger_actor_not_blank CHECK (btrim(actor) <> ''),
    CONSTRAINT ck_action_ledger_details_not_blank CHECK (btrim(details) <> ''),
    CONSTRAINT ck_action_ledger_risk CHECK (risk_score IS NULL OR risk_score BETWEEN 0 AND 100)
);

CREATE INDEX ix_action_ledger_incident_time ON action_ledger(incident_id, recorded_at);
CREATE INDEX ix_action_ledger_claim_time ON action_ledger(claim_id, recorded_at) WHERE claim_id IS NOT NULL;

CREATE TABLE simulated_remediation_state (
    service_id UUID PRIMARY KEY REFERENCES fleet_service(id),
    restart_generation INTEGER NOT NULL DEFAULT 0,
    replica_count INTEGER NOT NULL DEFAULT 1,
    cache_generation INTEGER NOT NULL DEFAULT 0,
    rollback_generation INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_simulated_restart_generation CHECK (restart_generation >= 0),
    CONSTRAINT ck_simulated_replica_count CHECK (replica_count >= 1),
    CONSTRAINT ck_simulated_cache_generation CHECK (cache_generation >= 0),
    CONSTRAINT ck_simulated_rollback_generation CHECK (rollback_generation >= 0)
);

CREATE TABLE simulated_action_effect (
    claim_id UUID PRIMARY KEY REFERENCES action_claim(id),
    service_id UUID NOT NULL REFERENCES fleet_service(id),
    action_type VARCHAR(40) NOT NULL,
    state VARCHAR(20) NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL,
    compensated_at TIMESTAMPTZ,
    CONSTRAINT ck_simulated_effect_action CHECK (
        action_type IN ('RESTART_SERVICE', 'ROLLBACK_DEPLOYMENT', 'SCALE_OUT', 'CLEAR_CACHE')
    ),
    CONSTRAINT ck_simulated_effect_state CHECK (state IN ('APPLIED', 'COMPENSATED')),
    CONSTRAINT ck_simulated_effect_completion CHECK (
        (state = 'APPLIED' AND compensated_at IS NULL)
        OR (state = 'COMPENSATED' AND compensated_at IS NOT NULL)
    )
);

CREATE FUNCTION reject_action_ledger_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'action_ledger is append-only';
END;
$$;

CREATE TRIGGER action_ledger_no_update_or_delete
BEFORE UPDATE OR DELETE ON action_ledger
FOR EACH ROW EXECUTE FUNCTION reject_action_ledger_mutation();
