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

## Product question before framework question

The interface must answer three questions in order:

1. **Why does this exist?** On-call engineers lose time joining scattered evidence, while an unconstrained AI fix can worsen an outage.
2. **What does Sentinel do?** It turns an alert into a durable investigation, lets AI propose, lets deterministic Java policy decide, and records the result.
3. **How can I prove it?** Configure a bounded incident, watch its durable status, then inspect its raw stored evidence, recommendation, safety decision, and ledger.

The original console started at question three. It looked like a database viewer and forced a visitor to infer the product. The revised console begins with the problem and gives one obvious hands-on path.

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

- `frontend/app/page.tsx`: product overview, navigation, searchable incident explorer, evidence/safety/audit views, and learning center.
- `frontend/app/ScenarioLauncher.tsx`: parameterized incident builder plus API-reported workflow and event stream.
- `frontend/app/EvidenceConsole.tsx`: filterable, copyable terminal view assembled from the selected run's database evidence.
- `frontend/app/LearningCenter.tsx`: six guided modules, knowledge checks, saved progress, glossary, and hands-on routes.
- `frontend/app/ThemeControl.tsx`: saved light/dark appearance with the operating-system preference as the first default.
- `frontend/lib/demo-api.ts`: typed API boundary.
- `frontend/app/experience.css`: readable responsive hierarchy, interaction states, motion, and reduced-motion behavior.
- `frontend/app/globals.css`: original shared/catalog styles retained for the protected administrative workspace.
- `frontend/app/page.test.tsx`: mocked API interaction tests.
- `frontend/next.config.ts`: static-export decision.
- `build.gradle.kts`: includes the finished export in the application artifact.
- `deployment/azure-demo/Caddyfile`: stable TLS hostname and proxy.

## Concrete example

When the page starts, it requests `GET /api/v1/demo/runs` and `GET /api/v1/demo/overview`. The overview counts are scalar database queries, not marketing constants. Selecting a public UUID requests its detail projection. The browser renders transcript entries, proposal rationale, risk notes, gate result, and ledger facts, but it cannot change an incident because no mutation API is called and the ordinary incident API still requires a JWT.

In the Live Lab, the browser reads a server-owned vocabulary plus the 12 active seeded services. The visitor combines service, symptom, severity, signal strength, customer impact, and recent-change context. Spring validates and persists those fields, generates bounded telemetry, publishes one idempotent request, and returns a generated public UUID. Arbitrary prompts, commands, URLs, SQL, and remediation choices are not accepted.

The evidence preview also comes from the API: five series with 12 samples each, eight logs, and optionally one deployment. After completion, the raw console formats the selected run's actual projection. Its filter and wrapping state are presentation-only; no console text is accepted as a backend command.

## Failure modes and safeguards

- Backend unavailable: the UI shows a connection error and retry action.
- Slow detail request: the investigation panel shows a skeleton rather than stale content.
- Unknown run: Spring returns `404`; the frontend enters a safe error state.
- Static assets exposed: only UI assets and reviewed demo DTOs are anonymous; operational APIs remain authenticated.
- Frontend fabricates business state: forbidden by using API DTOs rather than hard-coded incident objects.
- Terminal becomes theatrical: avoided by deriving every evidence line from the selected database projection and clearly labeling the launch stream as API-state narration.
- Public configurability becomes remote control: avoided with enumerated incident facts, rate limits, idempotency, dry-run, and no prompt/action/target fields.
- Motion misrepresents progress: the launcher advances only to completed after the API returns `COMPLETED`, and labels the animation as a visualization of polled durable state.
- Tiny operations-style typography: avoided with a 16 px baseline, 14–19 px supporting copy, large task headings, and monospace reserved for identifiers/timestamps.
- Motion harms accessibility: `prefers-reduced-motion` collapses animation and transition duration.
- Extra runtime consumes VM memory: avoided through static export; no Node process runs in Azure.

## Design tradeoffs

Static export cannot perform server-side Next.js actions at runtime. That is desirable here because Spring Boot already owns the server boundary. A separate Node service would add memory, failure modes, dependency patching, and routing without adding necessary product capability.

Synthetic data is deliberate, not fake UI state. A public incident-response demo needs repeatable failures without exposing customer telemetry or granting infrastructure authority. The PostgreSQL records, message delivery, agent workflow, safety policy, and ledger are real; the fictional company and injected outages are the safe digital twin.

Motion and Lucide are local frontend dependencies. Motion communicates state changes and view transitions; Lucide supplies consistent accessible vector icons. Neither owns business state or adds a server process.

Light and dark themes change CSS presentation only. The browser stores the preference locally; theme choice is not user identity and does not enter the backend. The first visit follows the operating-system preference.

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
5. Explain why a synthetic outage can still prove real distributed-systems behavior.
6. Mark which progress states the browser may infer and which must come from the backend.
7. Explain why a bounded parameterized incident is more useful than one fixed scenario but safer than a free-form prompt.
8. Pick one raw console line and trace it backward to its PostgreSQL table and forward to the operator decision it supports.
