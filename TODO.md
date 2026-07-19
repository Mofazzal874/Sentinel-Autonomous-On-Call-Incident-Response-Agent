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
- [x] Review every Phase 1 “Defend This” question before marking the phase complete.

Phase 1 gate: **complete** — engineering evidence and the seven-question learning/defense review both passed.

## Phase 2 — Alert ingestion, suppression, and durable delivery

Execution checkpoints:

- **P2.1 Deterministic intake:** validated alert contract, canonical SHA-256 fingerprint, command contract, and atomic PostgreSQL incident insert.
- **P2.2 Storm suppression:** atomic Redis first-seen claim, expiring duplicate counter, and client idempotency-key layer.
- **P2.3 Broker delivery:** durable topic/DLX/DLQ topology, JSON messages, publisher confirms/returns, and `202` REST response.
- **P2.4 Idempotent consumer:** manual acknowledgement after database commit, bounded transient retry, and poison-message dead lettering.
- **P2.5 Evidence and learning:** unit tests, PostgreSQL/Redis/RabbitMQ Testcontainers burst tests, failure-path checks, ADR/journal/lesson, and Defend This review.

### 1. Deterministic contract and database sink

- [x] Add only the Spring AMQP and Spring Data Redis production dependencies plus required Testcontainers modules.
- [x] Add a bounded, validated Alertmanager-style request DTO and stable acknowledgement DTO.
- [x] Canonicalize service, alert name, and sorted labels before SHA-256 fingerprinting.
- [x] Define a version-tolerant triage command contract without JPA entities.
- [x] Atomically create an incident with PostgreSQL `ON CONFLICT DO NOTHING` and the existing unique fingerprint constraint.

### 2. Redis storm suppression

- [x] Claim the fingerprint with one atomic Redis script and a configurable TTL.
- [x] Count suppressed duplicates with an expiring Redis counter.
- [x] Layer an optional bounded client `Idempotency-Key` over semantic fingerprinting.
- [x] Treat Redis as an efficiency layer; database uniqueness remains the correctness boundary.

### 3. RabbitMQ and REST intake

- [x] Declare durable `alerts.exchange`, `triage.queue`, retry queue, dead-letter exchange, and DLQ with explicit routing keys.
- [x] Configure JSON conversion, publisher confirms/returns, manual acknowledgements, prefetch, bounded concurrency, and virtual threads.
- [x] Publish only first occurrences and return `202 Accepted` with queued/suppressed status.
- [x] Keep the public alert webhook security requirement explicitly deferred to Phase 3.

### 4. Consumer correctness

- [x] Acknowledge only after the idempotent database transaction commits.
- [x] Apply bounded retries to transient failures; never create an infinite requeue loop.
- [x] Reject permanent/poison failures to the DLQ and preserve the original message plus broker `x-death` context.
- [x] Prove redelivery cannot create a second incident.

### 5. Verification and phase gate

- [x] POST 50 identical alerts and prove one incident plus 49 suppressions.
- [x] Verify durable queued delivery across a controlled broker restart.
- [x] Verify a poison command lands in the DLQ without looping.
- [x] Run all unit and PostgreSQL/Redis/RabbitMQ Testcontainers tests.
- [x] Review every Phase 2 “Defend This” question before marking the phase complete.

Phase 2 gate: **complete** — engineering evidence and the seven-question learning/defense review both passed.

## Phase 3 — Security and deterministic read tools

Execution checkpoints:

- **P3.1 Security boundary:** stateless JWT validation, role mapping, URL/method authorization, and timestamped HMAC alert intake.
- **P3.2 Deterministic tools:** bounded typed deploy, metric, log, and runbook read contracts with no Spring AI dependency.
- **P3.3 Evidence:** JWT role matrix, HMAC failure paths, tool unit/PostgreSQL tests, full build, ADR/journal/lesson, and Defend This review.

### 1. Security

- [x] Add only the Spring Security and OAuth2 resource-server production starters plus security test support.
- [x] Validate HS256 JWT signature, issuer, audience, time claims, and a `roles` claim mapped to `ROLE_*`.
- [x] Enforce stateless sessions, authenticated default access, `ADMIN` URL rules, and method-level authorization.
- [x] Prove `VIEWER`, `SRE_APPROVER`, `ADMIN`, and under-privileged `AGENT` access boundaries.
- [x] Protect the public alert route with a bounded, timestamped, constant-time HMAC verification filter.
- [x] Keep secrets external; generate local-only secrets under ignored project storage on `E:`.

### 2. Deterministic read tools

- [x] Implement a three-result deployment lookup before an incident time.
- [x] Implement a bounded/downsampled metric window with baseline and percentage delta.
- [x] Implement bounded ERROR-log clustering around an incident time.
- [x] Implement bounded lexical runbook retrieval now; defer pgvector semantic retrieval to Phase 4.
- [x] Return immutable DTOs, validate every argument, use read-only service transactions, and expose no mutation.

### 3. Verification and phase gate

- [x] Add focused unit tests for validation, bounds, mapping, computations, authorization, and HMAC failure paths.
- [x] Add real PostgreSQL seeded-scenario evidence for all four tool contracts.
- [x] Run the complete test suite and review query/transaction/security behavior.
- [x] Add ADR, project journal, beginner lesson, glossary, and system-design updates.
- [x] Review all seven Phase 3 “Defend This” questions before marking the phase complete.

Phase 3 gate: **complete** — engineering evidence and the seven-question learning/defense review both passed.

## Phase 4 — Grounded proposal workflow

Execution checkpoints:

- **P4.1 Safe workflow skeleton:** Spring AI dependency baseline, typed model-role ports, bounded sequential orchestration, and durable transcript lifecycle.
- **P4.2 Grounded retrieval:** Flyway-owned pgvector schema, explicit embedding profile, idempotent runbook indexing, and similarity-threshold retrieval.
- **P4.3 Model adapters:** structured router, deterministic evidence workers/tool adapters, proposal generator, evaluator, and per-incident call budget.
- **P4.4 Evidence:** bad-deploy and no-grounding scenarios, full transcript inspection, complete regression suite, lesson, ADR, and Defend This review.

### 1. Safe workflow skeleton

- [x] Verify Spring AI 2.0 BOM, Ollama, pgvector, tool-calling, and structured-output APIs against official documentation.
- [x] Add Spring AI libraries without installing a runtime or automatically downloading model weights.
- [x] Keep chat, embedding, and vector-store auto-configuration disabled unless explicitly enabled by environment.
- [x] Define typed router, evidence, generator, evaluator, proposal, and outcome contracts.
- [x] Bound proposal refinement to at most three attempts.
- [x] Reject a proposal whose cited runbook is absent from retrieved evidence, regardless of model evaluation.
- [x] Add Flyway-owned agent-run/transcript tables with one active run per incident and ordered entries.
- [x] Keep LLM calls outside database transactions; transcript writes use short independent transactions.
- [x] Prove the workflow skeleton with deterministic fake roles and real PostgreSQL persistence.

### 2. Grounded retrieval and model adapters

- [x] Select Qwen3 4B chat and nomic-embed-text 768-dimension embeddings after the hardware/disk audit; defer installation, keep future Ollama data on `E:`, and never auto-pull.
- [x] Add the dimension-specific runbook embedding migration and idempotent indexing job.
- [x] Replace lexical runbook candidates with top-k semantic hits and an explicit similarity threshold.
- [x] Adapt the router and proposal/evaluator roles to Spring AI structured output with validation.
- [x] Adapt the existing bounded read tools to the sequential evidence-gathering workflow and expose Spring AI read-only tool definitions.
- [x] Add a Redis per-incident model-call budget and fail-safe escalation for invalid/tool/model failures.
- [x] Wire lifecycle begin/complete/fail around the workflow without holding a transaction over network calls.

### 3. Verification and learning gate

- [x] Prove a seeded bad deploy produces a grounded proposal without executing it.
- [x] Prove an unmatched symptom escalates without inventing a runbook.
- [x] Prove duplicate concurrent delivery cannot create two active agent runs.
- [x] Inspect the persisted classification, evidence, proposals, critiques, and outcome.
- [x] Run the complete suite and complete all seven Phase 4 Defend This answers.

Phase 4 gate: **complete** — semantic RAG, structured Spring AI roles, bounded tool orchestration, Redis call limits, durable transcripts, end-to-end scenarios, full regression evidence, and the seven-question learning defense all pass. A live Ollama demo remains an optional later deployment task, not a correctness dependency.

## Deterministic safety and execution

Execution checkpoints:

- **P5.1 Decision boundary:** deterministic risk facts/breakdown, kill switch, service allowlist, idempotency query, dry-run, and one `GuardrailGate`.
- **P5.2 Durable execution:** append-only action ledger, committed `IN_PROGRESS` marker, idempotent strategies, result transaction, and compensation facts.
- **P5.3 Human control:** approval/rejection endpoint, approver authorization, approval timeout, and mandatory re-entry through the gate.
- **P5.4 Evidence:** low/high-risk scenarios, duplicate delivery, kill switch, dry-run, induced failure/compensation, full regression, lesson, ADR, and Defend This review.

### 1. Decision boundary

- [x] Decompose the safety work before implementation and preserve the single-gate invariant.
- [x] Compute risk only from validated Java facts and return an inspectable component breakdown.
- [x] Implement a DB-backed global kill switch with a Redis engaged-state acceleration key and fail-closed behavior.
- [x] Enforce the service action allowlist from persisted fleet ownership data.
- [x] Check durable action idempotency before any execution path and reserve with a unique database claim.
- [x] Keep local remediation in dry-run by default.
- [x] Route every proposed or human-approved action through one `GuardrailGate` and require its unforgeable package-scoped execution authorization.

### 2. Durable execution

- [x] Add a forward-only migration with mutable correctness claims and a trigger-enforced append-only action ledger.
- [x] Commit `IN_PROGRESS` before an external/simulated side effect and record the outcome in a separate transaction.
- [x] Add an idempotent, package-private strategy registry for all simulated remediation actions.
- [x] Make duplicate and crash-recovery behavior safe through database constraints, per-claim effect idempotency, pending recovery, and fail-closed stale-execution escalation.
- [x] Record compensation as new linked ledger facts and compensate completed work after an induced later-step failure.

### 3. Human control and verification

- [x] Add typed review plus approve/reject handling for `SRE_APPROVER`; reject agent self-approval.
- [x] Re-check kill switch, allowlist, risk, idempotency, and dry-run after approval rather than bypassing the gate.
- [x] Escalate expired approvals instead of executing silently.
- [x] Prove low-risk, high-risk, duplicate, dry-run, kill-switch, refusal, recovery, ungrounded rejection, and compensation scenarios.
- [x] Run the complete suite and complete all seven safety Defend This answers.

Safety/execution gate: **complete** — all mutation eligibility flows through the deterministic gate, execution requires its matching capability, database reservation survives Redis loss and races, the event ledger is database-enforced append-only, approval re-enters policy, recovery is fail-closed, and the full learning defense is recorded. Final uncached evidence: 89 tests, zero failures, errors, or skips.

## Operability, evaluation, packaging, and deployment

Execution checkpoints:

- **P6.1 Observability:** protected Actuator/Prometheus endpoint, bounded custom metrics, incident-stage observations, trace export profile, and dashboard assets.
- **P6.2 Verification:** deterministic coverage review, mocked-agent composition, complete Testcontainers pipeline, and fixed offline evaluation corpus/report.
- **P6.3 Packaging:** reproducible application artifact, OCI image, non-root runtime, health/readiness probes, configuration/secrets review, and local image smoke test.
- **P6.4 Deployment handoff:** compare current Azure options/costs, ask the user before provisioning, then deploy only with explicit approval and budget controls.
- **P6.5 Portfolio gate:** production README, architecture and safety narrative, demo evidence, learning lesson, journal, ADRs, and seven-question defense.

### 1. Observability

- [x] Re-read the operability/deployment plan and verify current Spring Boot/Micrometer guidance from primary documentation.
- [x] Add Actuator and Prometheus registry without installing a host monitoring system.
- [x] Keep management endpoints authenticated and expose only health, info, metrics, and Prometheus.
- [x] Add bounded-cardinality incident, triage, evaluator, tool, model-call-capacity, and remediation metrics.
- [ ] Record provider-reported input/output tokens and estimated cost when the live provider adapter exposes usage metadata; never estimate from prompt text in correctness tests.
- [x] Add Micrometer observations for the incident path and verify trace-parent continuity with an in-memory handler.
- [x] Add an opt-in OTLP export profile; ordinary tests/local startup must not require a collector.
- [x] Add a versioned dashboard/alert-rule asset and explain which signals should page a human.

### 2. Layered verification and evaluation

- [x] Review deterministic safety/core coverage and add missing boundary regressions, including evidence-policy and semantic-wiring regressions.
- [x] Prove mocked model routing, bounded tools, transcript, gate invocation, and outcome without asserting generated prose.
- [x] Add one complete PostgreSQL/Redis/RabbitMQ pipeline scenario from signed alert through guarded dry-run result.
- [x] Add a fixed train/validation/holdout ground-truth corpus and deterministic scorer with explicit quality gates.
- [x] Run the fixed corpus against the selected live chat and embedding models, record latency/quality, inspect mismatches, and iterate without tuning on holdout.
- [x] Keep optional live-model evaluation separate, explicitly invoked, bounded, and non-gating for the build.

### 3. Packaging and deployment readiness

- [x] Produce and reproducibility-check the executable application artifact.
- [x] Add a layered multi-stage Java 25 image, numeric non-root runtime, and status-only health/readiness probes.
- [x] Build and smoke-test the image locally with a read-only root filesystem while keeping Docker data on `E:`.
- [x] Document environment variables, secret injection, resource limits, migrations, rollback, recovery, and cost controls.
- [x] Compare a one-VM demo, Azure Container Apps managed topology, and AKS using current official sources.
- [x] Add a locally rehearsable one-VM bundle with pinned services, generated ignored secrets, semantic startup indexing, and E-drive model reuse.
- [x] Add a stable edge identity design, public landing page, immutable GHCR images, and opt-in GitHub deployment automation.
- [x] Prove the GitHub Linux regression, image build, and GHCR publish job; confirm Azure deployment stays skipped while disabled.

### 4. Deployment and portfolio gate

- [x] Stop and ask the user before creating any Azure resource, registry, paid model deployment, DNS entry, or public endpoint.
- [x] Obtain final approval for the dedicated Azure resource group, static public IP, ports 80/443, and VM spend.
- [ ] Configure the VM hostname/custom domain plus GitHub environment secrets and enable deployment only after the first manual health check.
- [ ] After approval, provision the chosen minimal-cost target and verify health, metrics, migrations, secrets, and end-to-end behavior.
- [x] Add the recruiter-facing README, architecture flow, safety model, evaluation result, demo instructions/evidence, and future work.
- [x] Complete the final uncached suite after deployment startup wiring: 103 tests across 35 suites, zero failures/errors/skips.
- [x] Complete all seven operability/deployment Defend This answers.

Operability/deployment gate: **deployment handoff open** — local observability, layered evaluation, packaging, learning defense, and smoke evidence are complete. Cloud provisioning, public access, and paid resources remain intentionally unstarted pending the user's subscription, region, budget, and access approval.
