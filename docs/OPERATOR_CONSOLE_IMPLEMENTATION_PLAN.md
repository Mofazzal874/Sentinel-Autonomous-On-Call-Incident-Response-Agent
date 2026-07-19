# Sentinel operator console implementation plan

## Product motto

Turn noisy production alerts into evidence-backed, safely controlled incident actions.

## Users

- An on-call engineer investigates incidents and follows evidence.
- An SRE approver accepts or rejects high-risk proposals.
- A platform administrator manages the service catalog, runbooks, allowlists, dry-run policy, and kill switch.
- An engineering manager or auditor reviews outcomes and the immutable action ledger.
- A public portfolio visitor explores recorded incidents and runs bounded sandbox scenarios without receiving production authority.

## Data strategy

Public datasets were evaluated before choosing the shipped data model:

- The [Alibaba microservices trace](https://github.com/alibaba/clusterdata) provides production-shaped service dependencies, request rates, and latency patterns at very large scale.
- The [Tsinghua NetMan AIOps datasets](https://github.com/NetManAIOps) provide real-world anomaly, root-cause, and failure-diagnosis benchmark structures.
- [Microsoft AIOpsLab](https://microsoft.github.io/AIOpsLab/) demonstrates fault injection into observable microservice environments with metrics, traces, and logs.

The repository will not copy a huge research dataset into PostgreSQL. Those sources are optimized for research workloads, have different schemas, may require surveys or research-use attribution, and would make startup and review unnecessarily expensive. Sentinel will instead ship an original deterministic operations digital twin whose topology, cardinalities, anomaly shapes, and failure modes are informed by those sources.

Target baseline:

- 4 teams and 12 services across checkout, payments, catalog, identity, notifications, and platform domains.
- Explicit upstream/downstream service dependencies and criticality tiers.
- At least 50 deployments with good, failed, rolled-back, and incident-correlated releases.
- At least 10 versioned runbooks covering rollback, restart, scale-out, cache, dependency, and escalation paths.
- At least 30 coherent incident histories spanning all severities and terminal outcomes.
- At least 10,000 bounded metric samples and 750 structured log events with trace IDs.
- Alert storms, ambiguous failures, capacity exhaustion, dependency outages, bad releases, denied actions, dry-runs, approvals, and compensation histories.

Every record must be internally consistent: timestamps line up, incidents reference existing services and deployments, evidence supports or contradicts the outcome, and ledger events follow valid state transitions.

## CRUD boundary

CRUD is applied according to domain semantics rather than forcing deletion everywhere:

| Resource | Create | Read | Update | Delete/archive rule |
|---|---:|---:|---:|---|
| Teams | Yes | Yes | Yes | Archive when referenced |
| Services | Yes | Yes | Yes | Archive when referenced |
| Service dependencies | Yes | Yes | Yes | Hard delete is safe |
| Runbook drafts | Yes | Yes | Yes | Archive published versions |
| Allowlist entries | Yes | Yes | Yes | Hard delete is a valid policy change |
| Sandbox scenario templates | Yes | Yes | Yes | Hard delete when unused; archive otherwise |
| Incidents | Created by alerts | Yes | State transitions only | Archive from ordinary views; never erase |
| Agent transcripts | Workflow only | Yes | No | Never delete |
| Action ledger | Workflow only | Yes | No | Database-enforced append-only |

Editable resources use generated UUIDs, DTO validation, service-layer transactions, optimistic locking, and referential checks. IDs returned by create operations become stable URLs and foreign keys; they are not frontend-only variables.

## Phase 1 — Product and information architecture

Deliverables:

- Define roles, navigation, page hierarchy, responsive behavior, loading/error/empty states, and public-versus-authenticated boundaries.
- Freeze the dataset scale and CRUD semantics above.
- Establish acceptance criteria for a recruiter: understand the product in 15 seconds, open a coherent incident in one click, and trace evidence to a gate decision without documentation.

Gate: plan, architecture, and safety boundaries are recorded before expanding implementation.

## Phase 2 — Readable incident operations console

Deliverables:

- Pinned Next.js and React workspace with a static export and no production Node process.
- Operations overview, incident queue, investigation timeline, proposal, grounding confidence, deterministic risk, gate decision, and ledger.
- Responsive desktop/mobile layouts and accessible keyboard/focus/loading/error states.
- Real API data only; no duplicated hard-coded incident objects in the frontend.

Gate: production build and type checking pass, and all three recorded scenarios render from Spring Boot projections.

## Phase 3 — Substantial operations digital twin

Deliverables:

- Forward-only schema changes for dependencies, lifecycle/archive metadata, and any missing operational dimensions.
- Deterministic, idempotent data generator at the target baseline scale.
- Coherent causal scenarios with bounded time-window queries and useful indexes.
- Database tests for counts, referential consistency, repeatability, causal time order, and query bounds.

Gate: a fresh demo database is immediately useful and the data can be defended as synthetic but production-shaped.

## Phase 4 — Catalog and runbook management

Deliverables:

- Generated-ID create/read/update/archive APIs for teams, services, dependencies, runbooks, and scenario templates.
- Optimistic version checks prevent lost updates; referenced resources cannot be destructively deleted.
- Authenticated administration UI with forms, validation feedback, pagination, confirmation, and conflict handling.
- A restricted public workspace may exercise CRUD only against isolated disposable records; it never changes the authoritative safety policy.

Gate: PostgreSQL integration and authorization tests cover successful CRUD, validation, concurrent update conflict, forbidden access, and safe deletion rules.

## Phase 5 — Live incident sandbox

Deliverables:

- Server-owned scenarios submit validated alerts through Redis, RabbitMQ, PostgreSQL, the agent, guardrail gate, and ledger.
- Each run receives a generated public UUID and durable status.
- The UI polls bounded status/timeline projections and explains slow local-model stages.
- Per-client rate limits, global concurrency, daily caps, timeouts, and dry-run are mandatory.

Gate: concurrent submissions cannot duplicate incidents, exceed the model budget, or create an infrastructure action claim.

## Phase 6 — Human operations and safety views

Deliverables:

- Approval inbox, safe read-only public simulation, administrative kill-switch/allowlist views, audit explorer, and service topology.
- Authentication-aware navigation for VIEWER, SRE_APPROVER, ADMIN, and AGENT boundaries.
- Empty, stale, failure, expired-approval, and recovery states are visible and understandable.

Gate: role matrix, accessibility, responsive layout, and complete operator journeys pass.

## Phase 7 — Stable deployment and portfolio evidence

Deliverables:

- Caddy serves the frontend at `/` and routes `/api/*` to Spring Boot under the existing Azure hostname.
- CI builds immutable backend/frontend artifacts, uses Azure OIDC plus VM Run Command, and verifies readiness plus a real demo API.
- Full backend, frontend, Compose, security, and external browser checks pass.
- Journal, learning notes, architecture, demo instructions, screenshots, and accurate resume evidence are updated.

Gate: a new visitor can use the permanent URL without setup, while protected and destructive functionality remains inaccessible.
