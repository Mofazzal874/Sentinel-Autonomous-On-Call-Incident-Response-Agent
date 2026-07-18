# Sentinel

Sentinel is an autonomous on-call and incident-response system. It combines a transactional Spring backend with a bounded AI decision layer that gathers evidence and proposes remediation. Deterministic guardrails—not the model—decide whether an action may run.

## Current status

Phases 0–3 are complete. Sentinel now has the reproducible build and Docker baseline, Flyway-owned fleet/evidence persistence, idempotent Redis/RabbitMQ alert ingestion, stateless JWT/RBAC security, HMAC-authenticated alert intake, and four bounded deterministic read tools. All engineering and Defend This gates through Phase 3 are recorded in [TODO.md](TODO.md).

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

The environment script creates two cryptographically random local development secrets once under ignored `.sentinel/` project storage on `E:`. It reuses them on later runs and never prints their values.

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

Create a short-lived local viewer token and call the protected fleet API:

```powershell
$token = .\scripts\new-dev-token.ps1 -Role VIEWER -Subject local-viewer
Invoke-RestMethod http://localhost:8080/api/v1/fleet/services `
    -Headers @{ Authorization = "Bearer $token" }
```

The token helper permits only the four declared roles and a maximum 60-minute lifetime. It is a local-development convenience, not an authorization server. Production deployment must use externally managed asymmetric keys and issuer/JWK discovery.

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
