ALTER TABLE team
    ADD COLUMN archived_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE fleet_service
    ADD COLUMN archived_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE runbook
    ADD COLUMN archived_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX ix_team_active_name ON team(name) WHERE archived_at IS NULL;
CREATE INDEX ix_fleet_service_active_name ON fleet_service(name) WHERE archived_at IS NULL;
CREATE INDEX ix_runbook_active_title ON runbook(title) WHERE archived_at IS NULL;

CREATE TABLE demo_scenario_template (
    id UUID PRIMARY KEY,
    scenario_key VARCHAR(80) NOT NULL UNIQUE,
    display_name VARCHAR(160) NOT NULL,
    description VARCHAR(500) NOT NULL,
    scenario_type VARCHAR(40) NOT NULL,
    service_id UUID NOT NULL REFERENCES fleet_service(id),
    severity VARCHAR(10) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_demo_scenario_key_not_blank CHECK (btrim(scenario_key) <> ''),
    CONSTRAINT ck_demo_scenario_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_demo_scenario_description_not_blank CHECK (btrim(description) <> ''),
    CONSTRAINT ck_demo_scenario_type CHECK (scenario_type IN (
        'BAD_DEPLOY', 'DEPENDENCY_TIMEOUT', 'CAPACITY_SATURATION', 'CACHE_STALENESS'
    )),
    CONSTRAINT ck_demo_scenario_severity CHECK (severity IN ('SEV1', 'SEV2', 'SEV3', 'SEV4')),
    CONSTRAINT ck_demo_scenario_time_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_demo_scenario_active_name
    ON demo_scenario_template(display_name) WHERE archived_at IS NULL;
