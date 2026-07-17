# ADR 0003: Layered Idempotent Alert Delivery

- Status: Accepted
- Date: 2026-07-18

## Context

Alert senders retry, alert storms repeat the same condition, brokers redeliver after failures, and consumers can crash between database work and acknowledgement. Sentinel must avoid both message loss and duplicate incident effects without claiming an impossible distributed exactly-once guarantee.

## Decision

- A canonical SHA-256 fingerprint identifies the semantic alert from normalized service, alert name, and sorted labels.
- Redis atomically claims first-seen fingerprints and counts duplicates for a bounded window. An optional hashed client idempotency key covers HTTP retries whose payload changes slightly.
- Redis is an efficiency layer. If it is unavailable, ingestion continues to RabbitMQ.
- RabbitMQ exchanges, primary queue, retry queue, dead-letter exchange, and DLQ are durable. Commands are persistent and publishers require correlated confirms and mandatory returns.
- Consumers acknowledge manually only after the incident transaction returns successfully.
- PostgreSQL's unique incident fingerprint plus `ON CONFLICT DO NOTHING` is the final correctness boundary.
- Transient database failures use a delayed retry queue with three total attempts. Permanent failures and exhausted retries are rejected to the DLQ without requeue.

## Consequences

Benefits:

- Alert storms are filtered before expensive downstream work.
- Broker redelivery cannot create a second incident.
- Unavailable Redis degrades efficiency rather than correctness.
- Poison commands stop looping and retain their payload plus RabbitMQ `x-death` history.
- Publisher failure is visible to the HTTP client as `503`, and the Redis claim is released for a safe retry.

Costs:

- Redis, RabbitMQ, and PostgreSQL each have a different role that operators must understand.
- End-to-end delivery is at-least-once; only the database effect is effectively once.
- A DLQ needs monitoring and an explicit replay procedure in a later operational phase.

## Rejected alternatives

- Database uniqueness alone: correct but wastes broker, consumer, and future agent capacity during storms.
- Redis as the correctness boundary: eviction or outage could violate the guarantee.
- Automatic acknowledgement: can lose a message after receipt but before a committed incident.
- Infinite `requeue=true`: a poison command can consume capacity forever.
- Claiming distributed exactly-once delivery: RabbitMQ and PostgreSQL do not share one atomic transaction.
