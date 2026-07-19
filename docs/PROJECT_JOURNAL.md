# Sentinel Project Journal

This journal preserves continuity across sessions and teaches the project as it is built. It records outcomes and reasoning rather than copying raw chat transcripts. Read it with `AGENTS.md` and `TODO.md` before resuming work.

## How to use this journal

Each entry answers seven questions:

1. What was the user trying to achieve?
2. What prerequisites were actually required?
3. What changed?
4. How does it connect to the architecture and later phases?
5. How was it verified?
6. What engineering insight should the user retain?
7. What is the next safe action?

---

## Session 1 — Phase 0 repository and build bootstrap

### Goal

Understand the complete plan before implementation, create durable project memory, initialize the repository safely, keep development storage on `E:`, and prove the Java/Spring baseline without installing Docker prematurely.

### Prerequisites audit

- Git was already installed and reused.
- Java 20 was installed but could not satisfy the planned Java 25 toolchain.
- Gradle was not globally installed.
- Docker, PostgreSQL, Redis, RabbitMQ, Azure CLI, and Ollama were absent and not required for Phase 0.
- All project-plan documents were read in full before application code was created.

### Changes

- Added Git hygiene, including ignoring the private `project_plan/` directory and Claude-local files.
- Added `AGENTS.md` as durable repository rules and architecture memory.
- Installed checksum-verified Temurin Java 25.0.3 under `E:\DevTools`.
- Directed Gradle caches to `E:\DevCaches\gradle`.
- Pinned Gradle 9.6.1 with distribution and wrapper checksums.
- Created the minimal Spring Boot 4.1.0 application, configuration, and context smoke test.
- Added `README.md`, `TODO.md`, and an `E:`-aware PowerShell environment helper.

### Architectural connection

Phase 0 creates a reproducible build boundary. Every later phase depends on the same wrapper, JDK, package namespace, test runner, and repository rules. Keeping database and messaging dependencies out at this point preserves the planned phase boundaries and makes failures attributable.

### Verification

- Temurin archive SHA-256 matched the publisher metadata.
- Gradle distribution and wrapper JAR hashes matched Gradle's published checksums.
- `gradlew.bat clean test --no-daemon` completed successfully on Java 25.
- One Spring application-context test ran with zero failures.
- Git ignore checks confirmed that project plans and Claude-local files are excluded.

### Insights to retain

- A Gradle wrapper is part of the project; it prevents every developer from needing the same global Gradle installation.
- A Java toolchain declares the compiler/runtime contract, while `JAVA_HOME` determines which JVM starts Gradle.
- Phase gates reduce debugging ambiguity: infrastructure was deliberately absent while the build baseline was proven.
- Checksums turn a download into a verifiable supply-chain input rather than an act of trust.

### Next action

Install Docker Desktop only when Phase 1 begins, place its application and WSL data roots on `E:`, then verify the engine before adding PostgreSQL/JPA work.

---

## Session 2 — Phase 1 infrastructure prerequisite: Docker on E

### Goal

Determine whether Docker Desktop can avoid `C:`, reuse the existing WSL installation, install only what is missing, and establish permanent audit/cleanup/teaching rules.

### Prerequisites audit

- WSL 2.3.24 is already installed and exceeds Docker Desktop's minimum WSL 2.1.5 requirement.
- Ubuntu 24.04 already exists as a WSL 2 distribution. Its current distribution disk is under the user's `C:` profile.
- Docker Desktop and the Docker CLI are not installed.
- The current shell is not elevated; an all-users custom-location installation may require a visible UAC confirmation.
- `E:` is the intended location for the Docker application and Docker's own WSL data disk.

### Planned installation layout

- Application: `E:\Docker\Docker`
- Docker WSL data: `E:\Docker\wsl`
- Installer download: `E:\DevTools\downloads` and removed after successful installation
- Backend: WSL 2, Linux containers only

### Architectural connection

Docker is not an application-layer dependency. It supplies reproducible local PostgreSQL/pgvector, Redis, RabbitMQ, and Testcontainers environments. Phase 1 initially consumes PostgreSQL; later phases reuse the same Compose environment for Redis and RabbitMQ.

### Important distinction

The Windows WSL feature, the existing Ubuntu distribution, and Docker Desktop's private WSL distribution are separate things. Docker can store its private Linux disk on `E:` without reinstalling WSL or moving Ubuntu. Moving the existing Ubuntu distribution is optional and riskier because it requires an explicit backup/migration procedure.

### Verification status

Completed:

- Installed Docker Desktop 4.76.0 (build 228118) under `E:\Docker\Docker`.
- Docker WSL storage was created under `E:\Docker\wsl`.
- Docker's default application and WSL disk locations on `C:` were not created.
- Docker Engine 29.5.2 is running with the Linux `overlayfs` driver.
- Docker Compose v5.1.4 is available.
- A `hello-world` container ran successfully; the temporary container and image were removed afterward.
- The Docker installer matched Docker's published SHA-256 and had a valid Docker Inc Authenticode signature.
- The Docker binary path is persisted in the machine PATH. Already-open terminals need to be reopened or have the path refreshed.
- Large Docker files are on `E:`. Windows-managed Docker metadata on `C:` is approximately 1.62 MB.
- The existing Ubuntu `ext4.vhdx` remains on `C:` and was measured at roughly 10.34 GB before Docker installation.

### Insights to retain

- Audit first: an installed prerequisite should be reused when its version is compatible.
- Application binaries and container/image data have separate location controls; both must be redirected to prevent future `C:` growth.
- A zero-byte `C:` footprint is not realistic on Windows because registry entries, shortcuts, logs, and small user settings remain OS-managed, even when large binaries and virtual disks are on `E:`.

### Next action

Delete the verified installer archive, then begin Phase 1 with Compose and PostgreSQL/pgvector. Treat relocation of the existing Ubuntu distribution as a separate backup-first maintenance task.

### Cleanup note

- The temporary `hello-world` container and image were removed.
- Earlier Gradle installer archives are no longer present.
- The downloaded Docker Desktop installer was confirmed absent in the final cleanup audit.

---

## Session template for future work

### Goal

State the user's intended outcome.

### Prerequisites audit

List what was checked, reused, missing, incompatible, or deliberately deferred.

### Changes

List concrete repository, machine, configuration, and infrastructure changes.

### Architectural connection

Explain which upstream contract this relies on and which downstream phases consume it.

### Verification

Record commands/tests and their observed result.

### Insights to retain

Capture the concepts, failure modes, and tradeoffs worth learning.

### Next action

Name the next safe, phase-aligned step and any user attention required.

---

## Session 3 — Docker portability and learning-system design

### Goal

Confirm whether installing Docker Desktop and its data on `E:` limits other projects, Docker Hub, or deployments; then reshape the project notes for a complete Spring Boot beginner learning from the finished system.

### Prerequisites audit

- Docker Desktop is running with application files and private WSL disks on `E:`.
- Ubuntu 24.04 remains a separate WSL 2 distribution on `C:`.
- Docker client/server and Compose were already verified, so no reinstall or reconfiguration was required.

### Findings

- Docker Desktop does not require a particular user WSL distribution. It runs in its own isolated `docker-desktop` distribution.
- The location of Docker's application files and virtual disk does not change OCI/Docker image contents, tags, registry authentication, pushes, pulls, Compose semantics, or deployment targets.
- Windows projects on any drive can use Docker Desktop bind mounts. Docker Desktop translates host paths into its Linux VM transparently.
- Docker Hub credentials use Docker's credential helper and small Windows user-profile metadata; image layers remain in Docker's `E:` data disk.
- The deliberate limitation is Linux containers only. That is sufficient for Sentinel, PostgreSQL, Redis, RabbitMQ, Java, and most modern backend projects. Windows containers would require a supported Windows edition/configuration and enabling that separate feature.

### System-design connection

Docker separates the control interface from storage:

- The Docker CLI is the client/control interface.
- The Docker Engine is the daemon that builds and runs containers.
- Docker Hub is a remote registry that stores images.
- The Docker WSL disk is local implementation storage for images, layers, containers, and volumes.

Changing the local storage drive changes none of the registry or deployment contracts. This is an example of abstraction: callers depend on the Docker API and image format, not the physical disk path.

### Verification

- Docker Desktop status remained `running`.
- Docker client and server both reported 29.5.2.
- Docker Compose reported v5.1.4.
- Docker settings continued to point at `E:\Docker\wsl`.
- PowerShell uses the `desktop-linux` context successfully.
- Ubuntu 24.04 WSL integration was enabled. `/var/run/docker.sock` exists and
  `docker info` from Ubuntu reached Docker Engine 29.5.2 successfully.
- The host is Windows Home Single Language. Docker Desktop can run Linux
  containers on this edition, but Docker's supported native Windows-container
  mode requires Windows Pro or Enterprise. That OS limitation is unrelated to
  installing Docker on `E:`.

### Insights to retain

- Deployment portability comes from the image and its declared runtime contract, not the developer machine's drive letter.
- A Docker image is portable; a bind mount is host-specific. Compose files should use relative project paths or named volumes where practical.
- Separating Ubuntu and Docker virtual disks is normal. WSL integration connects their tools through a socket/API rather than requiring both disks on the same physical drive.
- Storage location is an operational concern; API contracts are the architectural boundary.
- "Docker Desktop for Windows" describes the host application. It normally
  runs Linux containers through WSL 2. A "Windows container" is a different
  container type that uses Windows images and Windows host kernel features.

### Learning-system decision

The documentation now has three complementary layers:

1. `PROJECT_JOURNAL.md` — what happened chronologically and why.
2. `learning/SYSTEM_DESIGN_WORKBOOK.md` — architecture, flows, failure modes, tradeoffs, and scale.
3. Phase learning notes — beginner explanations tied directly to code, examples, exercises, and interview questions.

### Next action

Begin Phase 1 and update both the system-design workbook and the Phase 1 Spring/JPA learning note alongside implementation.

---

## Session 4 — Phase 1 fleet foundation

### Goal

Break Phase 1 into ordered checkpoints, implement the complete fleet persistence/read slice, verify each boundary, and preserve beginner and system-design learning material.

### Prerequisites audit

- Re-read the overview, Phase 1 plan, repository memory, current sources, and Git state.
- Reused Java 25, the Gradle wrapper, Docker Desktop, and Ubuntu integration; installed no additional desktop software.
- Verified current official image tags and Spring Boot/Testcontainers coordinates before using them.
- Found an unrelated existing `postgres.exe` listening on Windows IPv4 port `5432`. It was not stopped, changed, or reinstalled.

### Changes

- Added pinned PostgreSQL 17 + pgvector 0.8.2, Redis 7.4.9, and RabbitMQ 4.3.2 services with health checks and named volumes.
- Published Sentinel PostgreSQL on host port `55432` to isolate it from the existing server.
- Added the Boot 4 Flyway starter, JPA, validation, PostgreSQL, and Testcontainers 2.x dependencies.
- Added externalized datasource configuration, `ddl-auto: validate`, `open-in-view: false`, UTC JDBC handling, and batching.
- Added Flyway V1 core schema and V2 stable reference data.
- Modeled teams, services, remediation allowlists, deployments, metrics, logs, runbooks, and the incident lifecycle skeleton.
- Added query-driven composite indexes and bounded deployment/metric/log repositories.
- Added a transactional `seed` profile correlating one bad deployment with metric spikes and log clusters.
- Added a read-only application transaction and `GET /api/v1/fleet/services` DTO endpoint.
- Added unit tests and a disposable pgvector/PostgreSQL integration test.
- Added ADR 0002 and the Phase 1 beginner lesson.

### Problems found and corrected

1. The Phase 1 plan used the pre-2.0 Testcontainers coordinate `org.testcontainers:postgresql`. Testcontainers 2.x prefixes module artifacts, so it was corrected to `testcontainers-postgresql` and the relocated PostgreSQL package.
2. Spring Boot 4 modularized Flyway auto-configuration. Raw Flyway libraries alone did not enable it, so `spring-boot-starter-flyway` was added.
3. A container health check proved PostgreSQL was healthy internally, but the application reached a different server on host port `5432`. The actual port owner audit found an existing Windows `postgres.exe`; Sentinel moved to `55432`.

### Architectural connection

This phase establishes the deterministic evidence store. Phase 2 will attach alert ingestion to the same database boundary; Phase 3 tools will call these bounded repositories; Phase 4 will receive DTO evidence rather than entities; Phase 5 will extend the incident skeleton and optimistic concurrency into guarded execution.

The HTTP layer depends on an application service, the application service owns the transaction, repositories express bounded access, Hibernate maps objects, and Flyway/PostgreSQL own schema correctness.

### Verification

- All Compose services reached `healthy`; Redis returned `PONG`, RabbitMQ ping succeeded, and pgvector 0.8.2 was available.
- Flyway applied V1/V2 to PostgreSQL 17.10; Hibernate 7.4 initialized with schema validation enabled.
- The seed profile produced 2 deployments, 63 metric samples, and 5 logs. A second start left the same counts.
- The fleet endpoint returned three stable service DTOs.
- SQL review showed one joined endpoint query for services, teams, and allowlists rather than N+1 queries.
- `clean test` passed under normal Gradle configuration-cache settings using Java 25 and a disposable pgvector/PostgreSQL Testcontainer.
- Docker currently uses about 963 MB of images and 49 MB of local volumes; `E:` had approximately 14.11 GB free at the checkpoint.

### Insights to retain

- Verify a dependency's current module coordinate instead of copying an older plan verbatim.
- Container health proves only the container boundary; verify the application-to-host-port path too.
- Flyway and Hibernate have different jobs: migrate first, validate second.
- LAZY associations require an intentional fetch plan inside an intentional transaction.
- DTOs keep persistence behavior out of the HTTP contract.
- Query limits and matching composite indexes are safety boundaries for high-volume evidence.
- A repeatable scenario needs both an application transaction and a durable uniqueness constraint.

### Next action

The Phase 1 engineering work is complete. Review the seven Phase 1 Defend This questions in the learning note before opening Phase 2 ingestion/messaging work. No Git commit was created.

---

## Session 5 — Phase 2 alert ingestion and durable delivery

### Goal

Implement alert intake in ordered checkpoints: deterministic identity, storm suppression, durable messaging, idempotent incident creation, bounded failure handling, and evidence that the complete path survives duplicates and broker restart.

### Prerequisites audit

- Reused the Phase 1 PostgreSQL, Redis, RabbitMQ, Java, Gradle, and Docker installation; installed no host software.
- Kept Sentinel PostgreSQL on `55432`; the unrelated Windows PostgreSQL on `5432` was not touched.
- Verified current Spring Boot 4.1 AMQP/Redis and Testcontainers 2.0.5 module APIs against official documentation.

### Changes

- Added a validated Alertmanager-style HTTP contract and `202` queued/suppressed acknowledgement.
- Added canonical SHA-256 semantic fingerprints and a versioned triage command.
- Added atomic Redis first-seen/client-key claims, expiring duplicate counts, and release after failed publish.
- Added durable RabbitMQ primary, delayed retry, dead-letter topology, persistent JSON messages, mandatory returns, and correlated publisher confirms.
- Added a manual-ack consumer that commits the idempotent incident transaction before acknowledgement, retries transient database failures three total times, and dead-letters permanent/exhausted work.
- Added ADR 0003, the Phase 2 learning lesson, glossary terms, and the delivery-plane system design.

### Architectural connection

Phase 1 supplied the fleet and database correctness boundary. Phase 2 turns external alerts into durable incident records. Phase 3 will secure this webhook and expose under-privileged deterministic tools. Later AI phases consume the incident; they do not replace ingestion correctness.

### Verification

- Unit tests proved canonicalization, orchestration, publisher confirms, post-database acknowledgement order, bounded retries, and DLQ decisions.
- Redis Testcontainers proved one first-seen claim, 49 suppressions, TTLs, client-key semantics, and claim release.
- PostgreSQL Testcontainers proved duplicate commands create one incident.
- A three-container test POSTed 50 identical alerts and observed one queued, 49 suppressed, and one incident.
- The same test proved duplicate broker delivery remains one database effect and poison work reaches the DLQ with `x-death` context.
- With consumers stopped, a confirmed persistent message survived `rabbitmqctl stop_app/start_app` and was processed after recovery.
- `clean test` passed for the complete project in 1 minute 32 seconds.
- The packaged application started against the local Compose stack; a real POST returned `QUEUED` and its fingerprint appeared exactly once in Sentinel PostgreSQL.
- All three Sentinel Compose services remained healthy. Docker used about 963 MB for images and 49 MB for volumes; `E:` had approximately 13.84 GB free.

### Insights to retain

- Distributed exactly-once delivery is not claimed; an idempotent database boundary makes the observable effect effectively once.
- Redis protects capacity, RabbitMQ protects delivery, and PostgreSQL protects truth.
- Acknowledging after commit converts a crash window into harmless redelivery instead of message loss.
- Retries need classification, delay, and a limit; otherwise a poison message becomes a self-inflicted outage.
- `202 Accepted` is an asynchronous contract, not proof that downstream processing finished.

### Next action

The Phase 2 engineering gate is complete. Review the seven Phase 2 Defend This questions with the user before marking the learning gate complete or opening Phase 3. No machine installation or manual cleanup is required.

---

## Session 6 — Phase 1 learning-gate correction

### Goal

Complete every Phase 1 requirement, including the interview-oriented learning review, before allowing further phase progression.

### Prerequisites audit

- Compared all seven private-plan questions with the implemented lesson and code decisions.
- Confirmed the existing answers cover Open Session in View, N+1, lazy loading, enum storage, transactions, Flyway validation, and large-scale query design.
- No application code, dependency, container, or database change was required.

### Changes

- Added a formal interview-evidence checklist to the Phase 1 lesson.
- Marked the seven-question review complete and changed the Phase 1 status from a split gate to fully complete.
- Recorded the sequencing correction instead of rewriting the earlier journal history.

### Architectural connection

Phase 2 relies on the Phase 1 transaction, schema, query, and PostgreSQL correctness boundaries. The review confirms those foundations are explainable as well as tested. No Phase 3 work should begin until Phase 2 receives the same treatment.

### Verification

- Every Phase 1 Defend This question has a concise model answer and project-specific evidence.
- Each topic is connected to the failure it prevents or the tradeoff it introduces.
- `TODO.md` now records one complete Phase 1 gate.

### Insights to retain

- Tests prove behavior; the learning gate proves the design can be explained.
- Learn annotations through the failure they prevent, not as isolated syntax.
- Record and correct sequencing gaps rather than hiding them.

### Next action

Continue from Phase 2. Its engineering work is complete; its seven-question learning/defense review remains the next gate before Phase 3.

---

## Session 7 — Phase 2 learning-gate completion

### Goal

Apply the corrected phase policy consistently by completing the Phase 2 design-defense review after its engineering evidence passed.

### Prerequisites audit

- Compared all seven Phase 2 plan questions with the implemented Redis, RabbitMQ, and PostgreSQL boundaries.
- Reused the already passing burst, redelivery, poison-message, and broker-restart evidence.
- No code, infrastructure, dependency, or local data change was required.

### Changes

- Added project-specific model answers for atomic Redis claims, manual acknowledgements, delivery semantics, poison handling, layered deduplication, capacity controls, and virtual threads.
- Marked Phase 2 fully complete instead of leaving a split engineering/learning status.

### Architectural connection

Phase 3 may rely on a durable incident existing exactly once at the database-effect boundary. The Phase 2 review explains why that guarantee comes from layered idempotency rather than from Redis or a fictional distributed exactly-once transaction.

### Verification

- Each answer names the implemented mechanism, the failure it prevents, and its tradeoff.
- `TODO.md` records both Phase 1 and Phase 2 as complete.
- Phase 3 remains unopened and requires a separate plan review before implementation.

### Insights to retain

- Publisher confirms and consumer acknowledgements protect different handoffs.
- Redis protects cost, RabbitMQ protects delivery, and PostgreSQL protects durable truth.
- Virtual threads reduce thread cost but do not replace explicit concurrency and downstream capacity bounds.

### Next action

Begin Phase 3 only after re-reading its security/tooling plan, auditing prerequisites, and breaking the work into ordered checkpoints.

---

## Session 8 — Phase 3 security and deterministic tools

### Goal

Establish identity and least-privilege authorization, authenticate machine alert intake, and build the four bounded read tools before any LLM orchestration exists.

### Prerequisites audit

- Re-read repository memory and the complete Phase 3 plan after Phases 1–2 passed both gates.
- Verified the current Boot resource-server starter, JWT authority converter, and Spring Security MockMvc support against official Spring documentation.
- Reused Java, Gradle, Docker, PostgreSQL, Redis, and RabbitMQ; installed no machine or desktop software.
- Docker Desktop had stopped and was restarted from its existing `E:` installation without reinstall/reset. Compose data volumes remained intact.

### Changes

- Added Spring Security and OAuth2 resource-server starters plus test support only.
- Added validated external JWT/webhook settings, stateless JWT security, issuer/audience/time validation, and `roles` to `ROLE_*` conversion.
- Added a 64 KiB timestamped constant-time HMAC filter for alert intake.
- Added E:-local ignored secret generation and a bounded short-lived development token helper.
- Added deployment, metric, ERROR-log cluster, and lexical runbook tools as read-only guarded Java components returning immutable DTOs.
- Added repository queries only where required; no AI dependency or mutation was introduced.
- Added ADR 0004 and the Phase 3 lesson, glossary, system-design, README, and checklist updates.

### Architectural connection

Phase 3 creates the trust boundary and deterministic evidence API that Phase 4 may orchestrate. The future model chooses among already tested methods; it cannot reshape their queries or gain approval/admin authority. Phase 5 remains the only owner of approval business logic, guardrails, ledger, and execution mutations.

### Problems found and corrected

1. Windows PowerShell's older .NET runtime lacked the static RNG method used initially; the script now uses the compatible cryptographic RNG instance API.
2. Spring Security 7 adds `FACTOR_BEARER` alongside JWT roles; the test now asserts both factor evidence and application roles.
3. Docker Engine was stopped during the first Testcontainers attempt; the existing E:-installed Docker Desktop was started, not reinstalled.

### Verification

- Real signed token tests validate signature, issuer, audience, expiration, subject, and role mapping; invalid issuer/audience/expiry fail.
- URL tests prove `401`, viewer/agent reads, agent `403` at approval/admin rules, approver/admin passage, and no session cookie.
- Webhook tests prove accepted signatures and missing/stale/wrong-body/oversized rejection.
- Unit tests prove all tool validation, bounds, DTO mapping, calculations, and clustering.
- Real PostgreSQL under `ROLE_AGENT` finds the bad deploy, calculates a 1700% increase, clusters timeout errors, and retrieves the rollback runbook.
- `clean test` passed 47 tests with zero failures/errors in 1 minute 38 seconds.
- Docker storage remained about 963 MB of images and 49 MB of volumes; `E:` had about 13.81 GB free.

### Insights to retain

- Authentication establishes identity; authorization limits that identity.
- `401` and `403` describe different failures.
- Least privilege is especially important for autonomous actors because model error is an expected input, not an impossible event.
- HMAC authenticity and idempotency solve different problems and are both required.
- A deterministic tool owns validation, transaction, query bounds, and DTO shape before a model is allowed to select it.

### Next action

Phase 3 is complete. Do not open Phase 4 until its Spring AI artifact names and model-provider strategy are re-verified, its work is decomposed, and the user is told whether any local model dependency requires attention.

---

## Session 9 — Authenticated bounded incident reads

### Goal

Give authorized viewers and the service agent a real incident-facing read path instead of defining read roles without an incident API.

### Prerequisites audit

- Reused the existing incident entity, unique sink, JWT security chain, role mapping, and PostgreSQL test infrastructure.
- Added no dependency, migration, local installation, or mutable operation.

### Changes

- Added `GET /api/v1/incidents` with optional status filtering and a default limit of 20.
- Enforced an absolute limit range of 1–100 in both HTTP validation and the application service.
- Added a read-only transactional query service with method-level role authorization.
- Added an explicit entity graph for the service relationship and mapped incidents to immutable API summaries.

### Architectural connection

The incident table is the durable output of alert ingestion. This endpoint exposes that state through a controlled read model while keeping persistence entities and lazy associations inside the transaction. Human viewers and the service agent now share the same bounded evidence boundary.

### Verification

- Unit tests prove status-filtered repository selection, DTO mapping, and rejection of an excessive limit before repository access.
- A real PostgreSQL test creates an incident and reads it through the full JWT filter chain using a genuinely signed viewer token.
- The response contains only the intended incident, service, status, severity, and timestamp fields.

### Insights to retain

- Defining a role is incomplete until it protects a useful application capability.
- API limits must also exist behind the controller so internal callers cannot bypass them.
- Entity graphs solve the required association fetch without making the relationship globally eager.

### Next action

Add the next product capability as a similarly bounded vertical slice, with a code-focused commit message and evidence before expansion.

---

## Session 10 — Bounded proposal workflow and audit transcript

### Goal

Reconstruct all durable project context, confirm the security/tooling gate, verify current Spring AI 2.0 contracts, and begin model-assisted triage without installing a model runtime or weakening deterministic safety boundaries.

### Prerequisites audit

- Re-read repository memory, all private plan files, the current journal/checklist, architecture decisions, source tree, Git history, and the relevant learning/design notes.
- Confirmed the earlier gate is complete, Git began clean, and PostgreSQL/Redis/RabbitMQ remained healthy.
- Audited Ollama command/process/port/images: no runtime or model was installed, so nothing was duplicated.
- Confirmed `E:` had about 13.81 GB free. Java/Gradle dependencies were downloaded through `E:\DevCaches\gradle`; no application or model was installed on `C:`.
- Verified Spring AI 2.0 BOM, Ollama starter, pgvector starter, structured output, tool-calling advisor, and vector-store configuration against official Spring documentation. Confirmed Azure now uses the general OpenAI integration.

### Changes

- Added Spring AI 2.0, Ollama, and pgvector libraries with chat, embeddings, vector-store creation, and automatic model pulls disabled by default.
- Added immutable router/evidence/proposal/evaluator contracts and a sequential Java workflow capped at three proposals.
- Added a deterministic runbook-membership check that overrides any favorable model evaluation of ungrounded output.
- Added V3 Flyway tables for agent runs and ordered transcript entries, including one-running-run partial uniqueness.
- Added lifecycle transactions for `OPEN -> TRIAGING -> AWAITING_APPROVAL/ESCALATED` and short independent transcript writes.
- Added ADR 0005, this journal entry, an evolving beginner lesson, glossary terms, system-design flow, and synchronized checklist.

### Architectural connection

The earlier deterministic tools now form the evidence boundary for model adapters. Spring AI will implement narrow ports; it will not own workflow policy. pgvector will improve runbook selection, while Java still proves that a proposal cites retrieved evidence. The later guardrail layer remains the only place that may calculate authoritative risk or authorize infrastructure mutation.

### Problems found and corrected

1. The remembered Java directory pointed at the SDK parent rather than its versioned JDK child; verification used `E:\DevTools\temurin-25\jdk-25.0.3+9` without reinstalling Java.
2. pgvector auto-configuration activates by default and required an embedding model bean, breaking model-free application tests. `spring.ai.vectorstore.type` now defaults to `none` and must be deliberately enabled with a selected embedding profile.
3. The private plan's Azure starter was stale. Official 2.0 documentation says Azure/Foundry access should use the OpenAI integration.

### Verification

- Three workflow tests prove safe refinement, immediate escalation on empty retrieval, invented-runbook rejection, and the maximum-three-attempt bound.
- A real pgvector/PostgreSQL 17 Testcontainer applied V3, persisted ordered transcript rows through separate transactions, completed the run, and transitioned the incident.
- `clean test` passed 54 tests with zero failures/errors in 1 minute 36 seconds.
- Existing Compose services remained healthy and no model/runtime/model-weight data was created.

### Insights to retain

- The framework should adapt to domain ports; it should not hide orchestration policy inside prompts.
- A second model is not a deterministic safety check. Grounding must be verified against retrieved identifiers in Java.
- A model's risk explanation is input to a later deterministic scorer, never the permission to act.
- Do not hold a database transaction while waiting for an LLM.
- Application dependency installation and model-weight installation are separate choices; auto-pull is disabled to preserve disk control.

### Next action

Audit RAM/GPU and current small model options, choose E:-hosted chat and embedding models, then implement Flyway-owned dimension-specific pgvector retrieval and Spring AI adapters. No user action is required for the completed checkpoint; model installation will be presented with exact E: storage impact before it occurs.

---

## Session 11 — Grounded proposal workflow completion

### Goal

Iteratively verify the existing agent skeleton, complete semantic retrieval and Spring AI integration, prove both safe outcomes end to end, finish the learning-defense gate, and only then permit work on deterministic safety/execution.

### Prerequisites audit

- Re-read the open checklist and complete private plans for agent orchestration and guardrails.
- Re-ran the existing workflow and PostgreSQL transcript tests before adding code.
- Reused Java, Gradle, Docker, PostgreSQL, Redis, and RabbitMQ. Installed no application, Ollama runtime, model, or host package.
- Hardware audit selected Qwen3 4B and `nomic-embed-text` for a later live demo. Automatic model pulls remain disabled; future Ollama binaries/models must use `E:`.

### Changes

- Added V4 with a Flyway-owned `VECTOR(768)` runbook table and HNSW cosine index.
- Added explicit, idempotent runbook indexing with embeddings outside write transactions and dimension validation.
- Added semantic retrieval capped at five hits with a `0.60` threshold; lexical mode remains the safe default.
- Added Spring AI structured-output adapters for router, generator, and evaluator at temperature `0.1`.
- Exposed only the four bounded read methods as Spring AI tools and retained trusted incident identifiers in the main Java-controlled evidence flow.
- Added atomic Redis model-call budgeting: twelve calls per incident per hour.
- Added an internal `ROLE_AGENT` security scope for message-driven tool calls and restored the previous context after use.
- Added lifecycle coordination that starts/finishes in short transactions, escalates failures, and never wraps model calls in a database transaction.
- Made orchestration an explicit opt-in property so ordinary application contexts never require models or embeddings.

### Iterative defects found and corrected

1. Raw JDBC could not infer a PostgreSQL type for `Instant`; vector upserts now pass an explicit SQL `Timestamp`.
2. Component-level bean conditions depended on scan/test ordering and polluted unrelated contexts. Optional workflow and indexing now use explicit enable/retrieval properties.
3. A hand-built `ChatClient` did not represent Boot's auto-configured advisor chain. The framework-mechanics test now drives the documented user-controlled `ToolCallingManager` loop directly and proves tool-result continuation.
4. The first full suite exposed optional indexer construction in lexical contexts; tying it to semantic mode restored ingestion/security/persistence isolation.
5. Transaction review found semantic embedding under an outer read transaction; vector generation and SQL querying are now separate, and the integration test asserts no transaction is active during embedding.

### Verification

- Real pgvector/PostgreSQL proves three runbooks index idempotently, a bad-release symptom retrieves rollback above `0.60`, and an unrelated dependency symptom returns no hit.
- Scripted Spring AI tests prove JSON-to-record conversion for all three roles and charge the model budget for every call.
- Tool tests prove exactly four read callbacks and the full request → Java invocation → tool-response → continued model response sequence.
- Redis Testcontainers proves the per-incident limit is atomic, expiring, and isolated between incidents.
- End-to-end PostgreSQL tests prove the seeded bad deploy gathers deploy/metric/log/runbook evidence, produces a proposal without execution, persists five transcript stages, and reaches `AWAITING_APPROVAL`.
- The unmatched scenario escalates before proposal generation.
- Two simultaneous starts produce exactly one running agent row.
- After iterative fixes, `clean test` passed 62 tests with zero failures/errors. The final run is recorded in the commit checkpoint.

### Architectural connection

The agent now ends at an auditable proposal. The next safety layer can consume the proposal, retrieved similarity, incident service/tier, and transcript, but the model still has no risk-scoring, approval, ledger, or execution authority. Those responsibilities must enter through one deterministic gate.

### Insights to retain

- Vector dimension is schema: changing embedding models requires migration and re-indexing.
- A similarity threshold narrows candidates; Java membership verification establishes proposal grounding.
- Optional AI components need explicit activation, not accidental bean-presence inference.
- Test model integration at two layers: scripted structured responses for domain adapters and real framework tool execution for callback mechanics.
- Concurrency safety needs both transaction locking and a database uniqueness backstop.

### Next action

The Phase 4 engineering and defense gates are complete. Begin Phase 5 by decomposing the single gate, deterministic risk, dry-run/kill switch, append-only ledger, approval, and idempotent execution boundaries. No user action or installation is required.

---

## Session 12 — Deterministic safety foundation

### Goal

Checkpoint the fully verified grounded-proposal workflow, reconstruct durable state after context compaction, decompose the safety work, and implement its first model-free policy slice.

### Continuity and prerequisites

- Reconstructed state from `AGENTS.md`, `TODO.md`, the private safety plan, the prior journal entry, Git status/history, and test XML rather than relying on compacted chat.
- Added that recovery sequence to repository memory as a mandatory future-session rule.
- Confirmed 62 tests passed with zero failures/errors before checkpointing the proposal workflow.
- Confirmed private plan and Claude-local files remained ignored, attribution was absent, and E: had approximately 13.7 GB free.
- Installed nothing and left all existing database/container resources unchanged.

### Changes

- Committed and pushed the grounded proposal milestone as `7beceae` (`feat: complete grounded incident proposals`).
- Replaced the broad future safety item with decision-boundary, durable-execution, human-control, and evidence checkpoints.
- Added validated `RiskFacts`, an invariant-checking `RiskBreakdown`, and `DeterministicRiskScorer`.
- Encoded explicit action, tier, dependent, confidence, and peak-window contributions. Blast-radius contribution is capped at 10.
- Recorded the safety decision in ADR 0006 and began the beginner guardrail chapter, glossary, and workbook exercise.

### Iterative verification

- Focused risk tests passed six cases: low-risk restart, high-risk rollback, the exact `0.75` confidence boundary, capped blast radius, invalid safety facts, and inconsistent-total rejection.
- The first clean-suite command exceeded its two-minute shell limit; this was recorded as an incomplete run rather than a failure.
- A forced uncached rerun completed in 1 minute 57 seconds: 68 tests, zero failures, zero errors, and zero skips.

### Architectural connection

The proposal layer supplies an action and grounded evidence; persisted fleet/incident facts will supply the rest of `RiskFacts`. The scorer only explains risk. It cannot execute, approve, read a kill switch, or bypass an allowlist. Those policies will converge in one future `GuardrailGate` before any strategy exists.

### Insight to retain

An explainable number is still not permission. The safety boundary is the combination of deterministic facts, operator controls, durable idempotency, and a single gate—not the score alone.

### Next action

Checkpoint this model-free scorer, then implement a decision-only gate with ordering tests before adding any execution mutation. No user action or installation is required.

---

## Session 13 — Single-gate execution and human control

### Goal

Complete the safety-critical implementation without beginning deployment work: one deterministic gate, durable idempotency, dry-run, kill switch, immutable audit history, transactionally separated execution, compensation, human approval, crash recovery, full tests, and the learning defense.

### State reconstruction and environment

- Reconstructed the open work from `AGENTS.md`, `TODO.md`, the private safety plan, journal, Git history, and clean working tree.
- Reused Java, Gradle, Docker, PostgreSQL/pgvector, Redis, and RabbitMQ. Installed or downloaded nothing.
- Kept test PostgreSQL/Redis isolated in Testcontainers and did not touch other projects or the unmanaged Windows PostgreSQL instance.
- Kept local remediation dry-run as the default configuration.

### Decision boundary

- Added explicit gate decisions for escalation, refusal, skip, dry-run, automatic execution, approval, and approved execution.
- Enforced order: fail-closed kill switch, persisted service allowlist, deterministic score, durable action history, dry-run, then threshold/approval.
- Added a PostgreSQL-authoritative global kill switch. Redis stores only the engaged shortcut; absence never means safe without checking PostgreSQL.
- Added an admin-only kill-switch endpoint and commit-after Redis synchronization.
- Closed an API loophole discovered during review: the gate now issues a construction-restricted `ExecutionAuthorization`, and the executor plus reservation/result writers reject missing or mismatched authorization. Gate authorization issuance is package-scoped, and strategy types are package-private.

### Durable request, claim, ledger, and execution

- Extended grounded triage outcomes with the retrieved runbook ID and similarity.
- Persisted a remediation request in the same short transaction that completes triage, then evaluated/executed only after commit.
- Revalidated similarity `>= 0.60` and exact runbook ID/title/action at the persistence boundary so approval cannot revive an ungrounded proposal.
- Added V5 tables for safety control, remediation request, action claim, immutable ledger, simulated service state, and per-claim effect.
- Separated mutable correctness state (`action_claim`) from immutable audit events (`action_ledger`). A PostgreSQL trigger rejects ledger updates/deletes.
- Committed `IN_PROGRESS` plus its event in `REQUIRES_NEW`, ran the internal strategy without an outer coordinator transaction, and committed result in another `REQUIRES_NEW` transaction.
- Added four idempotent simulated strategies keyed by claim. Concurrent duplicate execution produces one claim and one effect.
- On induced later-step failure, recorded failure and compensation events and restored the completed simulated change.

### Human control and recovery

- Added a typed review view containing service, action, grounded runbook, steps, rationale, advisory model risk notes, similarity, authoritative score/breakdown, status, and expiry.
- Added `SRE_APPROVER`-only approve/reject handling; the `AGENT` role cannot self-approve.
- Recorded approval intent, then re-entered every gate policy before execution.
- Added approval expiry scanning; timeout records rejection and escalates without execution.
- Added pending-decision recovery for the crash window after durable triage completion.
- Added fail-closed stale-execution recovery: uncertain work escalates and its permanent claim continues blocking automatic repetition.

### Iterative defects and loopholes corrected

1. A concurrent-test lambda inferred `Object`; explicit `Callable<ExecutionOutcome>` made the harness type-safe.
2. The new approval route changed an old placeholder security expectation from missing route to malformed body; the test now sends valid JSON and API exceptions map to `404`/`409`.
3. A timeout fixture violated V5 time-order constraints; the fixture now represents a genuinely older request, demonstrating the constraint works.
4. Database/JVM clock skew could reject a valid kill-switch update; safety-control audit time now remains monotonic by keeping the later instant.
5. Making the internal strategy final prevented Spring transaction proxying; package visibility, rather than `final`, now closes its API while preserving interception.
6. Direct executor construction could previously forge an `AUTO_EXECUTE` decision; the opaque matching gate authorization closes that path.
7. Recovery fixtures initially violated incident/request time checks; corrected fixtures preserve the same invariants as production.
8. `@Repository` translated deliberate grounding-policy errors into data-access errors; a component stereotype preserves domain validation while `JdbcTemplate` still translates SQL failures.

### Verification evidence

- Gate unit tests cover every short circuit, inclusive threshold, human re-entry order, and fail-closed kill-switch error.
- Real PostgreSQL tests prove one result under concurrent execution, committed reservation visibility, per-claim strategy idempotency, compensation, and trigger-enforced ledger immutability.
- Real PostgreSQL plus isolated Redis tests prove automatic low-risk execution, high-risk approval, agent self-approval denial, allowlist refusal, engaged kill switch, expired approval, pending recovery, stale-execution escalation, and approver review.
- The existing grounded-agent integration now proves default dry-run records a decision but creates no action claim or effect.
- Persistence-boundary regression proves similarity below `0.60` and mismatched runbook/action cannot create a remediation request.
- An uncached full run passed 88 tests in 2 minutes 20 seconds before the final grounding regression.
- The final uncached checkpoint passed 89 tests with zero failures, errors, or skips in 2 minutes 27 seconds.

### System-design lesson

The database cannot atomically commit an external side effect. Correct design does not hide that gap inside a long transaction; it records intent before, makes the effect idempotent, records result after, and escalates uncertain outcomes without automatic replay. Sentinel uses effectively-once mutation semantics rather than making a false exactly-once claim.

### Next action

Review diffs/authorship/ignored files and checkpoint the completed safety gate. Then begin the observability/testing checklist only; deployment remains later and requires an explicit user handoff before cloud actions or model/provider provisioning.

---

## Session 14 — Protected metrics foundation

### Goal

After the safety gate passed and was pushed, begin operability in dependency order without starting packaging or deployment: verify current official observability guidance, decompose the remaining work, expose protected Prometheus metrics, and add bounded domain measurements.

### Prerequisite and environment audit

- Re-read the complete private operability/deployment plan after commit `bc3d93d`.
- Verified Spring Boot 4.1 Actuator/Prometheus and Micrometer Observation guidance from current official Spring and Micrometer documentation.
- Added only Gradle-managed application dependencies. No Prometheus, Grafana, collector, CLI, model, or host package was installed.
- Gradle downloads remained under `E:\DevCaches\gradle`; Docker and unrelated projects were untouched.

### Changes

- Replaced the single future item with observability, layered verification, packaging, explicit deployment handoff, and portfolio checkpoints.
- Added Actuator and the Prometheus registry under Spring Boot dependency management.
- Exposed only health, info, metrics, and Prometheus, all still protected by the existing stateless JWT rules.
- Added `SentinelMetrics` with bounded tag normalization.
- Instrumented successful durable incident creation, total triage duration, evaluator attempts, known evidence tools, bounded model roles, and remediation decision outcomes.
- Kept provider tokens/cost open until a live provider supplies authoritative usage metadata; tests do not fabricate cost from prompt text.
- Recorded the observability decision in ADR 0008 and began the operability lesson/glossary.

### Verification

- `SentinelMetricsTest` proves counter, timer, distribution-summary, and unknown-tag behavior with an in-memory registry.
- A real application/PostgreSQL test proves unauthenticated Prometheus access is rejected and an authenticated scrape contains JVM metrics plus `application="sentinel"`.
- Focused agent/Redis tests prove the new tool/model instrumentation composes with grounded triage and the existing call budget.
- The final uncached suite passed 91 tests with zero failures, errors, or skips in 2 minutes 29 seconds.

### System-design lesson

Metrics are aggregated operational signals, not an audit ledger. Keep their tags low-cardinality and non-sensitive; use the transcript/ledger for exact incident facts. Observability must describe the safety system without becoming another authority path.

### Next action

Checkpoint the metrics foundation. Then add Observation spans and in-memory propagation tests; do not start deployment or cloud provisioning.

---

## Session 15 — Trace continuity and deployment feasibility

### Goal

Extend the operational evidence into traceable agent stages, research the fastest current Azure deployment route, and keep model accuracy separate from scripted workflow correctness.

### Changes and decisions

- Added fixed Micrometer observations for the triage root and classify, gather, propose, and evaluate children.
- Proved nested parent continuity with an in-memory observation handler; this test needs no collector or network.
- Added Spring Boot's current OpenTelemetry starter. Trace export is disabled in the normal profile and enabled only with the explicit `otlp` profile.
- Added a small Grafana dashboard and Prometheus rules using the actual exported metric names. A remediation failure pages; elevated escalations and unexpected intake silence create tickets.
- Corrected initial dashboard queries after comparing them with `SentinelMetrics`: the timer is `sentinel_triage_duration_seconds` and remediation uses `status`, not an invented `result` tag.
- Confirmed from current Microsoft documentation that Azure Container Apps can deploy an existing image in minutes, PostgreSQL Flexible Server supports PostgreSQL 17 plus `vector`, and new Redis work should target Azure Managed Redis rather than the retiring Azure Cache for Redis service.
- No Azure resource, public endpoint, collector, model, or host package was created or installed.

### Verification

- Focused workflow and observation tests pass after adding the OpenTelemetry starter.
- The default application configuration explicitly disables tracing export, so ordinary tests and startup cannot silently contact an OTLP endpoint.
- The first full run revealed that the OpenTelemetry starter separately enabled OTLP metric export. Assertions passed, but shutdown attempted `localhost:4318`; the default now explicitly disables that registry as well, and Prometheus remains the metric export path.

### Learning insight

A trace proves causal continuity and latency across stages; it does not prove that a model answer is correct. Model quality requires a separately versioned ground-truth corpus and scoring rules. Scripted model tests protect orchestration contracts but must never be reported as model accuracy.

### Next action

Create train/validation/holdout incident scenarios and a deterministic scoring harness, establish a non-live baseline, then run the same corpus against a live provider only when model access is deliberately enabled.

### Ground-truth checkpoint

- Added 12 balanced, immutable scenario IDs across development, validation, and holdout.
- Ground truth covers bad deploy, resource exhaustion, dependency outage, and unknown, with required signals and exact grounded action or escalation.
- Added separate classification, signal-coverage, retrieval-recall, outcome, and hallucination measurements. Scorer regression deliberately injects one bad classification and an invented runbook to prove errors remain visible.
- Corpus structure tests reject duplicate IDs, missing splits, duplicate signals, and incoherent action/escalation labels.
- Recorded the current live baseline as `NOT_RUN`. The PC audit found no Ollama installation or model image, 16 GB RAM, integrated AMD graphics, and 13.1 GB free on `E:`. Installing the current official Windows binary plus Qwen3 4B and embeddings would consume a material share of that space, so it is not being hidden inside this scorer checkpoint.
- Focused corpus/scorer tests pass. No model, package, cloud resource, or unrelated database was changed.

---

## Session 16 — Reproducible container and deadline deployment analysis

### Goal

Turn the verified application into a cloud-portable artifact, prove its runtime constraints locally, and answer the tonight/tomorrow deployment question without provisioning or hiding missing prerequisites.

### Implementation and iterative checks

- Added a Spring Boot layered Dockerfile on patch-pinned official Temurin Java 25.0.3+9.
- Kept build tooling outside the runtime image and ran the process as numeric `10001:10001`.
- Added status-only liveness/readiness endpoints for platform probes; Prometheus, metrics, health details, and business APIs remain protected.
- A focused real-PostgreSQL web test proves both probes return `UP` anonymously while `/actuator/metrics` remains `401`.
- Built the executable JAR twice from clean/no-cache inputs; both SHA-256 values matched.
- Built `sentinel:local` using Docker's E-backed storage. It is about 200 MB.
- Started it against the existing isolated Compose network with a read-only root filesystem and bounded `/tmp`; readiness/liveness returned `UP`, Prometheus returned `401`, and Docker confirmed the numeric user.
- Stopped and automatically removed the scratch container. The reusable base/application image remains for deployment work; `E:` has 10.75 GB free.

### Deployment conclusion

A one-VM Azure demo can realistically be online by tomorrow after subscription, region, budget, access mode, and credentials are supplied. A platform-only deployment can be sooner. A full live-agent demonstration still requires the selected chat/embedding models and evaluation; managed Container Apps plus PostgreSQL/Redis/RabbitMQ networking is a production-shaped follow-up, not the safest one-night shortcut. No Azure CLI, cloud resource, public endpoint, registry, paid model, or model weight was installed or created.

### Learning insight

An image is the immutable program artifact; environment variables and secret stores supply deployment-specific configuration. Liveness answers “should the platform restart me?” Readiness answers “should traffic reach me?” Neither should reveal database details or become an authorization bypass.

### Regression evidence

The final uncached packaging checkpoint passed 95 tests with zero failures, errors, or skips in 2 minutes 37 seconds. The run included all PostgreSQL/pgvector, Redis, RabbitMQ, agent, guardrail, evaluation-contract, security, and probe tests and made no OTLP connection attempt.

---

## Session 17 — Closing the real alert-to-agent handoff

### Gap found during start-to-finish review

The RabbitMQ consumer committed an incident and acknowledged the message but never invoked the enabled agent. Direct agent integration tests passed, yet real webhook traffic stopped at persistence. This was a cross-slice integration gap rather than a failing local component.

### Fix and safety reasoning

- Added a dispatcher that is a no-op when the agent is disabled.
- With the agent enabled, it resolves the already committed incident and invokes triage with the durable ID and original alert facts.
- The broker acknowledges only after a durable agent/gate outcome. Terminal redelivery skips a second run; concurrent `TRIAGING` delivery enters bounded retry rather than being silently acknowledged.
- Added stale-run recovery: after ten minutes an abandoned `RUNNING` run becomes `FAILED` and its incident becomes `ESCALATED`. Uncertain work is never replayed automatically.
- Kept the database insert transaction short and outside model/tool execution.

### Evidence

- Dispatcher and consumer unit tests prove disabled, open, terminal-redelivery, active-triage retry, permanent poison, transient retry, retry-publish failure, and acknowledgement order.
- Recovery unit evidence proves stale work fails closed.
- A real PostgreSQL/pgvector + Redis + RabbitMQ test now posts a correctly HMAC-signed bad-release alert and waits for one `PROPOSED` run, one `DRY_RUN` remediation request/ledger event, and zero action claims.
- The same integration class still proves 50-to-1 storm suppression, durable broker restart delivery, poison DLQ behavior, and PostgreSQL redelivery idempotency.
- Focused complete-pipeline tests pass. No model/provider or cloud resource was used; structured roles and embeddings are deterministic test doubles.
- The final uncached connected-pipeline checkpoint passed 101 tests with zero failures, errors, or skips in 2 minutes 28 seconds.

### Next action

Run the complete regression suite for this newly connected pipeline, then checkpoint it. Live model evaluation remains a separate accuracy measurement.

---

## Session 18 — Live ground truth, deterministic evidence, and final handoff

### Goal

Reverify the application from intake through guarded outcome, establish a real local-model baseline, iterate against versioned ground truth without tuning on holdout, and determine whether a deadline Azure demo is realistic.

### Environment and installation discipline

- Audited first: Ollama was absent; Docker and Java were reused.
- Downloaded official Ollama 0.32.1 to `E:`, verified its published SHA-256 and valid Ollama Inc. Authenticode signature, then installed under `E:\DevTools\Ollama`.
- Set model storage to `E:\DevModels\ollama`; installed only `qwen3:4b` and `nomic-embed-text` (2.58 GB measured).
- Deleted installer/checksum scratch after verification. No unrelated project, Windows PostgreSQL, cache, or user data was modified.
- The installer command exceeded its wait window, but registration, version, files, and API health proved success; it was not installed twice.

### Iterative findings and fixes

1. The first real-model run returned prose while the adapter expected JSON. Native Ollama JSON format fixed the transport contract.
2. The repaired development baseline classified 3/4 but selected none of the complete evidence sets. Explicit class boundaries fixed the ambiguous false deploy.
3. Qwen3 4B still omitted tool signals. Evidence selection moved to deterministic Java, leaving the model responsible only for type and rationale.
4. Validation found that an empty model signal list could fail domain construction before normalization. The router response was reduced to `{type, rationale}`.
5. Recall hid irrelevant results on a negative scenario. The scorer now reports ground-truth retrieval matching and similarity values.
6. The frozen holdout scored 4/4 classification, signal coverage, recall@3, and negative matching. No tuning followed.
7. A two-case full loop correctly proposed the grounded rollback and escalated the ambiguous case with zero grounding violations.

### Performance insight

On local CPU, holdout classification took 14.5–16.0 seconds and semantic retrieval 155–189 ms. The grounded full loop took 100.5 seconds: generation and evaluation each took about 41 seconds. The no-runbook fail-safe path took 14.8 seconds. The bottleneck is model inference, not pgvector.

### Start-to-finish regression finding

The first final clean suite exposed nine context failures: `@ConditionalOnBean` on semantic search was evaluated before imported deterministic gateway beans. The semantic profile now creates the engine directly and constructor injection fails fast if no gateway exists. The three affected integration suites passed after repair, followed by an uncached clean run of 102 tests across 34 suites with zero failures, errors, or skips.

Two clean application builds produced identical SHA-256 `5A1B87B51234FF465F21E3CA816A358DEF6A120CACB10F4612F6B03870F7DD61`. The final image was rebuilt and ran as `10001:10001` with a read-only root filesystem; readiness and liveness returned `200`, anonymous Prometheus returned `401`, and the temporary smoke container was removed.

### Deployment conclusion

A one-VM Azure demo is realistic by tomorrow once the user supplies an active subscription, region, maximum budget, and private-tunnel versus public-TLS choice. The current image and Compose topology are locally smoke-tested. Full-agent latency will be slow on CPU; a paid/accelerated provider is a separate implementation and approval. No Azure CLI, cloud resource, registry, DNS record, or public endpoint was created.

---

## Session 19 — Rehearsable deployment and stable delivery identity

### Goal

Prepare the complete one-VM deployment bundle, prove it before Azure, and keep one résumé URL stable while later commits deploy automatically.

### What changed and how it connects

- Added an opt-in application startup runner that idempotently embeds all Flyway-seeded runbooks before readiness. Default/test startup remains unchanged.
- Added an isolated deployment Compose topology with digest-pinned PostgreSQL/pgvector, Redis, RabbitMQ, and Azure-only Ollama/Caddy overlays. Only Caddy is public in Azure; the app maps to host loopback and every dependency stays on the private Docker network.
- Added strong ignored secret generation for PowerShell and Linux, official Docker Engine bootstrap for Ubuntu 24.04, a confirmation-gated Azure provisioning script, and local start/stop/export tooling.
- Added a public static portfolio landing page at `/`; it calls only the status-only readiness endpoint. Protected metrics and APIs retain their existing security boundary.
- Added GitHub Actions verification, SHA-tagged GHCR publication, and opt-in SSH deployment to the existing VM. A static Azure IP/DNS or custom domain remains unchanged across releases.

### Iterative verification and defect found

The first rehearsal correctly failed: setting Spring AI's generic pgvector auto-configuration activated its unrelated `id/metadata` schema contract. Sentinel already owns a smaller Flyway schema and bounded JDBC retrieval implementation. Removing that unnecessary flag preserved semantic retrieval and avoided two owners for one table.

The corrected exact stack passed readiness `200`, liveness `200`, anonymous Prometheus `401`, and a database assertion of three indexed 768-dimension runbooks. The public Caddy edge returned `200` for both the landing route and proxied readiness. It reused the existing E-drive Ollama/model installation and did not touch the ordinary Compose project or Windows PostgreSQL.

The final uncached regression passed 103 tests across 35 suites with zero failures, errors, or skips. Both Compose combinations validated, all four Linux scripts passed `bash -n` under Ubuntu 24.04 WSL, and `git diff --check` was clean. The temporary Caddy image and all isolated rehearsal containers, network, and volumes were removed after evidence collection.

The first GitHub runner stopped immediately with exit code 126 because the repository's Windows-origin Gradle wrapper lacks the Unix executable bit. CI now invokes the checked-in wrapper explicitly through `bash`, preserving the wrapper checksum/version while avoiding an unnecessary permission mutation. Azure deployment was disabled, so the failed verification created no cloud resource or public endpoint.

The corrected GitHub run `29679568345` completed successfully: Linux regression, executable JAR, OCI build, and commit-SHA/main GHCR publication all passed. The deploy job remained skipped because `AZURE_DEPLOY_ENABLED` is not set to `true`, proving the cloud mutation boundary works as intended.

---

## Session 20 — Domain-free stable HTTPS decision

The user has no custom domain. The Azure DNS label still produces a qualifying public hostname. Caddy can validate control through the hostname's public A record and externally reachable ports 80/443, obtain a publicly trusted certificate, redirect HTTP to HTTPS, and renew the certificate using its persistent data volume. Deployment configuration now uses the bare Azure FQDN; adding `http://` would deliberately disable automatic HTTPS. A purchased domain is therefore optional branding rather than a deployment prerequisite.

The user confirmed `sentinel-mofazzal874` is available in Central India, made the GHCR package public, privately recorded the SSH source IPv4 address, and explicitly approved the reviewed Azure resource scope and spend. That authorization covers only `sentinel-demo-rg`, one non-zonal `Standard_B4as_v2` Ubuntu VM, 64 GB Standard SSD, static IP/DNS, VNet/subnet/NIC/NSG, public TCP 80/443, and SSH from the user's `/32`. It does not authorize AKS, ACR, Azure OpenAI, managed data services, a custom domain, or changes to unrelated resources. Resource creation still requires the user to execute the confirmation-gated script inside their authenticated Azure Cloud Shell.

Azure created the approved resources successfully at `sentinel-mofazzal874.centralindia.cloudapp.azure.com`, and cloud-init completed. The first application attempt correctly created the permission-restricted VM environment file and anonymously pulled the verified GHCR image, then stopped before Compose startup: Azure Run Command uses a root-only waagent working directory, which the unprivileged `azureuser` could not inspect. The launcher now changes to the repository directory explicitly before invoking Compose. The retry reuses the same secrets and image rather than reinstalling or regenerating them.

### Durable lesson

The service identity and release artifact must be separate. DNS/static IP is the durable address; an image tagged by commit SHA is replaceable software. A budget is an alert rather than an automatic shutdown. Continuous résumé availability therefore costs money even when no release occurs, while deallocation preserves the address but takes the demo offline.

### Next safe action

Request explicit approval before the confirmation-gated Azure script creates the dedicated resource group and public endpoint. The remaining user choices are the DNS label and whether to use the Azure HTTP hostname initially or point a custom domain for HTTPS.

---

## Session 21 — Safe operational digital-twin foundation

### Goal and product value

Replace the blank/static public demonstration with traceable operational stories. Sentinel's real use is to reduce on-call investigation time while preventing an AI-generated suggestion from becoming an unsafe infrastructure mutation. A portfolio deployment is a reference implementation of that control plane; production adoption would add adapters to a company's actual monitoring, deployment, identity, runbook, and execution systems.

### Plan and implementation

- Defined three reviewer journeys: a grounded faulty-release rollback stopped by dry-run, an ambiguous dependency outage escalated for missing grounding, and a bounded scale-out held for human approval.
- Added the forward-only `demo_run` registry. It stores a public identifier and reference instead of copying mutable incident state.
- Added a `demo`-profile startup seeder that writes coherent synthetic history to the authoritative deployment, incident, agent-run, transcript, remediation-request, and action-ledger tables.
- Enabled `seed,demo` only in the Azure demo Compose bundle. Normal profiles and unrelated databases are unchanged.
- Added bounded public GET projections for registered demo runs. Ordinary incident, approval, metrics, administrative, and fleet APIs remain protected by JWT.
- Added an explicit synthetic-data disclaimer and recorded the public demo constraints in repository memory and ADR 0013.

### Verification and iteration

The first verification attempt found Docker Desktop stopped. The compatible E-drive installation was located and started; nothing was downloaded or reinstalled. The initial database assertions passed, while the append-only assertion was too strict because PostgreSQL appended trigger context to the expected message. The assertion was corrected to match the stable error content.

The focused real PostgreSQL/pgvector integration then passed. It proves three public histories, fourteen ordered transcript entries, the dry-run and approval-requested ledger events, zero action claims, repeatable seeding, anonymous curated reads, `404` for an unknown run, `401` for the ordinary incident API, and rejection of ledger updates. The complete regression then passed 106 tests across 36 suites with zero failures, errors, or skips.

### Learning insight

Useful seed data is a causal graph, not a pile of rows. A reviewer should be able to follow alert evidence into a proposal, deterministic decision, and audit fact. The small `demo_run` registry is an access boundary: it identifies what may be shown publicly while the source of truth remains the normal domain tables.

### Next safe action

Implement server-owned live scenarios with strict rate/concurrency controls, then build the operator console on the recorded and live read models. The environment remains dry-run throughout.

---

## Session 22 — Operator console foundation

### Goal

Turn the public experience from an architectural landing page into an actual incident operations product while preserving the existing Azure hostname, backend authority, dry-run safety, and E-drive storage policy.

### Planning and data decision

The user requested phase-by-phase delivery, substantial realistic data, and generated-ID CRUD. `docs/OPERATOR_CONSOLE_IMPLEMENTATION_PLAN.md` now defines seven gated phases, users, routes, a CRUD matrix, and a target digital twin of 12 services, 50+ deployments, 30+ incidents, 10+ runbooks, 10,000+ metrics, and 750+ logs.

Alibaba production microservice traces, Tsinghua NetMan AIOps datasets, and Microsoft AIOpsLab informed the topology and failure-distribution decision. Sentinel will generate an original deterministic dataset rather than copying large research downloads with mismatched schemas or ambiguous distribution constraints. Catalog/configuration resources receive generated persistent UUIDs and CRUD; incidents and audit facts remain non-deletable.

### Implementation

- Added pinned Next.js 16.2.10 and React 19.2.7 under `frontend/`, using static export so Azure needs no Node runtime process.
- Built a responsive console with a service state header, incident metrics, selectable incident queue, investigation timeline, grounding confidence, risk visualization, gate outcome, append-only ledger, loading/error/empty states, and explicit synthetic-data labeling.
- The frontend contains only typed API contracts. All incident content comes from the bounded Spring demo projections.
- Embedded the generated export into the Spring Boot artifact and opened only `/`, static Next assets, and the already reviewed demo GET projection.
- Simplified Caddy to one same-origin TLS proxy and removed the obsolete standalone landing-page file and mount.
- Updated CI and the local rehearsal to build the frontend before the JAR and verify the real console/API boundary.
- Added a project-specific social preview that represents evidence flowing through a deterministic gate into an audit ledger.

### Iterative findings

The first build exposed that the newest TypeScript 7 release was ahead of the current Next build worker. TypeScript was pinned to compatible 5.9.3. The dependency audit then exposed an older transitive PostCSS; a safe 8.5.10 override removed the advisory. The first interaction test used a case-sensitive text assertion against deliberately uppercase visual copy; the assertion was corrected without changing product behavior.

### Verification

- Frontend: 2 interaction tests pass, strict type checking passes, static export succeeds, and `npm audit` reports zero vulnerabilities.
- Packaged artifact: the executable JAR contains `static/index.html`, hashed Next assets, and the social preview.
- Exact isolated stack: console `200`, three real demo runs, protected incidents `401`, readiness/liveness `200`, Prometheus `401`, and three semantic runbook embeddings.
- Complete backend regression: 106 tests across 36 suites, zero failures, errors, or skips.
- Azure Compose merge validates and `git diff --check` passes.
- The isolated rehearsal containers, network, and volumes were deleted after verification. No global package or second Node installation was created; npm cache remained on `E:`.

### Learning insight

A frontend does not make the browser authoritative. The console explains and requests; Spring Boot still owns identity, validation, transactions, state transitions, and safety. Static export is a deployment optimization, not a reduction in interactivity.

### Next action

Expand the persistent digital twin to the documented scale before adding catalog/runbook CRUD and live incident creation.

---

## Session 23 — Versioned operations digital twin

### Goal

Make a fresh demo database substantial and immediately explorable without turning the browser into a mock-data store or importing unrelated customer/research records.

### Implementation

- Added a forward-only dependency graph and database-owned public run title/summary fields.
- Added a dataset-version marker and an idempotent `demo`-profile generator that writes the authoritative fleet, telemetry, incident, transcript, remediation, claim, and append-only ledger tables in one transaction.
- Expanded the baseline to 4 teams, 12 services, 18 dependencies, at least 60 deployments, 10 runbooks, 30 incidents, 10,800 metrics, and 1,080 structured logs.
- Added histories for missing-grounding escalation, dry-run, human approval, automatic simulated resolution, and compensation.
- Removed the last frontend-facing hard-coded scenario summary map; titles and summaries now come from PostgreSQL through the bounded demo DTO.

### Correctness evidence

The focused real PostgreSQL/pgvector integration test passes. It proves exact baseline cardinalities, repeatable seeding, 30 bounded public records, no correlated deployment after an incident, all five metric series near every generated incident, at least one traceable log near every generated incident, protected ordinary APIs, and database rejection of ledger mutation.

The complete backend regression passes 106 tests across 36 suites with zero failures, errors, or skips. The frontend's two interaction tests, strict type check, production static export, and dependency audit also pass; the audit reports zero vulnerabilities. `git diff --check` is clean.

The data is synthetic and deterministic, not copied production traffic. Its topology and failure mix are informed by public AIOps research, while its schema and causal histories are original and tailored to Sentinel.

### Learning insight

A useful incident dataset is a causal graph, not a collection of random rows. Service ownership, dependency direction, release time, telemetry window, proposal grounding, deterministic gate result, and audit outcome must agree. Reproducibility makes that graph testable and explainable.

### Next action

Add authenticated generated-ID catalog and runbook CRUD with validation, optimistic locking, archive rules, pagination, authorization tests, and an administration UI. Do not expose safety-policy mutation to anonymous portfolio visitors.

---

## Session 24 — Durable catalog administration

### Goal

Make the fleet, topology, runbooks, allowlists, and future sandbox scenarios editable as real persisted resources while protecting history and concurrent administrator work.

### Implementation

- Added generated-UUID create/read/update/archive APIs for teams, services, runbooks, and fixed scenarios, plus create/read/hard-delete for dependency edges.
- Added forward-only lifecycle/version columns and a fixed-scenario table whose type is a closed server-owned enum.
- Added service-layer transactions, bounded pagination, input validation, explicit stale-version checks, database optimistic locking, and stable `400`/`404`/`409` errors.
- Preserved historical references through archive semantics. Active fleet and runbook retrieval now exclude archived records; runbook update/archive removes stale embeddings.
- Enforced read access for operational roles and mutation access only for `ADMIN` at the method boundary.
- Added a responsive Catalog workspace with a memory-only short-lived token field, generated-ID forms, edit/archive/delete actions, version display, and conflict feedback.

### Verification and iteration

The real PostgreSQL/MockMvc lifecycle test passes. It proves generated IDs, anonymous `401`, viewer mutation `403`, administrator success, stale update `409`, archive preservation, dependency hard-delete, duplicate-edge conflict, and rejection of an arbitrary `SHELL_COMMAND` scenario type.

The final complete regression passes 109 tests across 37 suites with zero failures, errors, or skips. Frontend verification passes 4 tests across 2 files, strict type checking, production static export, and a zero-vulnerability dependency audit.

The in-app browser service was unavailable during this session. The temporary local Next process was stopped and its process tree removed. Component interaction tests cover navigation, the locked state, token connection, and form submission; visual browser rehearsal remains in the final deployment gate.

### Learning insight

CRUD is not one universal deletion rule. A dependency edge describes current topology and can disappear. A service or runbook explains past incidents and must remain as an archived fact. Optimistic locking turns a dangerous silent overwrite into a visible human decision.

### Next action

Build the public fixed-scenario execution pipeline through existing alert ingestion, durable messaging, agent workflow, guardrail gate, and demo-run registry. Add per-client/global/daily bounds before exposing its submit endpoint.

---

## Session 25 — Bounded public live-scenario pipeline

### Goal

Let a recruiter create a fresh, realistic incident from the operator console while keeping anonymous input, resource use, and remediation authority tightly bounded.

### Implementation

- Added four server-owned scenario templates and a durable submission table with generated public UUIDs, state, completion linkage, and hashed retry identity.
- Added one atomic Redis admission decision for per-client minute, global daily, and concurrent lease limits. New work fails closed if Redis cannot enforce the boundary.
- Generated causal metrics, structured logs, and deployment evidence for each accepted run, then reused the existing fingerprinting, Redis alert suppression, RabbitMQ, incident, agent, guardrail, and ledger path.
- Extended the RabbitMQ consumer with ordered lifecycle listeners. A live submission completes or fails and releases capacity before the message is acknowledged or terminally dead-lettered.
- Added a public launcher that lists only fixed templates, generates an idempotency key, polls bounded status, and opens the resulting authoritative incident view.
- Kept the public safety mode at dry-run and exposed no prompt, alert body, action, tool argument, or policy mutation.

### Iterative findings

The first migration attempted to insert scenario templates before the demo operations seeder had created their service rows. Flyway must own schema while the profile seeder owns demo data, so the migration became schema-only and seeder ordering was made explicit.

The stronger integration test initially expected six transcript entries by copying a pre-seeded narrative's shape. A real successful one-pass workflow has five authoritative stages: classification, evidence, proposal, critique, and outcome. The oracle was corrected and strengthened to prove dry-run creates no executable action claim.

### Verification

The focused gate passes against real PostgreSQL 17/pgvector, Redis 7, and RabbitMQ 4. It proves fixed public discovery, durable retry idempotency, queued processing, generated evidence, agent escalation, five transcript facts, deterministic dry-run, an append-only ledger event, zero action claims, a global daily rejection, protected ordinary APIs, and acknowledgement ordering on success and every terminal failure path.

Frontend verification passes five interaction tests across two files, strict type checking, production static export, and a zero-vulnerability dependency audit.

The final clean backend regression passes 113 tests across 38 suites with zero failures, errors, or skips. `git diff --check` is clean, and the private project plan plus Claude memory files remain ignored.

### Learning insight

An interactive demo should exercise the real system without granting the public its authority. Sentinel separates selection, identity, capacity, delivery, reasoning, and authorization so each failure has a specific boundary. The browser asks for a reviewed story; it never defines or approves remediation.

### Next action

Run the complete regression suite, then build the approval/safety/topology views. Deployment remains deferred until those operator journeys and final browser rehearsal pass.

---

## Session 26 — Azure deployment and CI/CD learning reconstruction

### Goal

Turn the scattered deployment scripts, architecture notes, command history, and troubleshooting discoveries into one beginner-safe start-to-finish guide. The user must be able to reproduce and explain the deployment rather than depend on chat history.

The durable tutorial is [`docs/deployment/AZURE_BEGINNER_DEPLOYMENT_GUIDE.md`](deployment/AZURE_BEGINNER_DEPLOYMENT_GUIDE.md). Future deployment sessions must update that guide when commands, identity, topology, or operational evidence changes.

### Documentation audit

The repository already described the single-VM topology, local rehearsal, provisioning script, stable hostname, pinned Compose services, GHCR publishing, and an opt-in SSH job. It did not clearly distinguish a green CI publication from an updated Azure site, did not reconstruct the subscription/provider/quota commands already used, and did not provide one reviewed release, verification, rollback, lifecycle, and troubleshooting sequence.

The audit also exposed a current-state mismatch: CI is active, but CD is not. `verify-and-publish` tests and publishes each `main` commit. The `deploy` job remains disabled behind `AZURE_DEPLOY_ENABLED` and uses SSH, while the Azure NSG permits SSH only from the user's recorded `/32`. Opening SSH broadly to GitHub-hosted runners would weaken the reviewed network boundary.

### What was documented

- The four execution locations—local PowerShell, GitHub runner, Azure Cloud Shell, and commands executed inside the VM—and why mixing them caused earlier quoting and working-directory errors.
- Subscription selection, provider registration, quota/SKU interpretation, budget semantics, DNS choice, repository preparation, confirmation-gated provisioning, cloud-init, public-IP verification, and the exact resources created.
- The CI stages from Next.js/Java verification through commit-SHA GHCR publication, including why `:main` is not rollback evidence.
- A current manual immutable-release procedure using the full verified SHA and Azure VM Run Command loaded from a reviewed temporary file.
- Post-deployment API/container/browser checks, common failure diagnosis, forward-only migration constraints, rollback, VM deallocation/start, cost implications, and destructive final teardown.
- The target OIDC design, its least-privilege rationale, required GitHub environment/identity work, and the explicit warning that the repository's actual post-15-July-2026 OIDC subject must be verified before federation is configured.
- A system-design/interview explanation and pen-and-paper exercises.

### Important lesson

Deployment has three separate claims: source was committed, an artifact was verified/published, and a particular environment activated that artifact. Only the third changes the website. Stable DNS identifies the service; an immutable SHA identifies the software. Treating those as separate facts makes updates, audit, and rollback understandable.

### Sources and accuracy

The commands and security model were checked against current primary Microsoft Azure, GitHub Actions, Docker, and Caddy documentation on 20 July 2026. GitHub now documents changed immutable default OIDC subject behavior for repositories created, renamed, or transferred after 15 July 2026; Sentinel's OIDC setup therefore remains a separately verified implementation task rather than an invented credential string in the guide.

### Next action

Use the guide for the next manual immutable Azure release. Then implement and rehearse OIDC plus VM Run Command before enabling automatic CD or deleting the old SSH secrets.

---

## Session 27 — Passwordless Azure CD and budget containment

### Goal

Make a verified `main` update deploy to the existing stable Azure hostname automatically, explain why the earlier deployment job was skipped, and add a reversible budget response without pretending Azure budgets are hard spending caps.

### Delivery implementation

- Replaced the unreachable SSH/scp job with pinned Azure Login OIDC and Azure VM Run Command.
- Audited the repository's post-15-July-2026 immutable OIDC identity: owner ID `35369040`, repository ID `1304261078`, and GitHub environment `azure-demo`.
- Added a confirmation-gated Cloud Shell bootstrap that creates or reuses the Entra application, service principal, federated credential, custom role, and exact-VM assignment. It creates no client secret.
- Restricted GitHub to VM read, instance-view read, and Run Command. It cannot start, stop, resize, create, or delete compute.
- Added a portable release activator that validates the full SHA, requires the matching immutable GHCR tag, refuses tracked VM drift, checks out the exact source, preserves ignored secrets and named volumes, and waits for Compose readiness.
- Kept `AZURE_DEPLOY_ENABLED` as the last one-time switch. CI continues safely while identity variables are absent; once enabled, every green `main` push runs deployment and external readiness verification.
- Updated pinned checkout and Docker actions to current major releases, removing the earlier Node 20 action warning source.

### Cost-control implementation

- Added a confirmation-gated Cloud Shell bootstrap that connects the user's existing budget to an Action Group and Consumption Logic App.
- The Logic App uses its own managed identity and a custom role that can deallocate only `sentinel-demo-vm`; it cannot deploy, start, resize, or delete it.
- The default notification is 50% actual budget usage. With a `$10` budget this nominally reacts at `$5`, leaving margin for Azure's delayed cost records.
- Kept VM start authority away from GitHub so a later push cannot undo a budget stop.
- Made daily VM auto-shutdown optional because it limits unattended runtime but deliberately makes the public résumé link unavailable until a manual start.
- Rejected automatic resource-group deletion because it destroys the database and stable DNS resource. It also cannot create an exact cap when cost records arrive late.

### Accuracy boundary

Microsoft documents that budget notifications do not affect resources or stop consumption. Cost data is typically delayed 8–24 hours and budgets are evaluated periodically. Deallocation ends compute allocation but the managed disk, retained Standard static IP, and small automation execution costs can remain. Therefore “not a single penny after `$10`” is not a guarantee Azure Budgets can provide. Early deallocation is risk reduction; only explicit teardown ends this resource group's remaining lifecycle, and already accrued delayed cost cannot be reversed.

### Learning model

CI proves code and publishes an artifact. CD proves a particular environment activated that artifact. OIDC answers “which workflow is asking?” RBAC answers “what may it do?” Run Command is the control path that avoids public SSH. The cost guard is a separate failure-containment identity, so deployment authority cannot override financial safety.

The beginner guide now includes the one-time setup, every-push flow, failure boundaries, concurrency behavior, database/rollback implications, early budget wiring, optional time-based shutdown, cost-state table, verification commands, and common beginner questions with scenarios.

### Verification status and next action

All three shell scripts pass Bash syntax checks. Invalid release identity is rejected before mutation, both Azure bootstrap scripts refuse to run without their explicit confirmation values, the workflow parses as YAML, its OIDC permission/SSH-removal invariants pass, documentation fences are balanced, stale SSH deployment configuration is removed, and `git diff --check` is clean. No application code changed, so the already-green 113-test regression is not repeated locally; the pushed workflow will run the complete frontend/backend gate again.

Cloud identity creation and budget wiring require the owner's authenticated Azure Cloud Shell. They remain unchecked in `TODO.md` until the user runs both confirmation-gated scripts and one complete automated deployment is green.

---

## Session 28 — Recorded Azure identity and UI deployment walkthrough

### Goal

Preserve the exact real demo topology and reproduce the chat-assisted Azure/GitHub setup as a beginner-friendly visual sequence without publishing unnecessary Azure account fingerprints or any secret.

### Account-side progress

The owner ran `configure-github-oidc.sh` successfully from authenticated Azure Cloud Shell at commit `7a05a88`. Azure created or reused the `sentinel-github-deployer` Entra application, its service principal, the `sentinel-main-azure-demo` immutable federated credential, the `Sentinel Demo Release Activator` custom role, and the exact-VM assignment. The CLI's read-only role-definition attribute warning was non-fatal. No client secret was created.

The owner then entered all seven `azure-demo` GitHub environment variables and the repository-level `AZURE_DEPLOY_ENABLED=true` switch. Enabled workflow run `29699411314` completed successfully for exact SHA `7a05a88f6024cf6d5a050a4bd4efb47b39d32a72`: both `verify-and-publish` and `deploy` were green. Independent public checks returned `200 {"status":"UP"}` from readiness and HTTP `200` from a homepage containing the `Sentinel | Incident Operations Console` title.

### Durable inventory

- Azure: `Azure for Students`, Central India, 6 regional vCPUs, 4 BS-family vCPUs, `sentinel-demo-rg`, non-zonal `Standard_B4as_v2`, Ubuntu 24.04, 64-GiB Standard SSD.
- Network: static `20.219.22.24`, `sentinel-mofazzal874.centralindia.cloudapp.azure.com`, owner-only TCP 22, public TCP 80/443, VNet `10.40.0.0/16`, subnet `10.40.1.0/24`.
- GitHub: public owner ID `35369040`, repository ID `1304261078`, environment `azure-demo`, automation commit `7a05a88f6024cf6d5a050a4bd4efb47b39d32a72`.
- Cost: owner-reported approximately `$100` student credit with one-year validity and a `$10` demo budget. The budget remains an alert until the separate cost-guard setup is run and verified.

Tenant, subscription, and application client IDs are recognizable but partially masked in the public walkthrough. They are not authentication secrets, but publishing full Azure account fingerprints provides no learning benefit. SSH source IP and all actual credentials remain fully excluded.

### Documentation delivered

`docs/deployment/AZURE_GITHUB_UI_WALKTHROUGH.md` now follows ten screens in operational order: subscription, resource group, VM/public identity, Actions graph, GitHub environment variables, first automated run, first-failure diagnosis, budget, cost-guard verification, and deliberate start/deallocate/retire operations. Each screen includes the exact Sentinel values, UI route, Cloud Shell equivalent, expected result, and the system-design reason it exists.

The walkthrough also records a demonstration script, a recruiter-link checklist, masked-value notation, authority separation, and an explicit warning that Azure's delayed cost data prevents any exact `$10` guarantee.

### Next evidence

Automatic delivery is proven. Connect the exact existing `$10` budget to the early deallocation guard, verify the resulting Logic App, Action Group, budget notification, and deallocate-only role without intentionally consuming credit, then keep Cost Analysis under observation for Azure's delayed records.

---

## Session 29 — Product-shaped interactive operator experience

### Goal

Correct the portfolio demo's central failure: the first console exposed backend-shaped records but did not quickly explain the user problem, provide visual hierarchy, guide a first-time visitor, or make the real backend workflow feel interactive.

### Product definition recovered

Sentinel solves two connected on-call problems. Operational evidence is scattered across alerts, releases, metrics, logs, and runbooks, so diagnosis is slow. An AI-generated remediation can also increase blast radius if the model is allowed to authorize itself. Sentinel creates one durable evidence-backed investigation, permits the model to propose only, applies deterministic Java safety policy, and preserves the decision in an append-only ledger.

The public user is not expected to connect Kubernetes, Prometheus, or Grafana. Those are possible evidence providers in a production integration, not prerequisites for understanding or exercising this bounded portfolio sandbox.

### Experience rebuilt

- Replaced the sidebar data wall with five clear destinations: Overview, Live Lab, Incidents, Learn, and protected Admin.
- Added a problem-first landing experience that identifies the users, shows the alert-to-ledger pipeline, distinguishes real backend behavior from the safe synthetic digital twin, and provides one primary action.
- Replaced the scenario dropdown with four visual backend-owned scenario cards and a six-stage animated workflow. Completion still comes only from the polled durable API state.
- Added incident search and severity filtering, plus separate Incident Story, Evidence & AI, Safety Decision, and Audit Ledger views.
- Surfaced remediation rationale and risk notes that the backend already returned but the old UI hid.
- Added a five-lesson documentation/tutorial workspace, concrete payment-release example, glossary, and hands-on path.
- Raised the typography baseline and hierarchy, added responsive layouts, focusable controls, loading/error/empty states, motion, and reduced-motion behavior.
- Added Motion and Lucide as pinned project dependencies using the `E:\DevCaches\npm` cache; no global application was installed.

### Backend truth added

`GET /api/v1/demo/overview` performs bounded scalar aggregate queries under a read-only transaction. It reports the current PostgreSQL counts for teams, services, dependency edges, deployments, metric samples, logs, incidents, runbooks, public scenarios, visitor-triggered runs, and ledger events. It also reports the public environment's `DRY_RUN` execution mode and `PROPOSE_ONLY` model authority. The frontend therefore does not hard-code portfolio scale.

### Safety and system-design review

The new endpoint is anonymous only under the existing demo profile, returns aggregate synthetic-demo facts, accepts no input, exposes no JPA entity, and performs no mutation. The Live Lab still accepts only server-owned scenarios, uses idempotency and capacity limits, and exposes no prompt/action/policy input. Admin CRUD remains protected by the existing JWT/RBAC boundary.

### Iterative verification

The first UI test pass correctly failed because the tests still navigated through the removed sidebar and because the new overview request was not mocked. The test oracle was updated to exercise the new navigation, incident tabs, ungrounded escalation, Admin authentication boundary, and live-run transition.

Verified evidence:

- TypeScript strict check passed.
- Six frontend interaction/API tests across two suites passed, including the beginner learning path.
- Next.js production static export compiled and generated all pages.
- PostgreSQL/pgvector integration test passed, including exact overview counts and anonymous/protected boundary assertions.
- Full clean backend regression passed 113 tests across 38 suites with zero failures, errors, or skips. A Redis client emitted a non-failing event-loop shutdown race after the assertions completed; the test result and process exit remained successful.
- The npm production dependency audit found zero vulnerabilities, and `git diff --check` passed.
- No second hosting system was created: the existing Azure hostname and OIDC deployment pipeline remain the single delivery path.

### Learning insight

A technically real backend does not automatically create a believable product. A portfolio interface must translate system invariants into a user's decision sequence: understand the pain, cause a bounded event, observe real state, inspect evidence, understand the safety verdict, and verify history. Animation is useful only when it clarifies that sequence; API state remains authoritative.

### Next action

Run the full backend regression and dependency audit, inspect the final source diff, commit and push the coherent redesign, then watch the existing OIDC workflow deploy it to the stable Azure URL. The separate `$10` cost-guard bootstrap remains the only owner-side deployment prerequisite not yet verified.
