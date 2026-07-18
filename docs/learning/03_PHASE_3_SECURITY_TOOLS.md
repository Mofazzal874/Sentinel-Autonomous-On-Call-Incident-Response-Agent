# Phase 3: JWT Security, RBAC, and Deterministic Read Tools

Phase 3 builds the trust boundary before introducing a model. It assumes no previous Spring Security knowledge.

## 1. Authentication and authorization are different

- **Authentication** answers: who made this request, and can the credential be trusted?
- **Authorization** answers: may that authenticated identity perform this operation?

```text
Bearer token -> verify JWT -> create Authentication -> check URL/method role -> controller
```

A valid token is not permission to do everything. A valid `AGENT` token can read evidence but still receives `403 Forbidden` at approval and admin boundaries.

## 2. JWT in plain language

A JWT has three base64url-encoded parts:

```text
header.payload.signature
```

The payload contains claims such as subject (`sub`), issuer (`iss`), audience (`aud`), expiration (`exp`), and roles. Encoding is not encryption; anyone holding a token can read its claims. Trust comes from verifying the signature and validation rules.

Sentinel validates:

- HS256 signature for local development;
- expected issuer `sentinel-local` by default;
- expected audience `sentinel-api` by default;
- expiration and not-before/time claims;
- the `roles` claim, mapped to `ROLE_VIEWER`, `ROLE_SRE_APPROVER`, `ROLE_ADMIN`, or `ROLE_AGENT`.

Local secrets are randomly generated under ignored `.sentinel/` storage on `E:`. HS256 is a development choice: every verifier holding the symmetric key could also mint a token. Production must use an external identity provider with asymmetric signing and issuer/JWK discovery, so Sentinel receives only verification material.

## 3. The Spring Security filter chain

For `GET /api/v1/fleet/services`:

```text
HTTP request
  -> BearerTokenAuthenticationFilter extracts Authorization: Bearer ...
  -> JwtDecoder checks signature + issuer + audience + time
  -> JwtAuthenticationConverter maps roles to authorities
  -> AuthorizationFilter applies URL rules
  -> FleetController executes
```

No token produces `401 Unauthorized`. A trusted identity lacking a required role produces `403 Forbidden`.

Spring Security 7 also adds `FACTOR_BEARER` to JWT authentication. This records that bearer-token possession satisfied an authentication factor; it exists alongside application roles.

## 4. Stateless sessions and CSRF

`SessionCreationPolicy.STATELESS` means Sentinel does not store login state in an HTTP session or send a `JSESSIONID`. Every protected request presents and validates its bearer token.

CSRF protection primarily prevents a browser from being tricked into sending automatically attached credentials, such as session cookies. Sentinel disables CSRF because this API uses explicit Authorization headers and no cookie authentication. If JWTs later move into cookies, this decision must be reversed and the browser flow redesigned.

Stateless does not mean no state anywhere. PostgreSQL, Redis, and RabbitMQ still hold application state; only server-side login session state is absent.

## 5. RBAC and the under-privileged agent

| Role | Phase 3 boundary |
|---|---|
| `VIEWER` | Read fleet evidence and deterministic tools |
| `SRE_APPROVER` | Read evidence and pass the future approval URL boundary |
| `ADMIN` | Read evidence and pass `/api/v1/admin/**` rules |
| `AGENT` | Read evidence and tools; rejected at approval and admin boundaries |

The actual audited approval operation arrives in Phase 5 with proposals, guardrails, and an action ledger. Phase 3 deliberately proves access control without inventing an unsafe partial mutation.

Least privilege limits blast radius. Prompt injection, hallucination, or compromise of the agent identity cannot turn an `AGENT` token into human approval or administration.

## 6. Why the alert endpoint uses HMAC instead of user JWT

Alertmanager-style senders are machine webhooks, not human users. The Spring URL layer permits the route, then `WebhookHmacFilter` independently verifies:

```text
HMAC-SHA256(secret, unixTimestamp + "." + exactRequestBody)
```

The sender supplies `X-Sentinel-Timestamp` and `X-Sentinel-Signature`. Sentinel accepts only a five-minute clock window, limits the body to 64 KiB, decodes the body only once, and compares signature bytes in constant time.

The timestamp reduces old replay. It does not stop replay within five minutes. Redis suppression and PostgreSQL uniqueness make repeated authenticated alerts safe. HMAC establishes sender authenticity; idempotency establishes repeated-effect safety.

## 7. A tool is ordinary Java, not AI

The model will only choose a tool in Phase 4. In Phase 3 each tool is a normal method:

```text
validated input -> read-only transaction -> bounded repository query -> immutable DTO
```

The four tools are:

1. `DeployQueryTool`: newest three deployments before a timestamp.
2. `MetricsQueryTool`: maximum six-hour/360-sample window, downsampled to at most 20 points with baseline/current averages and percentage delta.
3. `LogSearchTool`: ERROR-only search within at most ±one hour, normalized into at most ten clusters with at most five trace IDs each.
4. `RunbookRetrieveTool`: at most five lexical matches. pgvector semantic retrieval is deferred to Phase 4.

Hard bounds protect database capacity and the future model context window. DTOs prevent JPA proxy serialization, lazy-loading surprises, and accidental schema coupling. No tool mutates anything.

## 8. Code map

- Security chain and decoder: `security/SecurityConfiguration.java`
- Externalized validated settings: `security/SecurityProperties.java`
- Webhook verification: `security/webhook/WebhookHmacFilter.java`
- Local token helper: `scripts/new-dev-token.ps1`
- Tools: `tools/DeployQueryTool.java`, `MetricsQueryTool.java`, `LogSearchTool.java`, `RunbookRetrieveTool.java`
- Query boundaries: fleet repositories under `fleet/repository/`

## 9. Verification evidence

- Real HS256 tokens prove signature, issuer, audience, time, subject, and role conversion.
- Invalid audience, issuer, and expired tokens are rejected.
- HTTP tests prove `401`, viewer/agent reads, agent `403` at approval/admin rules, approver/admin passage, and absence of session cookies.
- HMAC tests prove valid body preservation plus missing, stale, wrong-body, and oversized rejection paths.
- Tool unit tests prove validation, calculations, clustering, limits, and entity-to-DTO mapping.
- A real PostgreSQL test under `ROLE_AGENT` finds the bad deployment, calculates a 1700% metric increase, clusters timeouts, and retrieves the rollback runbook.
- The complete suite has 47 tests with zero failures or errors.

Run everything:

```powershell
. .\scripts\dev-env.ps1
.\gradlew.bat clean test --no-daemon
```

## 10. Failure modes and tradeoffs

- Expired/wrong-audience token: `401`; obtain a correctly scoped token.
- Valid viewer at admin path: `403`; authentication is not authorization.
- Stolen agent token: attacker retains only agent permissions until expiry; rotate the signing secret and move production to asymmetric issuer keys.
- Clock drift: can reject JWT or webhook timestamps; use synchronized clocks and a deliberately small tolerance.
- Webhook body changed by a proxy: signature fails because exact bytes differ; sign what Sentinel receives.
- Huge tool window: rejected before a repository call.
- Unknown service: a clear `ToolInputException` lets the future orchestrator recover rather than query everything.
- Empty lexical runbook result: return no result and escalate; do not invent a runbook.

## 11. Pen-and-paper exercises

1. Draw a request and label authentication, URL authorization, method authorization, transaction, and repository boundaries.
2. Explain why a signed token with `ROLE_AGENT` still cannot approve.
3. Write the HMAC signing input for timestamp `1000` and body `{}`. Which byte change invalidates it?
4. Compare `401` and `403` using two concrete Sentinel requests.
5. For 10,000 metric samples, show where Phase 3 rejects or bounds the data before a model can see it.
6. Explain why lexical runbook search is acceptable now and why pgvector belongs in Phase 4.

## 12. Defend This review — completed

### 1. Walk the filter chain

`BearerTokenAuthenticationFilter` extracts the token, `JwtDecoder` validates it, `JwtAuthenticationConverter` creates authorities, and `AuthorizationFilter` evaluates URL rules before the controller. `@PreAuthorize` adds a method interceptor around guarded tool methods.

### 2. Why stateless?

No `HttpSession` or `JSESSIONID` stores authentication. Every request authenticates from its bearer token, which simplifies horizontal scaling and prevents stale server login state. It does not remove application/database state.

### 3. Why disable CSRF?

Credentials arrive through an explicit bearer header, not automatically attached cookies. CSRF does not protect token theft and is not needed for this non-cookie API. Cookie-based authentication would require CSRF protection again.

### 4. `hasRole` versus `hasAuthority`

`hasRole('SRE_APPROVER')` applies Spring's conventional `ROLE_` prefix and checks `ROLE_SRE_APPROVER`. `hasAuthority('ROLE_SRE_APPROVER')` checks the exact string. Sentinel maps the JWT `SRE_APPROVER` value to that prefixed authority once.

### 5. Why under-privilege the agent?

It limits prompt-injection, hallucination, credential-theft, and implementation-error blast radius. The agent cannot administer the system or approve its own future high-risk proposal; a separate human role is required.

### 6. How is `permitAll` intake protected?

JWT authorization permits the machine webhook route, but a separate filter requires a timestamped body HMAC. Deployment can additionally use mTLS or network policy. Redis/database idempotency protects replayed effects.

### 7. Why tool DTOs instead of entities?

DTOs form a stable serialization boundary, contain no lazy JPA proxies, expose only intended fields, enforce bounded payload shape, and decouple future model contracts from database schema.

All seven decisions are tied to implemented code, tested failure paths, and explicit tradeoffs. The Phase 3 learning/defense gate is complete.
