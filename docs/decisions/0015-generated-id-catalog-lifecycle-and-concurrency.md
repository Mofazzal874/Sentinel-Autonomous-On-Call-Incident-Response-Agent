# ADR 0015: Generated-ID catalog lifecycle and optimistic concurrency

## Status

Accepted.

## Context

Sentinel needs editable teams, services, dependencies, runbooks, allowlists, and fixed demo scenarios. Treating every resource as hard-deletable would break incident history and audit explanations. Accepting client-selected IDs or silent last-write-wins updates would make references unreliable and allow one administrator to overwrite another administrator's newer decision.

## Decision

PostgreSQL-backed JPA entities generate UUIDs for new catalog records. Create responses return those IDs for later URLs and foreign keys.

Teams, services, runbooks, and fixed scenarios use archive timestamps. They remain available to historical foreign keys but disappear from active fleet/retrieval queries. Dependency edges contain no independent history and may be hard-deleted. Runbook update/archive removes the previous embedding so stale text cannot remain eligible for semantic retrieval; the deployment indexing job rebuilds current active documents.

Every editable resource has a numeric JPA `@Version`. Update, archive, and dependency delete requests submit the version they read. An explicit stale check provides a clear `409 Conflict`, while the database update count remains the final race boundary when two transactions pass the first check concurrently.

Reads are bounded to at most 100 records per page. Method security allows operational roles to read and permits mutations only to `ADMIN`. Fixed scenarios use a closed enum and a real service reference; they do not carry arbitrary prompts or mutation commands.

## Consequences

- IDs are durable database facts, not temporary React variables.
- Incident and action history remains explainable after catalog retirement.
- Administrators must refresh after a conflict and consciously merge changes.
- Archived names remain globally reserved because existing unique constraints are retained; accidental identity reuse is impossible.
- Runbook changes are conservative: semantic use pauses until current content is indexed rather than serving a stale embedding.
- The public portfolio can show the locked admin workspace without granting administrative authority.

## Rejected alternatives

- Client-generated or sequential public IDs: easier to guess and places identity ownership in the wrong layer.
- Hard-delete all resources: breaks foreign keys or erases the context of past incidents.
- Last write wins: silently loses concurrent administrator changes.
- Browser-only CRUD: produces no reusable IDs and bypasses security, validation, transactions, and references.
- Free-form scenario definitions: creates an unreviewed path from public input toward agent or execution behavior.
