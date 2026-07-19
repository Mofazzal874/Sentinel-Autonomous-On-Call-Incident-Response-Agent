# ADR 0013: Public demo as a safe operational digital twin

## Status

Accepted.

## Context

The first public page explained Sentinel but could not prove the backend. A new database also contained no completed incidents, so a reviewer could not inspect classification, evidence, proposal, gate, and ledger records without privately creating alerts and credentials.

Calling random rows “real data” would be misleading. Exposing the authenticated operational APIs or a free-form model prompt would also expand the public attack surface and could consume unbounded compute.

## Decision

Treat the portfolio environment as a deterministic synthetic operations digital twin:

- Seed three idempotent, internally consistent incident histories into the authoritative domain tables only under the `demo` profile.
- Cover three safety outcomes: grounded dry-run, ungrounded escalation, and human approval required.
- Register each showcase incident in a small `demo_run` table that holds only its public identifier, scenario key, source, and incident reference.
- Expose bounded, read-only DTO projections under `/api/v1/demo/**`; keep the ordinary business and management APIs authenticated.
- Label every recorded history as synthetic and keep public execution in dry-run.
- Add live interaction later through fixed server-owned scenarios with rate and concurrency limits. A public caller will never supply an arbitrary prompt or action.

## Consequences

The demo is useful immediately after first startup and every displayed step is traceable to the real incident, transcript, remediation-request, and append-only ledger tables. It is reproducible and honest, but it is not production telemetry. Real value beyond the portfolio requires adapters to an organization’s alert, deploy, metrics, logs, runbook, identity, and execution systems.
