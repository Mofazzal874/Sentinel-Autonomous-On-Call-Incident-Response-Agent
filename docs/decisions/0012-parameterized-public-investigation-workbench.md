# ADR 0012: Parameterized public investigation workbench

- Status: Accepted
- Date: 2026-07-20

## Context

Four fixed demo scenarios proved the backend path but made Sentinel appear scripted. Allowing arbitrary public prompts or commands would create prompt-injection, cost, data-retention, and remote-execution risks.

## Decision

Expose a bounded parameterized investigation composer over the synthetic fleet. A visitor selects a persisted service, supported symptom family, severity, impact, signal strength, and deployment context. The server validates every dimension, generates causal telemetry, ingests a real alert, runs the existing message/agent/guardrail path, and persists the result.

Expose bounded evidence projections for the resulting incident. Do not expose a mutating tool, arbitrary query, infrastructure identifier, policy input, or approval operation.

## Consequences

- The public demo supports hundreds of meaningful combinations rather than four opaque stories.
- Every displayed outcome remains traceable to database facts and the real workflow.
- Some combinations correctly escalate because a service lacks an allowlisted action or the evidence is insufficient.
- This is a simulation workbench, not a replacement for connecting a real monitoring source through the authenticated ingestion boundary.
