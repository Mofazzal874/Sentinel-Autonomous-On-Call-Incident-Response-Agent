# Live scenario sandbox: from a click to a guarded incident

## Prerequisites

Review HTTP request/response, database unique constraints, Redis atomic commands, RabbitMQ acknowledgement, and Sentinel's agent-versus-guardrail boundary.

## Plain-language model

The browser chooses one reviewed failure story. Spring, not the browser, creates the alert and realistic evidence. PostgreSQL remembers the request, Redis decides whether capacity is available, RabbitMQ carries the work, the agent proposes from bounded evidence, and deterministic Java decides that the public run is dry-run.

An idempotency key answers: “Have I already accepted this click?” A fingerprint answers: “Does this alert describe an incident already known?” They solve different duplicate problems and both are needed.

## Request and data flow

1. `GET /api/v1/demo/scenarios` returns active fixed templates.
2. `POST /api/v1/demo/scenarios/{id}/runs` requires an `Idempotency-Key`.
3. PostgreSQL checks durable retry identity; Redis atomically reserves bounded capacity.
4. Sentinel writes causal metrics, logs, and—where relevant—a deployment near the generated incident time.
5. The existing ingestion service fingerprints the server-owned alert and publishes a triage command.
6. RabbitMQ delivers at least once. The consumer creates the incident idempotently and dispatches the bounded agent.
7. Read tools collect the generated evidence; the model-shaped test roles classify, propose, and critique.
8. `GuardrailGate` evaluates validated Java facts and records `DRY_RUN`; no action claim or external side effect exists.
9. The lifecycle listener links the incident to the public run, marks completion, releases capacity, and only then permits the broker acknowledgement.
10. The UI polls submission status and opens the real transcript, remediation decision, and ledger.

## Code map

- `DemoLiveScenarioController`: narrow anonymous HTTP contract.
- `DemoLiveScenarioService`: orchestration and server-owned alert construction.
- `DemoLiveSubmissionStore`: short transactional state changes and durable idempotency.
- `DemoSandboxRateLimiter`: one atomic Redis capacity decision.
- `DemoScenarioEvidenceSeeder`: deterministic causal telemetry.
- `TriageCommandConsumer` and `DemoLiveTriageLifecycle`: completion before acknowledgement.
- `ScenarioLauncher.tsx`: selection, retry key, polling, and transition to the resulting run.

## Concrete example

A visitor selects “Bad deployment.” Sentinel creates a fresh payments alert plus a deployment five minutes earlier, a rising error-rate series, and traceable error logs. The agent retrieves the rollback runbook and proposes rollback. The gate still owns authority and, because this is the public demo, records a dry-run and escalates the incident without executing anything.

## Failure modes and defenses

- Double-click or HTTP retry: the same idempotency key returns the same public UUID.
- Duplicate broker delivery: database uniqueness prevents a second incident.
- Traffic spike: per-client, daily, and concurrent Redis limits reject excess work.
- Redis outage: submission fails closed instead of running without limits.
- Consumer crash before acknowledgement: RabbitMQ redelivers; durable transitions are idempotent and the lease has a TTL.
- Model error or missing grounding: the workflow escalates; the model cannot authorize an action.
- Malicious browser input: no free-form payload, prompt, or action exists in the public contract.

## Design tradeoffs

Fixed scenarios provide less experimentation than arbitrary alerts, but make cost, evidence, tool access, and behavior reviewable. Synthetic evidence is less authentic than a real production integration, but it is reproducible, privacy-safe, and immediately useful on an empty database. Polling is simpler and more robust for a small demo than WebSockets; streaming can be added later if scale requires it.

## Verification commands

```powershell
.\gradlew.bat test --tests "io.mofazzal.sentinel.demo.DemoLiveScenarioIntegrationTest" --tests "io.mofazzal.sentinel.alert.messaging.TriageCommandConsumerTest"
Set-Location frontend
npm test -- --run
npm run typecheck
npm run build
```

The integration gate uses real PostgreSQL/pgvector, Redis, and RabbitMQ containers. It asserts one durable run, generated evidence, five transcript stages, a dry-run ledger event, no action claim, daily limiting, and protection of ordinary APIs.

## Three levels to remember

- Locally: a click creates a new inspectable dry-run incident.
- System design: durable idempotency protects correctness, Redis bounds load, RabbitMQ decouples work, and the gate protects authority.
- Interview defense: the demo reuses the production-shaped pipeline while constraining anonymous input and cost at multiple independent boundaries.

## Pen-and-paper exercises

1. Draw the flow and circle each durable commit. Put the RabbitMQ acknowledgement last.
2. Explain what happens if the process crashes after incident creation but before acknowledgement.
3. Compare an idempotency key, an alert fingerprint, and an action claim.
4. Write the three rate-limit counters and decide which must expire.
5. Explain why dry-run records a ledger fact but must not reserve an executable action claim.
