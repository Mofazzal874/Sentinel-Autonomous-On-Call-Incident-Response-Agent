CREATE TABLE service_dependency (
    id UUID PRIMARY KEY,
    caller_service_id UUID NOT NULL REFERENCES fleet_service(id),
    dependency_service_id UUID NOT NULL REFERENCES fleet_service(id),
    criticality VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_service_dependency UNIQUE (caller_service_id, dependency_service_id),
    CONSTRAINT ck_service_dependency_not_self CHECK (caller_service_id <> dependency_service_id),
    CONSTRAINT ck_service_dependency_criticality CHECK (criticality IN ('REQUIRED', 'DEGRADED_OK'))
);

CREATE INDEX ix_service_dependency_caller ON service_dependency(caller_service_id);
CREATE INDEX ix_service_dependency_target ON service_dependency(dependency_service_id);

ALTER TABLE demo_run
    ADD COLUMN display_title VARCHAR(160),
    ADD COLUMN summary VARCHAR(320);

UPDATE demo_run SET
    display_title = CASE scenario_key
        WHEN 'faulty-deployment' THEN 'Faulty payment release'
        WHEN 'ambiguous-dependency' THEN 'Ambiguous checkout dependency'
        WHEN 'capacity-approval' THEN 'Capacity change awaiting approval'
        ELSE 'Incident response run'
    END,
    summary = CASE scenario_key
        WHEN 'faulty-deployment' THEN 'Release correlation and a grounded rollback stopped by dry-run.'
        WHEN 'ambiguous-dependency' THEN 'Insufficient evidence forces escalation instead of an invented fix.'
        WHEN 'capacity-approval' THEN 'A bounded scale-out remains blocked until an SRE approves it.'
        ELSE 'Recorded synthetic incident-response history.'
    END;

ALTER TABLE demo_run
    ALTER COLUMN display_title SET NOT NULL,
    ALTER COLUMN summary SET NOT NULL,
    ADD CONSTRAINT ck_demo_run_title_not_blank CHECK (btrim(display_title) <> ''),
    ADD CONSTRAINT ck_demo_run_summary_not_blank CHECK (btrim(summary) <> '');

CREATE TABLE demo_dataset_version (
    version INTEGER PRIMARY KEY,
    generator_name VARCHAR(120) NOT NULL,
    records JSONB NOT NULL,
    seeded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_demo_dataset_version_positive CHECK (version > 0),
    CONSTRAINT ck_demo_dataset_generator_not_blank CHECK (btrim(generator_name) <> '')
);
