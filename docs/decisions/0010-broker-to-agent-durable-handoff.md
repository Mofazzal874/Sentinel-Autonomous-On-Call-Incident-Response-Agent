# ADR 0010: Broker-to-Agent Durable Handoff

- Status: accepted
- Date: 2026-07-19

## Context

Alert ingestion durably created incidents, and the agent/safety workflow worked when invoked directly, but the RabbitMQ consumer did not connect those two vertical slices. Acknowledging immediately after incident insertion meant an enabled agent never saw real queued alerts.

The handoff also spans a database transaction and slow model/tool work. Holding the incident insert transaction across that work would create long locks and make broker acknowledgement semantics harder to reason about.

## Decision

- Commit the idempotent incident creation transaction first.
- When the agent is enabled, resolve that durable incident by fingerprint and dispatch a DTO into `AgentTriageCoordinator` outside the insert transaction.
- Acknowledge RabbitMQ only after the agent records a durable proposal/escalation and the deterministic gate records its decision. When the agent is disabled, acknowledge after incident persistence as before.
- On redelivery, skip terminal incidents. If another delivery sees `TRIAGING`, send the command through the existing bounded retry queue rather than acknowledging it silently.
- Recover an abandoned `RUNNING` agent run after ten minutes by marking it failed and escalating its `TRIAGING` incident. Never automatically repeat uncertain model-assisted work after the recovery timeout.

## Consequences

- One signed alert can now traverse webhook, Redis, RabbitMQ, PostgreSQL incident state, semantic evidence, mocked structured roles, deterministic guardrail, and dry-run ledger.
- Crashes before acknowledgement cause redelivery. Database incident uniqueness, the incident state machine, one active run, and gate/action idempotency prevent duplicate durable effects.
- A crash during triage may place the broker message in the DLQ before the ten-minute recovery scan; that is visible and fail-closed rather than silently losing or replaying uncertain work.
- Agent-disabled deployments retain the ingestion-only behavior and do not require a model.
