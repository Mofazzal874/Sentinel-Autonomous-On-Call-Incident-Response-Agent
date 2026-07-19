# Learning note: Next.js operator console over a Spring Boot control plane

## Prerequisites

Understand HTTP requests, JSON, a browser, an API endpoint, static files, and a reverse proxy.

## Plain-language definitions

- **Frontend:** code the browser renders and uses for interaction.
- **Backend:** server code that owns rules, security, transactions, and database access.
- **Static export:** HTML, CSS, and JavaScript produced during the build instead of by a permanent Node server.
- **Same origin:** the page and API share the same scheme, hostname, and port, avoiding a separate cross-origin trust relationship.
- **Hydration:** React attaching interaction behavior to HTML after it reaches the browser.
- **Reverse proxy:** the public entry point that forwards requests to an internal server.

## Request flow

```text
Browser
  -> HTTPS sentinel-mofazzal874.centralindia.cloudapp.azure.com
  -> Caddy TLS and proxy
  -> Spring Boot
       -> / and /_next/*: packaged Next.js export
       -> /api/v1/demo/*: bounded public DTOs
       -> /api/v1/incidents/*: JWT-protected control plane
       -> PostgreSQL authoritative records
```

Next.js does not access PostgreSQL. It calls Spring Boot, and Spring Boot applies validation, authorization, transactions, bounds, and DTO mapping.

## Code map

- `frontend/app/page.tsx`: operations overview, incident queue, investigation, proposal, decision, and ledger.
- `frontend/lib/demo-api.ts`: typed API boundary.
- `frontend/app/globals.css`: responsive operations-console visual system.
- `frontend/app/page.test.tsx`: mocked API interaction tests.
- `frontend/next.config.ts`: static-export decision.
- `build.gradle.kts`: includes the finished export in the application artifact.
- `deployment/azure-demo/Caddyfile`: stable TLS hostname and proxy.

## Concrete example

When the page starts, it requests `GET /api/v1/demo/runs`. Selecting a public UUID requests its detail projection. The browser renders transcript entries and remediation facts, but it cannot change an incident because no mutation API is called and the ordinary incident API still requires a JWT.

## Failure modes and safeguards

- Backend unavailable: the UI shows a connection error and retry action.
- Slow detail request: the investigation panel shows a skeleton rather than stale content.
- Unknown run: Spring returns `404`; the frontend enters a safe error state.
- Static assets exposed: only UI assets and reviewed demo DTOs are anonymous; operational APIs remain authenticated.
- Frontend fabricates business state: forbidden by using API DTOs rather than hard-coded incident objects.
- Extra runtime consumes VM memory: avoided through static export; no Node process runs in Azure.

## Design tradeoffs

Static export cannot perform server-side Next.js actions at runtime. That is desirable here because Spring Boot already owns the server boundary. A separate Node service would add memory, failure modes, dependency patching, and routing without adding necessary product capability.

## Verification

```powershell
$env:npm_config_cache='E:\DevCaches\npm'
npm --prefix frontend test
npm --prefix frontend run typecheck
npm --prefix frontend run build
npm --prefix frontend audit --audit-level=moderate
```

The full rehearsal additionally builds the Spring image, starts PostgreSQL/Redis/RabbitMQ, and verifies console `200`, demo API `200`, protected incidents `401`, protected Prometheus `401`, health, migrations, and runbook indexing.

## Interview defense

Locally, Next.js provides a responsive operator interface. In the system design, it is an untrusted client of a policy-owning Spring API. In an interview, emphasize that same-origin static delivery reduces operational cost while preserving backend authorization; hiding a button is never treated as a security control.

## Pen-and-paper exercises

1. Draw the request path for the homepage and for `/api/v1/demo/runs`.
2. Explain why the browser must never connect directly to PostgreSQL.
3. List what would break if frontend code were trusted to enforce `SRE_APPROVER`.
4. Compare a static export with a permanent `next start` process for this VM.
