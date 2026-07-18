# ADR 0004: Stateless Security and Deterministic Tool Boundary

- Status: Accepted
- Date: 2026-07-18

## Context

Phase 4 will let a model choose read operations. Before that can be safe, Sentinel needs authenticated identities, explicit authorization, an under-privileged agent identity, authenticated alert intake, and read operations whose cost and output are deterministic without a model.

## Decision

- Sentinel is an OAuth2 resource server and validates signed JWT signature, issuer, audience, expiration, and not-before claims on every protected request.
- A `roles` claim maps to Spring authorities with the `ROLE_` prefix. Roles are `VIEWER`, `SRE_APPROVER`, `ADMIN`, and `AGENT`.
- HTTP sessions are stateless and CSRF is disabled only because bearer credentials are sent explicitly rather than automatically through cookies.
- The alert route is permitted by the JWT authorization layer but requires a timestamped HMAC-SHA256 over the exact request body. Bodies are capped at 64 KiB and signatures use constant-time comparison.
- Local development uses project-local random HS256 and webhook secrets generated under ignored `.sentinel/` storage on `E:`. Production must replace symmetric JWT verification with an external asymmetric issuer/JWK configuration.
- The agent may call the same bounded read tools as authorized humans but cannot pass approval or admin rules.
- Deploy, metric, log, and runbook tools are ordinary Java components with immutable DTOs, validated inputs, hard result/time bounds, read-only transactions, and method guards.
- No Spring AI dependency or mutating tool is introduced. Phase 4 will adapt these Java methods to model-callable descriptions; Phase 5 owns approval and all mutations.

## Consequences

Benefits:

- A compromised or mistaken model cannot self-approve or administer the system.
- Requests do not inherit server session state; each call presents its identity.
- Alert authentication is independent of interactive/user JWT issuance.
- Tool behavior, database cost, and serialized payload size are testable without an LLM.
- Persistence entities and lazy proxies never cross the future model boundary.

Costs:

- Local HMAC JWT keys are suitable only for single-service development; symmetric verifiers can also mint tokens.
- Timestamped webhook HMAC limits replay time but does not by itself prevent replay inside the tolerance window; Redis/database idempotency handles repeated effects.
- Lexical runbook search is intentionally less capable than semantic retrieval until Phase 4.
- Role matchers prove access to the future approval boundary, while the audited approval business operation remains absent until Phase 5.

## Rejected alternatives

- Giving the agent `ADMIN` or `SRE_APPROVER`: violates least privilege and permits self-approval after prompt injection or model error.
- Cookie/session authentication for the API: adds server session state and changes the CSRF threat model.
- Leaving `/api/v1/alerts` truly anonymous: permits forged incidents and resource exhaustion.
- Exposing repositories or JPA entities as tools: leaks persistence behavior and allows unbounded/model-shaped access.
- Adding Spring AI annotations in Phase 3: couples deterministic tools to orchestration before their headless contracts pass.
