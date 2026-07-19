# Learning note: a production-shaped operations digital twin

## Prerequisites

Know the basic purpose of a relational database, a primary key, a foreign key, a transaction, and an HTTP API. Read the fleet-domain and operator-console notes first if those ideas are new.

## Plain-language definitions

- **Digital twin:** a synthetic but internally consistent model of a real system.
- **Seed data:** records inserted so a new environment starts in a useful state.
- **Deterministic:** the same input produces the same identifiers, counts, and stories on every run.
- **Idempotent:** repeating the operation does not add duplicates or change the result.
- **Causal evidence:** facts whose timestamps and relationships could actually explain the incident.
- **Cardinality:** how many records or distinct values a dataset contains.
- **Bounded query:** a query limited by service, time, and result count instead of scanning everything.

## What it does locally

When Spring starts with the `demo` profile, Flyway first creates the schema. Two seeders then populate the small showcase stories and the larger versioned digital twin. The version marker prevents a restart from duplicating data.

The result is a reusable fleet with 12 services and linked dependencies, release histories, five metric series per service, traceable logs, runbooks, incidents, agent transcripts, remediation requests, safety decisions, and immutable ledger facts.

## System-design flow

```text
Flyway V1..V7
  -> database schema and constraints
  -> demo profile seeder (one transaction)
       -> catalog and dependency graph
       -> deployments, metrics, and logs
       -> incidents referencing those facts
       -> agent transcript and remediation proposal
       -> deterministic gate outcome and ledger
       -> bounded public demo registry
  -> Spring read service
  -> /api/v1/demo/runs
  -> Next.js operator console
```

The dependency order matters. An incident cannot safely reference a service or deployment that has not been inserted. The transaction means a failure rolls the new dataset back instead of leaving half a story.

## Concrete example

Imagine incident 12 belongs to `payments-api` at 01:22 UTC. Its correlated deployment must be at or before 01:22. The investigation query looks only at a small window around 01:22 for that service. It can then show latency, error rate, saturation, throughput, availability, and trace-linked logs. The remediation and ledger explain whether the gate chose dry-run, approval, simulated execution, compensation, or escalation.

This is stronger than placing `latency = 900` in React: PostgreSQL foreign keys, time-window queries, and the same DTO path used by the application all have to work.

## Code map

- `V7__operations_digital_twin.sql`: dependency graph, public summary fields, and dataset version marker.
- `DemoOperationsDigitalTwinSeeder`: versioned generator and causal record construction.
- `DemoPortfolioSeeder`: three hand-curated anchor stories.
- `DemoRunQueryService`: bounded public projections from authoritative records.
- `DemoPortfolioSeederIntegrationTest`: real PostgreSQL counts, idempotence, causal order, evidence windows, security, and append-only checks.

## Failure modes and prevention

- **Duplicate rows after restart:** the dataset version marker and unique constraints make reseeding a no-op.
- **Half-seeded database:** one service-layer transaction rolls back the version as a unit.
- **Future deployment blamed for an incident:** correlation explicitly requires `deployed_at <= created_at` and a regression test proves it.
- **Incident with no evidence:** tests require all five metric names and at least one log in a bounded nearby window.
- **Demo records entering production:** the seeder requires the explicit `demo` profile.
- **Browser becomes the source of truth:** scenario titles and summaries are stored and queried from PostgreSQL.
- **Audit history rewritten:** the existing database trigger still rejects action-ledger updates and deletes.

## Tradeoffs

Set-based SQL is compact and fast for thousands of deterministic records, but it is less approachable than constructing every Java object. The learning boundary is kept clear by giving each seed stage a named method and testing outcomes through real PostgreSQL. The dataset is large enough to feel real while remaining cheap enough for a student VM.

## Verification

```powershell
$env:GRADLE_USER_HOME='E:\DevCaches\gradle'
.\gradlew.bat test --tests io.mofazzal.sentinel.demo.DemoPortfolioSeederIntegrationTest
.\gradlew.bat clean test
```

The focused test checks exact baseline counts, rerun idempotence, all public histories, evidence time windows, deployment ordering, protected API access, and the append-only ledger boundary.

## Interview defense

Locally, the generator makes a fresh database useful. At system-design level, it models a causal graph across catalog, telemetry, workflow, policy, and audit layers. In an interview, explain that deterministic synthetic data gives reproducible evidence without exposing customer information, while real tables and constraints make the demonstration an integration test rather than a mockup.

## Pen-and-paper exercises

1. Draw one incident and arrows to its service, deployment, metrics, logs, runbook, transcript, proposal, and ledger events.
2. Explain why a version marker alone is not enough without database unique constraints.
3. Write the time-order rule between a deployment and the incident it may explain.
4. Compare deterministic synthetic data with random faker data for regression testing.
5. Explain why an incident may be archived but its action ledger must never be deleted.
