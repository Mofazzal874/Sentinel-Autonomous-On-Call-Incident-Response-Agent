# ADR 0016: Bounded public live-scenario sandbox

## Status

Accepted.

## Context

A portfolio visitor needs to operate Sentinel, not merely read pre-seeded histories. Direct anonymous alert bodies, prompts, or action selections would create an abuse path into model work and safety-sensitive orchestration. A browser retry must also not create duplicate incidents or consume unbounded cloud resources.

## Decision

The public API exposes only active administrator-authored scenario templates. The server converts the selected closed scenario type into a bounded alert and deterministic synthetic evidence. It then uses the normal Redis suppression, RabbitMQ delivery, PostgreSQL incident sink, agent workflow, deterministic guardrail, and ledger path.

PostgreSQL gives each submission a public UUID and records its state. A unique hash of client identity and `Idempotency-Key` makes submission retry durable. Redis atomically enforces a per-client minute allowance, a global daily allowance, and a short global concurrency lease; loss of Redis rejects new public work. The lease is released after lifecycle completion or terminal failure and expires after a crash.

The consumer invokes lifecycle listeners before acknowledging RabbitMQ. This makes the public status transition part of the successful durable-processing boundary. Demo mode seeds evidence before publishing so the normal bounded tools can retrieve it. Public runs remain dry-run, and no action claim may be created.

## Consequences

- A visitor can generate and inspect a genuine new incident at the stable URL.
- The frontend remains an untrusted selector and status viewer; it cannot define work.
- PostgreSQL protects identity and retry correctness while Redis bounds cost and concurrency.
- A broker redelivery can repeat processing safely because incident and submission transitions are idempotent.
- Forwarded client addresses are only an abuse-control hint, not authentication; global limits still bound spoofing.
- Failed evidence generation can leave harmless synthetic telemetry, but cannot publish an unbounded or executable request.

## Rejected alternatives

- Anonymous free-form alerts or prompts: too much control and unpredictable model cost.
- Browser-only simulation: visually interactive but proves none of the backend system.
- Redis-only identity: expiration or restart could recreate durable work.
- A separate demo execution implementation: would drift from the system being demonstrated.
- Automatic remediation in the public sandbox: unnecessary portfolio risk.
