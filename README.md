# Sentinel

Sentinel is an autonomous on-call and incident-response system. It combines a transactional Spring backend with a bounded AI decision layer that gathers evidence and proposes remediation. Deterministic guardrails—not the model—decide whether an action may run.

## Current status

Phase 0 (repository and build bootstrap) is in progress. The application intentionally contains no database, broker, cache, security, or AI integration yet. Those capabilities are introduced and verified in strict phase order.

## Prerequisites

- Git
- Java 25 LTS
- PowerShell 7 or Windows PowerShell
- Docker is not required during Phase 0 and is intentionally deferred.

The development JDK is installed under `E:\DevTools\temurin-25`, and Gradle caches are directed to `E:\DevCaches\gradle` to avoid filling `C:`. Gradle itself is supplied by the checked-in wrapper.

Open a new terminal after initial setup so persisted environment variables are available. Alternatively, initialize the current PowerShell session from the repository root:

```powershell
. .\scripts\dev-env.ps1
```

## Build and test

```powershell
.\gradlew.bat clean test
```

Run the minimal application:

```powershell
.\gradlew.bat bootRun
```

## Engineering rules

The durable architecture, safety invariants, phase gates, environment policy, and contribution workflow are recorded in [AGENTS.md](AGENTS.md). Key constraints include:

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

