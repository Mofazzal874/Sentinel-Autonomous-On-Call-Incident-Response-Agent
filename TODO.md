# Sentinel Delivery Checklist

This is the active, phase-gated task list. Complete and verify each phase before moving to the next one.

## Phase 0 — Repository and build bootstrap

- [x] Read all project-plan documents in full.
- [x] Record durable architecture, safety, environment, and Git rules in `AGENTS.md`.
- [x] Initialize Git and ignore `project_plan/`.
- [x] Ignore Claude-specific local files (`CLAUDE.md`, `CLAUDE.local.md`, and `.claude/`).
- [x] Install checksum-verified Temurin Java 25 under `E:\DevTools`.
- [x] Direct Gradle caches to `E:\DevCaches\gradle`.
- [x] Pin and checksum-verify Gradle 9.6.1 through the repository wrapper.
- [x] Create the minimal Spring Boot 4.1 application and context smoke test.
- [x] Run `clean test` successfully on Java 25.
- [x] Document the local development workflow.

Phase 0 exit gate: **passed**.

## User attention required before Phase 1 verification

- [ ] Install Docker Desktop with the WSL 2 backend.
- [ ] Put Docker application/data storage on `E:` wherever the installer and Docker settings allow it.
- [ ] Start Docker Desktop and confirm `docker version` and `docker compose version` work from PowerShell.
- [ ] Confirm there is enough free space for PostgreSQL/pgvector, Redis, RabbitMQ, and Testcontainers images.

Do not install standalone PostgreSQL, Redis, or RabbitMQ on Windows. Phase 1 and later run them in Docker.

## Phase 1 — Foundation and simulated-fleet domain

### 1. Infrastructure and configuration

- [ ] Add `compose.yaml` with PostgreSQL 17 + pgvector, Redis 7, and RabbitMQ 4 management images.
- [ ] Configure Docker named volumes so persistent data uses Docker's `E:`-backed data location.
- [ ] Add JPA, validation, Flyway, PostgreSQL, and Testcontainers dependencies only; no messaging or AI code.
- [ ] Add local datasource configuration with secrets overridable through environment variables.
- [ ] Set `hibernate.ddl-auto: validate` and `open-in-view: false`.

### 2. Domain model

- [ ] Model service ownership, service tier, and per-service remediation allowlists.
- [ ] Model immutable deployments with string enums, UTC `Instant`, lazy associations, and indexed service/time access.
- [ ] Model metric samples with bounded time-window query support.
- [ ] Model log events with timestamp, level, trace ID, and bounded search support.
- [ ] Model runbooks and their associated action type.
- [ ] Add the Phase 1 incident skeleton and valid status transitions.
- [ ] Keep persistence entities encapsulated; expose API DTOs rather than entities.

### 3. Schema and data

- [ ] Add forward-only `V1__core_schema.sql`, including `CREATE EXTENSION IF NOT EXISTS vector`.
- [ ] Add required foreign keys, check constraints, unique constraints, and query-driven indexes.
- [ ] Add `V2__seed_reference.sql` for stable teams/services/reference data.
- [ ] Add a repeatable, profile-gated synthetic scenario seeder for deploy/metric/log correlation data.

### 4. Queries and API

- [ ] Implement the bounded, indexed `recentBefore(serviceId, at, pageable)` deployment query.
- [ ] Implement bounded metric and log window queries; never call `findAll()` for telemetry analysis.
- [ ] Put read transaction boundaries in an application service with `readOnly = true`.
- [ ] Add `GET /api/v1/fleet/services` with a stable DTO contract.

### 5. Verification and phase gate

- [ ] Verify `docker compose up -d` reaches healthy infrastructure.
- [ ] Verify Flyway migration followed by Hibernate schema validation on application startup.
- [ ] Add repository/service unit tests for mapping and query boundaries.
- [ ] Add a PostgreSQL Testcontainers integration test proving the bad deployment correlation query.
- [ ] Run all tests and inspect query behavior for accidental N+1 access.
- [ ] Review every Phase 1 “Defend This” question before marking the phase complete.

## Later phases

- [ ] Phase 2 — alert intake, Redis deduplication, RabbitMQ delivery, DLQ, and sink idempotency.
- [ ] Phase 3 — JWT/RBAC, under-privileged agent account, and deterministic bounded tools.
- [ ] Phase 4 — structured agent router/workers/evaluator and grounded pgvector RAG.
- [ ] Phase 5 — deterministic guardrails, risk scoring, action ledger, approval, and compensation.
- [ ] Phase 6 — observations/metrics, layered testing, offline evaluation, and Azure deployment.

