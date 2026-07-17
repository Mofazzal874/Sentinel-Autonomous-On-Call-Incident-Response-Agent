CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE team (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    contact_channel VARCHAR(200) NOT NULL,
    CONSTRAINT ck_team_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_team_contact_not_blank CHECK (btrim(contact_channel) <> '')
);

CREATE TABLE fleet_service (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    owner_team_id UUID NOT NULL REFERENCES team(id),
    tier VARCHAR(20) NOT NULL,
    CONSTRAINT ck_fleet_service_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_fleet_service_tier CHECK (tier IN ('CRITICAL', 'STANDARD'))
);

CREATE INDEX ix_fleet_service_owner ON fleet_service(owner_team_id);

CREATE TABLE service_allowed_action (
    service_id UUID NOT NULL REFERENCES fleet_service(id) ON DELETE CASCADE,
    action_type VARCHAR(40) NOT NULL,
    PRIMARY KEY (service_id, action_type),
    CONSTRAINT ck_service_action_type CHECK (
        action_type IN ('RESTART_SERVICE', 'ROLLBACK_DEPLOYMENT', 'SCALE_OUT', 'CLEAR_CACHE')
    )
);

CREATE TABLE deployment (
    id UUID PRIMARY KEY,
    service_id UUID NOT NULL REFERENCES fleet_service(id),
    version VARCHAR(80) NOT NULL,
    git_sha VARCHAR(64) NOT NULL,
    deployed_at TIMESTAMPTZ NOT NULL,
    deployed_by VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT uq_deployment_service_git_sha UNIQUE (service_id, git_sha),
    CONSTRAINT ck_deployment_version_not_blank CHECK (btrim(version) <> ''),
    CONSTRAINT ck_deployment_git_sha_not_blank CHECK (btrim(git_sha) <> ''),
    CONSTRAINT ck_deployment_actor_not_blank CHECK (btrim(deployed_by) <> ''),
    CONSTRAINT ck_deployment_status CHECK (status IN ('SUCCEEDED', 'FAILED', 'ROLLED_BACK'))
);

CREATE INDEX ix_deployment_service_time ON deployment(service_id, deployed_at DESC);

CREATE TABLE metric_sample (
    id UUID PRIMARY KEY,
    service_id UUID NOT NULL REFERENCES fleet_service(id),
    metric_name VARCHAR(100) NOT NULL,
    value NUMERIC(18, 6) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_metric_name_not_blank CHECK (btrim(metric_name) <> '')
);

CREATE INDEX ix_metric_service_name_time
    ON metric_sample(service_id, metric_name, recorded_at DESC);

CREATE TABLE log_event (
    id UUID PRIMARY KEY,
    service_id UUID NOT NULL REFERENCES fleet_service(id),
    level VARCHAR(10) NOT NULL,
    message TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    trace_id VARCHAR(64),
    CONSTRAINT ck_log_level CHECK (level IN ('DEBUG', 'INFO', 'WARN', 'ERROR')),
    CONSTRAINT ck_log_message_not_blank CHECK (btrim(message) <> '')
);

CREATE INDEX ix_log_service_time ON log_event(service_id, occurred_at DESC);
CREATE INDEX ix_log_service_level_time ON log_event(service_id, level, occurred_at DESC);
CREATE INDEX ix_log_trace_id ON log_event(trace_id) WHERE trace_id IS NOT NULL;

CREATE TABLE runbook (
    id UUID PRIMARY KEY,
    title VARCHAR(160) NOT NULL UNIQUE,
    symptom_description TEXT NOT NULL,
    steps TEXT NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    CONSTRAINT ck_runbook_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_runbook_symptom_not_blank CHECK (btrim(symptom_description) <> ''),
    CONSTRAINT ck_runbook_steps_not_blank CHECK (btrim(steps) <> ''),
    CONSTRAINT ck_runbook_action_type CHECK (
        action_type IN ('RESTART_SERVICE', 'ROLLBACK_DEPLOYMENT', 'SCALE_OUT', 'CLEAR_CACHE')
    )
);

CREATE TABLE incident (
    id UUID PRIMARY KEY,
    fingerprint VARCHAR(128) NOT NULL UNIQUE,
    service_id UUID NOT NULL REFERENCES fleet_service(id),
    status VARCHAR(30) NOT NULL,
    severity VARCHAR(10) NOT NULL,
    correlated_deployment_id UUID REFERENCES deployment(id),
    proposed_runbook_id UUID REFERENCES runbook(id),
    risk_score NUMERIC(5, 2),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_incident_fingerprint_not_blank CHECK (btrim(fingerprint) <> ''),
    CONSTRAINT ck_incident_status CHECK (
        status IN ('OPEN', 'TRIAGING', 'AWAITING_APPROVAL', 'REMEDIATING', 'RESOLVED', 'ESCALATED')
    ),
    CONSTRAINT ck_incident_severity CHECK (severity IN ('SEV1', 'SEV2', 'SEV3', 'SEV4')),
    CONSTRAINT ck_incident_risk_score CHECK (risk_score IS NULL OR risk_score BETWEEN 0 AND 100),
    CONSTRAINT ck_incident_time_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_incident_service_created ON incident(service_id, created_at DESC);
CREATE INDEX ix_incident_status_updated ON incident(status, updated_at DESC);
