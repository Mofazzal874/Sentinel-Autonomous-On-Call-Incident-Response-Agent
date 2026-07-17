# ADR 0002: Flyway Owns the PostgreSQL Schema

- Status: Accepted
- Date: 2026-07-18

## Context

Sentinel's persistence model contains operational evidence, state transitions, constraints, and query-driven indexes. Later phases depend on consistent schema behavior under concurrency. Letting Hibernate modify each environment opportunistically would make the database state difficult to review and reproduce.

## Decision

- Flyway SQL migrations are the only schema-change mechanism.
- Committed versioned migrations are forward-only and are not edited after release.
- Hibernate runs with `ddl-auto: validate`.
- Open Session in View is disabled.
- PostgreSQL-specific integration is tested with a disposable pgvector/PostgreSQL Testcontainer rather than H2.
- Reference data is versioned in Flyway; repeatable demo telemetry is inserted by a profile-gated transactional seeder.
- Telemetry queries require service/time bounds and a caller-provided page limit.

## Consequences

Benefits:

- Schema changes are explicit, reviewable, reproducible, and deployable.
- Database constraints remain authoritative even if application validation is bypassed.
- Tests exercise the same SQL dialect and extension used locally and in deployment.
- Query indexes are designed alongside the access path.

Costs:

- Every entity/schema change requires coordinated Java and SQL edits.
- Integration tests are slower and require Docker.
- Forward fixes need new migrations instead of rewriting history.

## Rejected alternatives

- `ddl-auto: update`: convenient locally but non-deterministic and unsafe as a deployment mechanism.
- H2-only repository tests: faster but cannot establish PostgreSQL/pgvector compatibility.
- Returning JPA entities from controllers: couples the HTTP contract to lazy-loading and persistence internals.
