# Sentinel System Design Workbook

This workbook evolves with the implementation. It focuses on reasoning rather than framework syntax.

## 1. Problem statement

Sentinel receives operational alerts, gathers evidence, proposes a remediation, applies deterministic safety rules, automatically performs only bounded low-risk actions, and escalates everything else.

The central design tension is:

```text
respond quickly  <-------------------->  avoid causing a larger outage
```

The architecture must support both speed and safety.

## 2. Functional requirements

- Accept alert webhooks quickly.
- Suppress duplicate alert storms.
- Process alerts asynchronously.
- Correlate incidents with deployments, metrics, logs, and runbooks.
- Produce grounded remediation proposals.
- Execute only allowlisted, deterministic low-risk actions.
- Require human approval for higher-risk actions.
- Preserve a complete audit history.

## 3. Non-functional requirements

- Idempotency under retries and concurrency.
- Durable messaging and persistence.
- Least-privilege security.
- Bounded model cost and runtime.
- Explainability and auditability.
- Reversible or compensatable execution.
- Observable latency, tool calls, decisions, and outcomes.

## 4. High-level component map

```text
Alert sender
    |
    v
REST intake -> Redis suppression -> RabbitMQ -> Incident transaction
                                               |
                                               v
                                      deterministic tools
                                               |
                                               v
                                      AI orchestration
                                               |
                                               v
                                      GuardrailGate
                                       /        \
                              low risk          high risk
                                 |                 |
                                 v                 v
                             executor        human approval
                                 |
                                 v
                           action ledger
```

## 5. Current infrastructure boundary: Docker

Docker provides repeatable local infrastructure. It is not part of Sentinel's production domain model.

```text
Docker CLI -> Docker Engine API -> Linux containers
                                   |-- PostgreSQL/pgvector
                                   |-- Redis
                                   `-- RabbitMQ
```

Local storage location is hidden behind the Docker Engine API. Whether the engine stores layers on `C:` or `E:` does not change image names, Compose services, exposed ports, Docker Hub, or cloud deployment.

### Image versus container versus volume

- Image: immutable packaged filesystem and metadata used as a template.
- Container: one running or stopped instance created from an image.
- Volume: persistent data managed separately from a container lifecycle.
- Bind mount: a direct mapping from a host path into a container.

Example for Phase 1:

```text
pgvector image -> postgres container -> pgdata volume
```

Deleting and recreating the container should not delete the database volume.

## 6. Design-review template for every component

When a component is added, answer:

1. What problem does it solve?
2. What inputs and outputs cross its boundary?
3. What state does it own?
4. What happens when it is slow?
5. What happens when it is unavailable?
6. Can a request be repeated safely?
7. What is the transaction boundary?
8. How is it authenticated and authorized?
9. Which metrics reveal failure?
10. What simpler alternative was rejected, and why?

## 7. Pen-and-paper scenario

Trace this alert through the future system:

```text
payments-api error rate rises from 1% to 18%
five minutes after deployment version 2026.07.18.3
```

Write down:

- Which component receives the alert first?
- Where is its fingerprint calculated?
- What prevents 50 copies from launching 50 agent runs?
- Which repository query finds the latest deployment before the spike?
- What evidence must exist before rollback can be proposed?
- Why is the model not allowed to call rollback directly?
- Which durable record proves whether rollback already happened?

Revisit this scenario at the end of each phase and fill in the newly implemented answers.

## 8. Phase 1 implemented data plane

Phase 1 creates the evidence data plane that later tools query.

```text
                     +------------------+
GET /fleet/services  | FleetController  |
        ------------>+---------+--------+
                               |
                               v
                     +-------------------+
                     | FleetQueryService |  read-only transaction
                     +---------+---------+
                               |
                               v
                     +-------------------+
                     | JPA repositories  |  bounded queries/fetch plan
                     +---------+---------+
                               |
                               v
                     +-------------------+
                     | PostgreSQL 17     |
                     | + pgvector        |
                     +-------------------+
```

### State ownership

- PostgreSQL owns fleet reference data, deployment facts, telemetry, runbooks, and incident state.
- Flyway owns the schema version and forward changes.
- Hibernate maps Java objects and validates that mapping; it does not own DDL.
- The seed profile owns disposable demo scenarios, not permanent reference definitions.
- Docker named volumes own local persistence independently of container lifecycle.

### Evidence correlation

The first implemented correlation path is:

```text
(service ID, spike time, max results)
                 |
                 v
deployment where deployed_at <= spike time
                 |
                 v
newest first, limited by Pageable
```

The composite index `(service_id, deployed_at DESC)` matches the equality filter and time ordering. Metric and log access follows the same rule: service scope, bounded time, newest first, caller limit.

### Current scaling boundary

Reference tables are small and the fleet list can be fetched as one joined graph. Telemetry tables grow quickly and must never be loaded wholesale. At larger scale:

- partition metrics/logs by time;
- retain bounded query windows;
- summarize or archive older raw samples;
- use BRIN for very large time-correlated append-only data where appropriate;
- move full-text log search to a suitable PostgreSQL text index or search system only when measured need justifies it.

### Failure isolation learned locally

An unrelated `postgres.exe` already owned host IPv4 port `5432`. The container could be healthy while the Spring client still reached the wrong server. Sentinel now publishes its database on `55432` and leaves the existing process untouched.

System-design lesson: a component's internal health does not prove the whole request path. Verify from the actual client boundary.

### Phase 1 answers to the running scenario

For a `payments-api` error-rate spike:

- `DeploymentRepository.recentBefore` finds the latest deployments before the spike.
- `MetricSampleRepository.recentWindow` returns a limited metric window.
- `LogEventRepository.recentWindow/searchWindow` returns a limited evidence window.
- The synthetic scenario aligns a bad deployment, error-rate/p99/CPU changes, and timeout logs.
- No agent or automatic rollback exists yet. Those remain deliberately deferred.
