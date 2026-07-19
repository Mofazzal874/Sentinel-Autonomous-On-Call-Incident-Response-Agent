# Learning note: an operational digital twin for the public demo

## Prerequisites

Understand a database row, primary key, foreign key, API DTO, Spring profile, and read-only transaction.

## Plain-language definitions

- **Seed data:** known records inserted so a fresh database is useful immediately.
- **Synthetic data:** invented but realistic data. It resembles production facts without copying a real customer incident.
- **Digital twin:** a controlled model of an operating system used to demonstrate and test behavior.
- **Projection:** a deliberately limited view of stored data shaped for one reader. It is not the persistence entity itself.
- **Profile:** a Spring switch that activates components only in a named environment.

## Why this exists

An incident-response system with an empty database is technically valid but impossible to evaluate quickly. Random seed rows are also weak because they do not form a causal story. Sentinel therefore starts its public environment with three linked stories:

1. A payment release is followed by error and latency spikes. Retrieval finds the rollback runbook, evaluation accepts the grounded proposal, and the deterministic gate records a dry-run.
2. Checkout failures point toward an uncertain dependency. No authoritative runbook matches, so the agent escalates instead of inventing a fix.
3. Catalog saturation supports a one-replica scale-out, but residual downstream risk forces human approval.

## Request and data flow

```text
demo profile startup
  -> idempotent seeder
  -> incident + agent_run + transcript + remediation_request + action_ledger
  -> demo_run public identifier
  -> bounded read-only query
  -> safe JSON projection
  -> operator console (next slice)
```

## Code map

- `V6__demo_run_registry.sql`: public-to-authoritative record mapping.
- `DemoPortfolioSeeder`: inserts the three coherent stories only in the `demo` profile.
- `DemoRunQueryService`: reads at most 20 runs and 50 transcript/ledger entries.
- `DemoRunController`: exposes only the curated read projection.
- `SecurityConfiguration`: permits those GET projections while keeping normal incident APIs protected.

## Local, system-design, and interview views

Locally, the seed prevents a blank screen. In system design, it separates a public showcase projection from the protected control plane. In an interview, defend it as a deterministic, repeatable fixture that proves causal data relationships without pretending to hold production data or opening mutation endpoints.

## Failure modes and safeguards

- Restart duplicates data: fixed IDs and conflict-safe inserts make startup idempotent.
- Demo history bypasses audit rules: the action record remains inside the append-only ledger table and its mutation trigger is tested.
- Public endpoint leaks operational data: only registered demo incidents can be queried, with bounded DTO fields.
- Reviewer mistakes it for customer data: every detail response contains an explicit synthetic-data disclaimer.
- Demo changes infrastructure: dry-run remains enabled and there are zero action claims in the recorded fixtures.

## Verification

```powershell
$env:GRADLE_USER_HOME='E:\DevCaches\gradle'
.\gradlew.bat test --tests io.mofazzal.sentinel.demo.DemoPortfolioSeederIntegrationTest
```

The test starts real PostgreSQL with pgvector, runs migrations and startup twice, checks all three stories, calls the anonymous API, proves ordinary incidents still return `401`, and proves ledger updates fail.

## Pen-and-paper exercises

1. Draw the foreign-key path from `demo_run` to an `action_ledger` event.
2. Explain why a `demo_run` row should reference an incident instead of copying its status.
3. List fields that would be unsafe to expose if a future live adapter ingested genuine logs.
4. Describe the difference between a recorded scenario and a fixed scenario executed live.
