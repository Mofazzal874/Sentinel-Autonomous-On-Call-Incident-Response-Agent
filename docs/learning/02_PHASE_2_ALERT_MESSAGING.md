# Phase 2: Alert Ingestion, Redis, and RabbitMQ

This lesson explains Phase 2 from first principles. Its central question is: how can one real incident emerge when an alert is submitted 50 times and messages may be redelivered?

## 1. The completed flow

```text
POST /api/v1/alerts
        |
        v
validate DTO -> canonical fingerprint
        |
        v
Redis atomic claim ---- duplicate ----> 202 SUPPRESSED
        |
      first
        v
RabbitMQ confirmed publish -----------> 202 QUEUED
        |
        v
manual-ack consumer -> PostgreSQL ON CONFLICT DO NOTHING
        |                         |
     failure                    commit
        |                         |
 retry queue or DLQ              `----> ACK
```

HTTP `202 Accepted` means the work was accepted for asynchronous processing. It does not mean investigation or remediation is complete.

## 2. Deterministic fingerprinting

A fingerprint is a stable identifier for the meaning of an alert. Sentinel normalizes the service and alert name, sorts label keys, length-prefixes each value, and hashes the result with SHA-256.

Sorting matters because maps do not communicate semantic order:

```text
{region=us, env=prod}
{env=prod, region=us}
```

These must produce the same fingerprint. Length prefixes prevent ambiguous concatenations such as `ab + c` and `a + bc`.

The summary and firing time are deliberately excluded. They can vary across repeated notifications while the underlying condition remains the same. This is a product decision: changing which fields participate changes what Sentinel considers "the same alert."

## 3. Redis suppresses cost, PostgreSQL guarantees correctness

The first request runs one Redis Lua script that atomically:

1. creates the first-seen key with a TTL if absent;
2. optionally claims a hashed client idempotency key;
3. increments an expiring suppression counter for duplicates.

Atomic means another request cannot observe the operation halfway through. A separate `GET`, `SET`, and `EXPIRE` sequence would have race and crash windows.

Redis keys expire after ten minutes so a genuinely recurring alert can create a later incident. Redis may still restart, evict data, or be unavailable. Therefore it is not the source of truth. Sentinel bypasses failed Redis suppression, and PostgreSQL's unique fingerprint rejects duplicate effects.

Think of the layers this way:

```text
Redis      = save work
RabbitMQ   = preserve and deliver work
PostgreSQL = guarantee the final state
```

## 4. RabbitMQ vocabulary

- **Exchange:** routes published messages; it does not process them.
- **Routing key:** text the exchange uses when selecting bindings.
- **Queue:** stores messages until consumers handle them.
- **Binding:** a routing rule between an exchange and a queue.
- **Publisher confirm:** the broker acknowledges responsibility for the publish.
- **Consumer acknowledgement:** the application tells the broker processing finished.
- **Dead-letter queue:** stores messages that should not keep retrying.
- **Prefetch:** maximum unacknowledged messages delivered to one consumer.

Sentinel uses a topic exchange with routing key `incident.alert`. The primary queue routes rejected messages to the DLX. Transient failures are explicitly republished to a retry queue; its TTL delays delivery, then dead-letters the message back to the primary exchange.

## 5. Two acknowledgements solve different problems

Publisher confirms answer: "Did RabbitMQ accept responsibility for my message?"

Consumer acknowledgements answer: "Did Sentinel finish processing this delivered message?"

The publisher waits up to five seconds for a correlated confirm and also rejects mandatory returned messages. On failure, the REST endpoint returns `503` and removes the Redis claim so the sender can retry.

The consumer uses manual acknowledgement. Its order is essential:

```text
database transaction commits -> application method returns -> basicAck
```

If the process crashes after commit but before acknowledgement, RabbitMQ redelivers. PostgreSQL sees the same fingerprint and performs no second insert, then the consumer acknowledges. This is at-least-once delivery with an effectively-once database effect.

## 6. Bounded retries and poison messages

A transient failure may recover, such as a temporary database connection problem. Sentinel makes three total attempts, separated by the retry queue delay.

A permanent failure will not improve with time, such as a command naming an unknown service. It is rejected directly to `triage.dlq`. Exhausted transient failures also go there. The broker adds `x-death` metadata containing the queue and rejection history.

There is no unconditional infinite requeue. Infinite retry is dangerous because one poison message can repeatedly consume workers and hide healthy traffic.

## 7. Spring Boot code map

- HTTP boundary: `alert/api/AlertController.java`
- Validation contract: `alert/api/AlertPayload.java`
- Orchestration: `alert/application/AlertIngestionService.java`
- Fingerprint: `alert/application/AlertFingerprinter.java`
- Redis Lua boundary: `alert/application/AlertSuppressionService.java`
- Rabbit topology: `alert/config/RabbitTopologyConfiguration.java`
- Confirmed publisher: `alert/messaging/TriageCommandPublisher.java`
- Manual-ack consumer: `alert/messaging/TriageCommandConsumer.java`
- Idempotent sink: `incident/application/IncidentCreationService.java`

Spring creates these objects as beans and injects their collaborators. The controller knows the application operation, not Redis or RabbitMQ details. The consumer knows the incident operation, not repository SQL details.

## 8. What the tests establish

- Unit tests prove normalization, orchestration branches, confirms, acknowledgement order, retry limits, and poison handling.
- A real Redis container proves one claim plus 49 suppression counts and claim rollback after publish failure.
- A real PostgreSQL container proves repeated fingerprints create one incident.
- The combined PostgreSQL/Redis/RabbitMQ test posts 50 identical HTTP requests and observes one queued response, 49 suppressed responses, and one incident.
- It publishes the same command twice and proves one database effect.
- It proves a poison command reaches the DLQ with `x-death` context.
- It stops and starts the RabbitMQ application while a persistent command is queued, then proves delivery after recovery.

## 9. Security boundary

The alert webhook is intentionally unauthenticated only during this phase. Phase 3 must authenticate callers, authorize operations, and protect the endpoint against abuse. `Idempotency-Key` is not authentication; a caller-provided header cannot establish identity.

## 10. Notebook exercise

Draw the flow for one alert and mark every durable state. Then answer:

1. If Redis loses all keys, what prevents duplicate incidents?
2. If RabbitMQ accepts a message but HTTP times out, what happens when the sender retries?
3. If PostgreSQL commits and the process crashes before `basicAck`, why is redelivery safe?
4. Why must a retry publish be confirmed before acknowledging the original?
5. What operational alarm should exist for a growing DLQ?

## Defend This review

Answer these without reading the hints before closing the learning gate:

1. Why must claim and TTL be one atomic Redis operation? What if Redis evicts early?
2. Why is manual acknowledgement used instead of automatic acknowledgement?
3. Which part is at-least-once, and which effect is effectively once?
4. What is a poison message, and why does it go to a DLQ?
5. Why use Redis when PostgreSQL already has a unique constraint?
6. How do prefetch and concurrency affect throughput, memory, and ordering?
7. Why are virtual threads useful for these blocking listeners?
