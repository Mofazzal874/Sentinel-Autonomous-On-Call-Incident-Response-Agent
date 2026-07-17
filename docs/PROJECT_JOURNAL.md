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
