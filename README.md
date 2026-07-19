# Sentinel

Sentinel is an autonomous on-call and incident-response system. It combines a transactional Spring backend with a bounded AI decision layer that gathers evidence and proposes remediation. Deterministic guardrails—not the model—decide whether an action may run.

## Current status

The transactional control plane is implemented through alert intake, durable triage, grounded proposals, deterministic guardrails, dry-run/human approval, observability, evaluation, and non-root packaging. The Azure student VM and stable DNS name exist. The repository also contains a substantial operations digital twin, incident console, protected catalog administration, bounded public fixed-scenario runner, and passwordless OIDC delivery through exact-VM Azure Run Command. Detailed evidence is recorded in [TODO.md](TODO.md) and [the project journal](docs/PROJECT_JOURNAL.md).

## Architecture

```text
signed alert -> Redis suppression -> RabbitMQ (at-least-once)
                                      |
                              unique PostgreSQL incident
                                      |
                       classify -> bounded evidence -> RAG
                                      |
                             grounded proposal/evaluator
                                      |
                         deterministic GuardrailGate
                                      |
                 dry-run / human approval / idempotent strategy
                                      |
                      append-only action ledger + metrics/traces
```

The model proposes; it never decides whether a mutation is safe. PostgreSQL uniqueness is the correctness boundary, Redis is an efficiency layer, RabbitMQ acknowledges only after durable processing, and every mutation path passes through one deterministic gate.

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

Live-model evaluation is opt-in and excluded from that command. See [the evaluation method](docs/evaluation/README.md) and [the measured Qwen3 4B baseline](docs/evaluation/2026-07-19-qwen3-4b-baseline.md).

Build the static Next.js console and run the application with the full repeatable demo dataset:

```powershell
$env:npm_config_cache='E:\DevCaches\npm'
npm --prefix frontend ci
npm --prefix frontend run build

.\gradlew.bat bootRun --args="--spring.profiles.active=seed,demo"
```

Open http://localhost:8080. The public incident console reads the same PostgreSQL records used by the backend. The `demo` profile is for synthetic environments only; never enable it against a real operational database.

Create a short-lived local viewer token and call the protected fleet API:

```powershell
$token = .\scripts\new-dev-token.ps1 -Role VIEWER -Subject local-viewer
Invoke-RestMethod http://localhost:8080/api/v1/fleet/services `
    -Headers @{ Authorization = "Bearer $token" }
```

Read at most 20 open incidents through the same authenticated boundary:

```powershell
Invoke-RestMethod 'http://localhost:8080/api/v1/incidents?status=OPEN&limit=20' `
    -Headers @{ Authorization = "Bearer $token" }
```

The token helper permits only the four declared roles and a maximum 60-minute lifetime. It is a local-development convenience, not an authorization server. Production deployment must use externally managed asymmetric keys and issuer/JWK discovery.

To use the protected Catalog workspace locally, create an administrator token and paste it into the Catalog screen. The token remains only in page memory:

```powershell
$token = .\scripts\new-dev-token.ps1 -Role ADMIN -Subject local-admin
```

Teams, services, runbooks, dependencies, and fixed sandbox scenarios use generated PostgreSQL UUIDs. Historical resources archive instead of being erased, and stale edits return `409 Conflict`.

## Engineering rules

The durable architecture, safety invariants, phase gates, environment policy, and contribution workflow are recorded in [AGENTS.md](AGENTS.md). The chronological build record is maintained in [docs/PROJECT_JOURNAL.md](docs/PROJECT_JOURNAL.md), while the beginner curriculum starts at [docs/learning/README.md](docs/learning/README.md). Key constraints include:

- Build the Spring and distributed-systems core before adding the agent.
- Keep safety decisions deterministic and outside the model.
- Route every mutation through one guardrail gate.
- Layer Redis suppression with database idempotency rather than treating Redis as the correctness boundary.
- Never commit secrets or the private planning directory.

## Safety and deployment

Local remediation defaults to dry-run. High-risk actions require an `SRE_APPROVER`, approval re-enters the same gate, and neither a human nor the agent can bypass kill-switch, allowlist, grounding, or idempotency checks. Compensation appends a new fact rather than rewriting history.

The demo bundle runs the verified image plus pinned Compose dependencies on one Azure Linux VM. A static IP/DNS name keeps the résumé URL stable while GitHub Actions tests each `main` commit, publishes an immutable GHCR image, exchanges repository-bound OIDC for a short-lived Azure token, activates that exact release through VM Run Command, and checks public readiness. It is a demo topology, not a highly available production topology. Start with the [click-by-click Azure/GitHub walkthrough](docs/deployment/AZURE_GITHUB_UI_WALKTHROUGH.md), study the [deep Azure and CI/CD guide](docs/deployment/AZURE_BEGINNER_DEPLOYMENT_GUIDE.md), use the [short operator runbook](docs/deployment/LOCAL_AND_AZURE_DEMO.md) during releases, and consult the [deployment readiness note](docs/deployment/DEPLOYMENT_READINESS.md) for production-shaped alternatives.
