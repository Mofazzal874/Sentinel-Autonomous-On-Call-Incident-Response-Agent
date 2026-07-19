ALTER TABLE demo_live_submission
    ADD COLUMN service_id UUID REFERENCES fleet_service(id),
    ADD COLUMN scenario_type VARCHAR(40),
    ADD COLUMN severity VARCHAR(10),
    ADD COLUMN signal_intensity VARCHAR(20),
    ADD COLUMN customer_impact VARCHAR(30),
    ADD COLUMN deployment_context VARCHAR(20),
    ADD COLUMN display_title VARCHAR(160),
    ADD COLUMN summary VARCHAR(500);

UPDATE demo_live_submission submission
SET service_id = template.service_id,
    scenario_type = template.scenario_type,
    severity = template.severity,
    signal_intensity = 'HIGH',
    customer_impact = 'PARTIAL_OUTAGE',
    deployment_context = CASE WHEN template.scenario_type = 'BAD_DEPLOY' THEN 'RECENT_CHANGE' ELSE 'NONE' END,
    display_title = template.display_name,
    summary = template.description
FROM demo_scenario_template template
WHERE template.id = submission.template_id;

ALTER TABLE demo_live_submission
    ALTER COLUMN service_id SET NOT NULL,
    ALTER COLUMN scenario_type SET NOT NULL,
    ALTER COLUMN severity SET NOT NULL,
    ALTER COLUMN signal_intensity SET NOT NULL,
    ALTER COLUMN customer_impact SET NOT NULL,
    ALTER COLUMN deployment_context SET NOT NULL,
    ALTER COLUMN display_title SET NOT NULL,
    ALTER COLUMN summary SET NOT NULL,
    ADD CONSTRAINT ck_demo_submission_scenario_type CHECK (scenario_type IN (
        'BAD_DEPLOY', 'DEPENDENCY_TIMEOUT', 'CAPACITY_SATURATION', 'CACHE_STALENESS'
    )),
    ADD CONSTRAINT ck_demo_submission_severity CHECK (severity IN ('SEV1', 'SEV2', 'SEV3', 'SEV4')),
    ADD CONSTRAINT ck_demo_submission_signal_intensity CHECK (signal_intensity IN ('ELEVATED', 'HIGH', 'CRITICAL')),
    ADD CONSTRAINT ck_demo_submission_customer_impact CHECK (customer_impact IN (
        'DEGRADED', 'PARTIAL_OUTAGE', 'FULL_OUTAGE', 'STALE_RESULTS'
    )),
    ADD CONSTRAINT ck_demo_submission_deployment_context CHECK (deployment_context IN ('NONE', 'RECENT_CHANGE')),
    ADD CONSTRAINT ck_demo_submission_title_not_blank CHECK (btrim(display_title) <> ''),
    ADD CONSTRAINT ck_demo_submission_summary_not_blank CHECK (btrim(summary) <> '');

CREATE INDEX ix_demo_submission_service_time ON demo_live_submission(service_id, submitted_at DESC);
