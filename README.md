# Sentinel

Sentinel is an autonomous on-call and incident-response system. It combines a transactional Spring backend with a bounded AI decision layer that gathers evidence and proposes remediation. Deterministic guardrails—not the model—decide whether an action may run.

## Current status

Phase 0 is complete. Phase 1's engineering deliverables are implemented and verified: healthy Docker infrastructure, a Flyway-owned PostgreSQL/pgvector schema, the simulated-fleet JPA model, bounded evidence queries, an idempotent seed profile, a DTO-based fleet endpoint, and real PostgreSQL Testcontainers coverage. The Phase 1 learning/Defend This review remains the exit gate before Phase 2. The active work queue is maintained in [TODO.md](TODO.md).

## Prerequisites

- Git
- Java 25 LTS
- PowerShell 7 or Windows PowerShell
- Docker Desktop with the WSL 2 backend (installed under `E:\Docker`).

The development JDK is installed under `E:\DevTools\temurin-25`, and Gradle caches are directed to `E:\DevCaches\gradle` to avoid filling `C:`. Gradle itself is supplied by the checked-in wrapper.

Docker Desktop application files are under `E:\Docker\Docker`, and its private WSL disks are under `E:\Docker\wsl`. Open a new terminal after installation so the machine PATH update is visible.

Open a new terminal after initial setup so persisted environment variables are available. Alternatively, initialize the current PowerShell session from the repository root:

```powershell
. .\scripts\dev-env.ps1
```

## Build and test

Start local infrastructure and wait for health:

```powershell
docker compose up -d --wait
```

Sentinel's PostgreSQL is published on `localhost:55432` because another local PostgreSQL process already owns IPv4 port `5432`. Redis uses `6379`; RabbitMQ uses `5672` and its management UI is at http://localhost:15672.

```powershell
.\gradlew.bat clean test
```

Run the application with the repeatable synthetic incident evidence:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=seed"
```

Read the fleet API at http://localhost:8080/api/v1/fleet/services.

## Engineering rules

The durable architecture, safety invariants, phase gates, environment policy, and contribution workflow are recorded in [AGENTS.md](AGENTS.md). The chronological build record is maintained in [docs/PROJECT_JOURNAL.md](docs/PROJECT_JOURNAL.md), while the beginner curriculum starts at [docs/learning/README.md](docs/learning/README.md). Key constraints include:

- Build the Spring and distributed-systems core before adding the agent.
- Keep safety decisions deterministic and outside the model.
- Route every mutation through one guardrail gate.
- Layer Redis suppression with database idempotency rather than treating Redis as the correctness boundary.
- Never commit secrets or the private planning directory.

## Phase roadmap

1. Simulated-fleet JPA domain and Flyway migrations.
2. Alert intake, Redis deduplication, and RabbitMQ processing.
3. JWT/RBAC security and deterministic read tools.
4. Structured agent orchestration and grounded runbook retrieval.
5. Guardrails, risk scoring, action ledger, approval, and compensation.
6. Observability, test harnesses, and Azure deployment.
