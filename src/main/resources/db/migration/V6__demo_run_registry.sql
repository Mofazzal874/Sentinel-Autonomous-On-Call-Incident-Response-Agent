CREATE TABLE demo_run (
    public_id UUID PRIMARY KEY,
    scenario_key VARCHAR(80) NOT NULL,
    incident_id UUID NOT NULL UNIQUE REFERENCES incident(id),
    source VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_demo_run_scenario_not_blank CHECK (btrim(scenario_key) <> ''),
    CONSTRAINT ck_demo_run_source CHECK (source IN ('RECORDED', 'LIVE'))
);

CREATE INDEX ix_demo_run_started_at ON demo_run(started_at DESC);
CREATE INDEX ix_demo_run_scenario_started ON demo_run(scenario_key, started_at DESC);
