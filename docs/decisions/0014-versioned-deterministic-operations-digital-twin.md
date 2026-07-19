# ADR 0014: Versioned deterministic operations digital twin

## Status

Accepted.

## Context

A blank database makes an incident-response product impossible to evaluate. Importing a large public research trace would add licensing, schema, startup-time, and causal-consistency problems without demonstrating Sentinel's safety boundary. A few frontend constants would look polished but would not exercise the real persistence and query paths.

## Decision

The `demo` Spring profile seeds an original deterministic operations digital twin into Sentinel's authoritative tables. Dataset version 1 contains 4 teams, 12 services, 18 service dependencies, at least 60 deployments, 10 runbooks, 30 complete incident histories, 10,800 metric samples, and 1,080 structured log events.

The generator records completion in `demo_dataset_version`. Re-running the same version is a no-op. Future dataset changes use a new version rather than mutating the meaning of a recorded version.

Incident records use deterministic UUIDs derived from stable seed keys. Later user-created catalog and runbook records use database-persisted generated UUIDs through the CRUD application layer. Public run summaries are stored in `demo_run`; the frontend does not duplicate scenario content.

Every generated incident has causal evidence in bounded windows. A correlated deployment is never newer than its incident. Nearby metrics expose five signal names, nearby logs carry trace IDs, and histories exercise missing-grounding escalation, dry-run, approval-required, automatic simulated resolution, and compensation.

## Consequences

- A fresh demo database is useful immediately and produces the same reviewable stories on every machine.
- Database queries, foreign keys, indexes, append-only ledger enforcement, and API projections are exercised rather than mocked in the browser.
- The data is production-shaped, not production data. The UI and documentation must say so clearly.
- The dataset is intentionally moderate so it fits the single Azure student VM and local E-drive Docker storage.
- The `demo` profile is unsafe for a real customer database because it intentionally inserts synthetic records; deployment configuration must opt into it explicitly.

## Rejected alternatives

- Shipping a blank database: reviewers could not understand the product without manually creating a fleet and incidents.
- Hard-coded frontend examples: these bypass persistence and can drift from backend behavior.
- Copying a public trace wholesale: the source schemas and research scale do not match Sentinel's transactional incident model.
- Random data on every startup: it makes failures, screenshots, tests, and interview explanations irreproducible.
