# Sentinel System Design Workbook

This workbook evolves with the implementation. It focuses on reasoning rather than framework syntax.

## 1. Problem statement

Sentinel receives operational alerts, gathers evidence, proposes a remediation, applies deterministic safety rules, automatically performs only bounded low-risk actions, and escalates everything else.

The central design tension is:

```text
respond quickly  <-------------------->  avoid causing a larger outage
```

The architecture must support both speed and safety.

## 2. Functional requirements

- Accept alert webhooks quickly.
- Suppress duplicate alert storms.
- Process alerts asynchronously.
- Correlate incidents with deployments, metrics, logs, and runbooks.
- Produce grounded remediation proposals.
- Execute only allowlisted, deterministic low-risk actions.
- Require human approval for higher-risk actions.
- Preserve a complete audit history.

## 3. Non-functional requirements

- Idempotency under retries and concurrency.
- Durable messaging and persistence.
- Least-privilege security.
- Bounded model cost and runtime.
- Explainability and auditability.
- Reversible or compensatable execution.
- Observable latency, tool calls, decisions, and outcomes.

## 4. High-level component map

```text
Alert sender
    |
    v
REST intake -> Redis suppression -> RabbitMQ -> Incident transaction
                                               |
                                               v
                                      deterministic tools
                                               |
                                               v
                                      AI orchestration
                                               |
                                               v
                                      GuardrailGate
                                       /        \
                              low risk          high risk
                                 |                 |
                                 v                 v
                             executor        human approval
                                 |
                                 v
                           action ledger
```

## 5. Current infrastructure boundary: Docker

Docker provides repeatable local infrastructure. It is not part of Sentinel's production domain model.

```text
Docker CLI -> Docker Engine API -> Linux containers
                                   |-- PostgreSQL/pgvector
                                   |-- Redis
                                   `-- RabbitMQ
```

Local storage location is hidden behind the Docker Engine API. Whether the engine stores layers on `C:` or `E:` does not change image names, Compose services, exposed ports, Docker Hub, or cloud deployment.

### Image versus container versus volume

- Image: immutable packaged filesystem and metadata used as a template.
- Container: one running or stopped instance created from an image.
- Volume: persistent data managed separately from a container lifecycle.
- Bind mount: a direct mapping from a host path into a container.

Example for Phase 1:

```text
pgvector image -> postgres container -> pgdata volume
```

Deleting and recreating the container should not delete the database volume.

## 6. Design-review template for every component

When a component is added, answer:

1. What problem does it solve?
2. What inputs and outputs cross its boundary?
3. What state does it own?
4. What happens when it is slow?
5. What happens when it is unavailable?
6. Can a request be repeated safely?
7. What is the transaction boundary?
8. How is it authenticated and authorized?
9. Which metrics reveal failure?
10. What simpler alternative was rejected, and why?

## 7. Pen-and-paper scenario

Trace this alert through the future system:

```text
payments-api error rate rises from 1% to 18%
five minutes after deployment version 2026.07.18.3
```

Write down:

- Which component receives the alert first?
- Where is its fingerprint calculated?
- What prevents 50 copies from launching 50 agent runs?
- Which repository query finds the latest deployment before the spike?
- What evidence must exist before rollback can be proposed?
- Why is the model not allowed to call rollback directly?
- Which durable record proves whether rollback already happened?

Revisit this scenario at the end of each phase and fill in the newly implemented answers.
