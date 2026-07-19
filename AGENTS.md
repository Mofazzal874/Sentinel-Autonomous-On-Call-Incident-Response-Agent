# Sentinel Repository Memory

This file is the durable implementation memory for contributors and coding agents. Read it before making changes. The private `project_plan/` directory is the source of truth but is intentionally ignored by Git.

## Product intent

Sentinel is an autonomous on-call and incident-response system built with Java 25, Spring Boot 4.1, and Spring AI 2.0. It ingests alerts, correlates deploys, metrics, logs, and runbooks, proposes remediations, executes only deterministically approved low-risk actions, and escalates everything else to a human.

This is not a chatbot. Optimize the implementation emphasis as:

- 70% Spring, persistence, messaging, security, and distributed-systems correctness.
- 30% AI orchestration and retrieval.

## Non-negotiable engineering invariants

1. Transaction boundaries, propagation, isolation, and locking must be deliberate and tested.
2. Concurrency cannot cause duplicate incidents or duplicate remediation.
3. Security uses JWT, RBAC, method guards, and a deliberately under-privileged agent service account.
4. Every infrastructure mutation passes through one `GuardrailGate`; there is no alternate execution path.
5. The model may propose actions but never determines whether an action is safe. Risk scoring and gate decisions are deterministic Java.
6. Read tools are typed, bounded, validated, side-effect free, and return DTOs rather than JPA entities.
7. No mutating operation is exposed directly as an LLM tool.
8. An ungrounded remediation is escalated. It is never executed.
9. Agent and evaluator loops are bounded, rate-limited, and recorded.
10. The safety-critical path contains no LLM and must be exhaustively testable.

## Delivery sequence and phase gates

Work strictly in order. Do not mix later-phase dependencies or concepts into an earlier phase without a documented reason.

- Phase 0 — repository/bootstrap: Git hygiene, Java 25, Gradle wrapper, minimal application skeleton, environment documentation. Docker is explicitly deferred.
- Phase 1 — fleet domain: PostgreSQL 17 with pgvector, JPA, Flyway, simulated telemetry, bounded correlation queries, seed profile, fleet read endpoint, Testcontainers verification. No messaging or AI.
- Phase 2 — ingestion: REST alert intake, deterministic fingerprinting, Redis storm suppression, RabbitMQ durable topology/manual acknowledgements/DLQ, database sink idempotency. No AI.
- Phase 3 — security and deterministic tools: JWT/RBAC/service account plus deploy, metrics, logs, and runbook tool contracts. No model-driven orchestration.
- Phase 4 — agent core: structured router, sequential orchestrator/workers first, bounded evaluator-optimizer, pgvector RAG, persisted transcript. The agent only proposes.
- Phase 5 — safety and execution: single gate, deterministic risk, kill switch, allowlists, dry-run, append-only action ledger, idempotent strategies, compensation, and human approval.
- Phase 6 — observability/testing/deployment: metrics and traces, deterministic tests, mocked-agent orchestration, offline evaluation harness, container image, then Azure deployment.

Do not start Phase 4 until Phases 1–3 pass all tests and their interview-oriented `Defend This` checks. A phase is complete only when its deliverables are verified and its design can be explained.

## Planned technical baseline

- Java 25 LTS; Java 21 is the explicit fallback only if Java 25 becomes infeasible.
- Spring Boot 4.1.0, Spring Framework 7, Jakarta EE 11, Jackson 3, Hibernate 7.
- Gradle Kotlin DSL with a checked-in wrapper; no global Gradle dependency.
- Spring AI BOM 2.0.0. Verify model-starter artifact names against current official documentation when Phase 4 begins.
- PostgreSQL 17 plus pgvector; Flyway owns schema changes.
- RabbitMQ 4 and Redis 7.
- JUnit 6, Mockito, and Testcontainers.
- Local/cheap model for development; Azure OpenAI only for the final demo window.

## Persistence and time rules

- Flyway migrations are forward-only and immutable after commit.
- Hibernate uses `ddl-auto: validate`, never `update` or `create`.
- Disable Open Session in View with `open-in-view: false`.
- Put transactions at the service layer; mark pure reads `readOnly = true`.
- Associations default to lazy loading. Avoid accidental N+1 queries through projections, entity graphs, explicit fetch joins, or batching as appropriate.
- Persist enums as strings and timestamps as UTC `Instant`.
- Query telemetry through bounded, indexed time windows; never load all rows and filter in Java.
- Keep the action ledger auditable. Compensation is a new fact referencing the original action, not erased history.
- Commit an `IN_PROGRESS` action marker before an external side effect and record its result in a separate transaction.

## Distributed-systems rules

- Redis suppression is an efficiency layer, not the correctness boundary.
- Database unique constraints provide durable incident/action idempotency.
- RabbitMQ is at-least-once. Consumers acknowledge manually after successful durable processing.
- Poison messages go to a DLQ; transient failures may be requeued under a bounded retry policy.
- Execution strategies must themselves be idempotent because crashes can happen between side effects and result recording.

## Security and safety rules

- Roles are `VIEWER`, `SRE_APPROVER`, `ADMIN`, and under-privileged `AGENT`.
- The agent cannot approve its own high-risk proposal.
- The alert webhook needs HMAC, mTLS, or a network-policy layer even if its Spring route is public.
- Human approval does not bypass the kill switch, allowlist, or idempotency checks.
- Secrets come from environment variables or a secret manager and are never committed.
- Default local remediation mode should remain dry-run until Phase 5 tests demonstrate the safety invariants.

## Public demo rules

- The public site must demonstrate the real persisted alert-to-agent-to-gate workflow, not only describe it.
- Seeded showcase records are deterministic synthetic operations data and must be labeled as such; never imply they are customer incidents.
- Public users may select only fixed, validated scenarios. Do not expose arbitrary model prompts or infrastructure mutation inputs.
- Keep the demo dry-run. Public read models must be bounded and redact secrets, raw credentials, and unrestricted internal records.
- Existing fleet, incident, approval, administration, metrics, and actuator APIs remain authenticated unless a narrowly reviewed demo projection explicitly exposes safe fields.

## Frontend baseline

- The operator console lives under `frontend/` and uses pinned Next.js/React with `output: "export"`; Azure runs no Node server.
- `frontend/out` is generated and ignored. Gradle packages it under Spring Boot static resources only after the frontend build has run.
- Caddy owns the stable hostname and TLS but proxies all requests to the single Spring Boot image. Spring serves the console and API from the same origin.
- CI and deployment rehearsals must run frontend install, tests, type checking, and export before `bootJar`.
- Public UI content comes from bounded Spring DTOs. Do not hard-code duplicate incident records into the frontend.
- Operational CRUD follows domain semantics: catalog/configuration resources may be edited; incidents, transcripts, and ledger facts are archived or append-only, never erased.

## Testing strategy

- Test deterministic components normally and thoroughly; do not involve a model in safety tests.
- Test agent orchestration with mocked model responses and assert routing, tool calls, state transitions, bounds, and gate invocation rather than prose.
- Use Testcontainers for PostgreSQL, Redis, and RabbitMQ integration tests once Docker is available.
- Maintain a fixed offline scenario corpus for classification, retrieval, and grounding regression evaluation.
- Every bug fix should include a regression test when practical.

## Local environment policy

- Prefer `E:` for SDKs, caches, downloads, databases, and project data when the tool permits choosing a location.
- Keep Gradle dependency/cache data under `E:\DevCaches\gradle`.
- Docker is not installed during Phase 0. Ask for or perform installation only when a Docker-dependent phase is ready to begin.
- Install other tools only when their phase requires them; avoid speculative global installs.
- Before installing anything, audit commands, versions, package registrations, services, and known install locations. Reuse a compatible existing installation instead of reinstalling it.
- Before large installations, check available disk space because all local drives are constrained.
- Remove verified-unnecessary installers, corrupt downloads, temporary archives, and task-specific scratch data after successful verification. Never remove caches or user data merely because they look unused.

## Mentor and continuity policy

- Maintain `docs/PROJECT_JOURNAL.md` after every material work session and at every phase checkpoint.
- Record the user's goal, what was required, what changed, how components connect, verification evidence, important tradeoffs, lessons to retain, and the next safe action.
- Teach while building: explain why a component exists, which failure it prevents, and which later phase depends on it.
- Assume the user is new to Spring Boot, persistence, messaging, security, distributed systems, containers, and agent systems. Never rely on unexplained framework vocabulary.
- For each phase, maintain beginner notes under `docs/learning/` with: prerequisite concepts, plain-language definitions, request/data flow, code map, concrete example, failure modes, design tradeoffs, verification commands, and pen-and-paper exercises.
- Teach each important component at three levels: what it does locally, how it participates in the system design, and how to defend the decision in an engineering interview.
- Introduce concepts in dependency order. Explain interfaces before implementations, transactions before concurrency, deterministic tools before AI orchestration, and safety invariants before autonomous execution.
- Keep `TODO.md` synchronized with actual progress. Do not mark an item complete without evidence.
- Record durable architectural choices as ADRs under `docs/decisions/`; use the journal for chronological learning context.
- Do not rely on chat history as the only memory. The repository journal is the handoff source for future sessions and for the user's project review.
- After chat context is compacted, reconstruct the working state from this file, `TODO.md`, the relevant private phase plan, `docs/PROJECT_JOURNAL.md`, and Git status/history before changing code; record any newly recovered durable constraint here or in the journal.

## Git and authorship policy

- Never force-add or commit `project_plan/`.
- Do not add assistant, AI, bot, co-author, generator, or similar attribution to commits, source headers, manifests, documentation metadata, or release metadata.
- Do not change the user's Git name or email.
- Preserve unrelated user changes and avoid destructive Git commands.
- Prefer small phase-scoped commits, but do not create a commit unless the user requests it or approves that workflow.

## Working method

1. Re-read the relevant phase plan and this file before starting a phase.
2. Verify current dependency/artifact names from primary official sources when versions may have changed.
3. Write or update a short implementation checklist.
4. Implement the smallest coherent vertical slice.
5. Run proportionate unit, integration, formatting, and build checks.
6. Review transaction, idempotency, security, and blast-radius implications explicitly.
7. Record material architectural decisions as ADRs under `docs/decisions/` once implementation choices begin.
8. Report what is verified, what remains deferred, and any manual prerequisites.
9. Update the project journal with the session summary and teaching insights.
10. Update the applicable beginner learning note and glossary when a new framework or system-design concept is introduced.
