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

## Phase 1 infrastructure prerequisite

- [x] Audit and reuse the existing compatible WSL installation.
- [x] Install Docker Desktop with the WSL 2 backend.
- [x] Put Docker application and Docker WSL storage on `E:`.
- [x] Start Docker Desktop and verify both client and Linux engine.
- [x] Confirm `docker compose version` works from PowerShell.
- [x] Run and remove the `hello-world` verification container/image.
- [x] Confirm enough free space remains for the Phase 1 baseline; continue monitoring image growth.
- [x] Enable Ubuntu 24.04 WSL integration and verify it reaches Docker Engine 29.5.2.
- [x] Confirm the downloaded Docker Desktop installer was removed after installation.
- [ ] Decide separately whether to migrate the existing Ubuntu 24.04 WSL disk from `C:` to `E:` using a backup-first procedure.

Do not install standalone PostgreSQL, Redis, or RabbitMQ on Windows. Phase 1 and later run them in Docker.

## Phase 1 — Foundation and simulated-fleet domain

Execution checkpoints (complete in order):

- **P1.1 Infrastructure:** Compose model, pinned images, named volumes, health checks, Spring database dependencies/configuration, and healthy local services.
- **P1.2 Domain and schema:** entities/value enums, forward-only migrations, reference data, and Hibernate validation.
- **P1.3 Synthetic scenario:** profile-gated repeatable seed generation aligned across deployments, metrics, and logs.
- **P1.4 Read path:** bounded repositories, read-only application transaction, DTO mapping, and `GET /api/v1/fleet/services`.
- **P1.5 Evidence:** unit tests, PostgreSQL Testcontainers correlation test, full build, query review, journal, learning notes, and Defend This review.

### 1. Infrastructure and configuration

- [x] Add `compose.yaml` with PostgreSQL 17 + pgvector, Redis 7, and RabbitMQ 4 management images.
- [x] Keep Sentinel PostgreSQL on host port `55432`; an existing unmanaged `postgres.exe` already owns IPv4 port `5432` and must not be replaced or modified.
- [x] Configure Docker named volumes so persistent data uses Docker's `E:`-backed data location.
- [x] Add JPA, validation, Flyway, PostgreSQL, and Testcontainers dependencies only; no messaging or AI code.
- [x] Add local datasource configuration with secrets overridable through environment variables.
- [x] Set `hibernate.ddl-auto: validate` and `open-in-view: false`.

### 2. Domain model

- [x] Model service ownership, service tier, and per-service remediation allowlists.
- [x] Model immutable deployments with string enums, UTC `Instant`, lazy associations, and indexed service/time access.
- [x] Model metric samples with bounded time-window query support.
- [x] Model log events with timestamp, level, trace ID, and bounded search support.
- [x] Model runbooks and their associated action type.
- [x] Add the Phase 1 incident skeleton and valid status transitions.
- [x] Keep persistence entities encapsulated; expose API DTOs rather than entities.

### 3. Schema and data

- [x] Add forward-only `V1__core_schema.sql`, including `CREATE EXTENSION IF NOT EXISTS vector`.
- [x] Add required foreign keys, check constraints, unique constraints, and query-driven indexes.
- [x] Add `V2__seed_reference.sql` for stable teams/services/reference data.
- [x] Add a repeatable, profile-gated synthetic scenario seeder for deploy/metric/log correlation data.

### 4. Queries and API

- [x] Implement the bounded, indexed `recentBefore(serviceId, at, pageable)` deployment query.
- [x] Implement bounded metric and log window queries; never call `findAll()` for telemetry analysis.
- [x] Put read transaction boundaries in an application service with `readOnly = true`.
- [x] Add `GET /api/v1/fleet/services` with a stable DTO contract.

### 5. Verification and phase gate

- [x] Verify `docker compose up -d` reaches healthy infrastructure.
- [x] Verify Flyway migration followed by Hibernate schema validation on application startup.
- [x] Add repository/service unit tests for mapping and query boundaries.
- [x] Add a PostgreSQL Testcontainers integration test proving the bad deployment correlation query.
- [x] Run all tests and inspect query behavior for accidental N+1 access.
- [ ] Review every Phase 1 “Defend This” question before marking the phase complete.

Phase 1 engineering gate: **passed**. Learning/defense gate: **awaiting user review**.

## Later phases

- [ ] Phase 2 — alert intake, Redis deduplication, RabbitMQ delivery, DLQ, and sink idempotency.
- [ ] Phase 3 — JWT/RBAC, under-privileged agent account, and deterministic bounded tools.
- [ ] Phase 4 — structured agent router/workers/evaluator and grounded pgvector RAG.
- [ ] Phase 5 — deterministic guardrails, risk scoring, action ledger, approval, and compensation.
- [ ] Phase 6 — observations/metrics, layered testing, offline evaluation, and Azure deployment.
