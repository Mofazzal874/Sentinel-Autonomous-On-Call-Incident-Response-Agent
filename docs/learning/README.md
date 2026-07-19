# Sentinel Learning Path

This directory turns the implemented project into a self-study course. It assumes you are new to Spring Boot and the surrounding infrastructure.

## How the notes are organized

Use all three views together:

1. **Build history:** `../PROJECT_JOURNAL.md` explains what changed during each working session.
2. **System design:** `SYSTEM_DESIGN_WORKBOOK.md` explains components, boundaries, data flows, failure modes, tradeoffs, and scaling.
3. **Phase lessons:** each phase note explains the framework concepts and maps them to real Sentinel code.

The build order is also the learning order:

- Phase 0: Java, Gradle, Spring Boot startup, configuration, and the first test.
- Phase 1: HTTP, layered architecture, dependency injection, JPA, SQL, Flyway, transactions, DTOs, and query performance.
- Phase 2: asynchronous messaging, Redis, RabbitMQ, delivery guarantees, deduplication, and idempotency.
- Phase 3: authentication, authorization, JWT, filter chains, least privilege, and deterministic tools.
- Phase 4: LLM orchestration, structured output, tool calling, embeddings, vector search, RAG, and bounded evaluation loops.
- Phase 5: deterministic safety gates, risk models, audit ledgers, exactly-once effects, strategies, and compensation.
- Phase 6: metrics, traces, testing nondeterminism, container images, cloud deployment, and operations.

## Recommended study routine

For each lesson:

1. Read the plain-language explanation without opening the code.
2. Draw the request or data flow by hand.
3. Predict which class owns each responsibility.
4. Open the linked code and trace one scenario line by line.
5. Run the verification command and deliberately change one input.
6. Answer the notebook questions without looking at the notes.
7. Explain the design aloud as if speaking to an interviewer.

Do not memorize annotations in isolation. Learn the problem each annotation solves and what would happen without it.

## Core mental model

Sentinel has three broad layers:

```text
Inputs and APIs  ->  deterministic application core  ->  infrastructure
                          |
                          v
                   AI decision layer
                          |
                          v
                   deterministic safety gate
```

The AI is not the backend. It is one decision-producing component inside a backend whose persistence, messaging, security, and execution rules remain ordinary, testable software.

## Current notes

- [Phase 0: Java, Gradle, and Spring Boot foundations](00_PHASE_0_FOUNDATIONS.md)
- [Phase 1: fleet persistence and the first read API](01_PHASE_1_FLEET_PERSISTENCE.md)
- [Phase 2: alert ingestion, Redis, and RabbitMQ](02_PHASE_2_ALERT_MESSAGING.md)
- [Phase 3: JWT security, RBAC, and deterministic tools](03_PHASE_3_SECURITY_TOOLS.md)
- [Phase 4: bounded agent orchestration and audit memory](04_PHASE_4_AGENT_CORE.md)
- [Phase 5: deterministic guardrails and safe execution](05_PHASE_5_GUARDRAILS.md)
- [Phase 6: operability, evaluation, packaging, and deployment](06_PHASE_6_OPERABILITY.md)
- [Azure deployment and GitHub CI/CD: start-to-finish beginner guide](../deployment/AZURE_BEGINNER_DEPLOYMENT_GUIDE.md)
- [System Design Workbook](SYSTEM_DESIGN_WORKBOOK.md)
- [Living Glossary](GLOSSARY.md)

Phase-specific files will be added and expanded as their code is implemented.
