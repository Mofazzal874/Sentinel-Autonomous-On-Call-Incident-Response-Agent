# ADR 0007: Single Gate, Durable Claim, and Immutable Event Ledger

- Status: accepted
- Date: 2026-07-19

## Context

Infrastructure execution crosses a database/external-side-effect boundary. A database transaction cannot atomically commit an external action. A crash can occur after reservation, during the side effect, or after the effect but before its result is recorded. At-least-once delivery can also race two executors.

The plan calls the ledger append-only while also requiring `IN_PROGRESS` to become `APPLIED` or `FAILED`. Updating one historical row would weaken the append-only claim.

## Decision

- Keep mutable correctness state in `action_claim`, with a permanent unique `(fingerprint, action_type)` reservation.
- Commit the `IN_PROGRESS` claim and its ledger event in `REQUIRES_NEW` before calling a strategy.
- Run the strategy without an enclosing database transaction from the coordinator. Simulated strategy state owns its own short transaction and uses a unique claim effect for idempotency.
- Record `APPLIED` or `FAILED` in a separate `REQUIRES_NEW` transaction.
- Store every decision, execution transition, approval, rejection, and compensation as a new `action_ledger` row. A PostgreSQL trigger rejects ledger updates and deletes.
- Require an opaque authorization issued only inside the `guardrail` package by `GuardrailGate`. The executor and claim/result writers verify it matches the incident, service, fingerprint, action, and decision.
- Retry a durable `PENDING_DECISION`. Never automatically retry stale `EXECUTING`/`IN_PROGRESS` work because its side-effect outcome is uncertain; escalate it while the unique claim continues blocking duplicates.

## Consequences

- Redis loss cannot permit duplicate execution; PostgreSQL is the correctness boundary.
- Two concurrent executors can obtain at most one claim and one simulated effect.
- A crash after the side effect may leave `IN_PROGRESS`, but it cannot silently repeat the action. Operators have an explicit reconciliation fact.
- Failed completed work is compensated and recorded as new linked events; history is not rewritten.
- Permanent failed/uncertain claims favor safety over automatic retries. A future manual reconciliation workflow must explicitly resolve them.
- The append-only guarantee is enforced by the database, not application convention.
