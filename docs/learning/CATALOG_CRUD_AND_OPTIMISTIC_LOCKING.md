# Learning note: catalog CRUD, archival, and optimistic locking

## Prerequisites

Review HTTP methods, JSON, database primary/foreign keys, Spring controllers, service-layer transactions, JPA entities, and JWT roles.

## Plain-language definitions

- **CRUD:** create, read, update, and delete.
- **Generated ID:** an identifier created by the persistence layer and returned to the caller.
- **Archive:** mark a record inactive while preserving it for history and references.
- **Optimistic locking:** detect that another transaction changed a record since you read it.
- **Lost update:** one user's save silently overwrites another user's newer save.
- **Referential integrity:** a foreign key cannot point to a record that does not exist.
- **Allowlist:** the closed set of actions policy permits for a service.

## Local behavior

An administrator opens Catalog, pastes a short-lived ADMIN JWT, and loads bounded pages from `/api/v1/catalog`. A create form does not contain an ID. Spring validates the request, starts a transaction, constructs the entity, and PostgreSQL persists the generated UUID. The response returns that UUID and version `0`.

An edit sends the last version the browser read. After one successful update, JPA increments it. Reusing the old version returns `409 Conflict`.

## System-design flow

```text
Admin browser
  -> Bearer JWT
  -> Spring Security (signature + role)
  -> Catalog controller (JSON validation)
  -> Catalog service (@Transactional + @PreAuthorize)
  -> JPA entity invariant + version comparison
  -> PostgreSQL constraints and optimistic update
  -> generated UUID/version response
```

The browser is not the security boundary. Hiding the form from a viewer is convenient UX; the backend role check is what prevents the write.

## Domain-specific delete rules

| Resource | Removal behavior | Reason |
|---|---|---|
| Team | Archive | Incidents/services must retain ownership context |
| Service | Archive | Telemetry, deployments, and incidents still reference it |
| Runbook | Archive + remove embedding | Past proposals retain the reference; stale text cannot be retrieved |
| Fixed scenario | Disable + archive | Existing runs remain explainable |
| Dependency edge | Hard delete | It is current topology configuration, not an audit fact |
| Action ledger | Never delete | It is append-only evidence |

## Concrete lost-update example

1. Asha and Noor both read service version `3`.
2. Asha changes the owner. PostgreSQL updates version `3 -> 4`.
3. Noor submits a tier change with version `3`.
4. Sentinel returns `409 Conflict` instead of overwriting Asha's owner change.
5. Noor reloads version `4`, reviews both fields, and decides whether to retry.

## Code map

- `V8__catalog_lifecycle_and_optimistic_locking.sql`: lifecycle/version columns and fixed-scenario schema.
- `CatalogAdministrationController`: bounded HTTP contract.
- `CatalogAdministrationService`: authorization, transactions, conflict checks, archive rules, and DTO mapping.
- `CatalogContracts`: validated request and response records.
- `Team`, `FleetService`, `Runbook`, `ServiceDependency`, `ScenarioTemplate`: local domain invariants.
- `CatalogExceptionHandler`: stable `400`, `404`, and `409` responses.
- `CatalogWorkspace.tsx`: protected forms and conflict feedback.
- `CatalogAdministrationIntegrationTest`: real security and PostgreSQL lifecycle proof.

## Failure modes and safeguards

- Missing/invalid JWT: `401 Unauthorized`.
- Valid viewer token attempts mutation: `403 Forbidden`.
- Stale version: `409 Conflict`.
- Duplicate name/key/dependency: database constraint becomes `409 Conflict`.
- Archived resource reused for new configuration: service rejects it.
- Self-dependency: Java and PostgreSQL both reject it.
- Arbitrary scenario command: closed enum deserialization rejects it with `400`.
- Oversized page: service rejects anything above 100.
- Stale runbook vector: embedding is deleted on content update/archive.

## Tradeoffs

Optimistic locking is appropriate because catalog edits are infrequent and conflicts should be rare. Pessimistic locks would hold database locks while an administrator thinks and types. Archive consumes storage, but operational catalog rows are small and historical correctness is more valuable than reclaiming a few kilobytes.

The current portfolio accepts a pasted development token only in memory. A production user experience should use an external OpenID Connect provider and short-lived asymmetric tokens; Sentinel is a resource server, not a password database or token issuer.

## Verification

```powershell
$env:GRADLE_USER_HOME='E:\DevCaches\gradle'
.\gradlew.bat test --tests io.mofazzal.sentinel.fleet.api.CatalogAdministrationIntegrationTest
.\gradlew.bat clean test

$env:npm_config_cache='E:\DevCaches\npm'
npm --prefix frontend test
npm --prefix frontend run typecheck
npm --prefix frontend run build
```

For local manual use, start the application, create an ADMIN token with `scripts/new-dev-token.ps1 -Role ADMIN`, open Catalog, and paste the short-lived token. Never commit or share the secret used to mint it.

## Interview defense

Locally, CRUD manages operational reference data. In system design, archive preserves the time dimension and optimistic locking protects concurrent human decisions. In an interview, emphasize that database constraints and versions are correctness boundaries; React validation only improves feedback.

## Pen-and-paper exercises

1. Draw the request path for creating a service and label every validation boundary.
2. Simulate two updates starting from version `7` and show which SQL update succeeds.
3. Explain why deleting a dependency edge is safe but deleting an action-ledger row is not.
4. List the records that still need a service after it has been archived.
5. Explain why a fixed scenario enum is safer than accepting an arbitrary prompt.
