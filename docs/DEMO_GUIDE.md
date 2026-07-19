# Sentinel live demo guide

This is the shareable navigation guide for the public Sentinel deployment.

## Open the system

### [https://sentinel-mofazzal874.centralindia.cloudapp.azure.com/](https://sentinel-mofazzal874.centralindia.cloudapp.azure.com/)

No account is required for the public investigation experience. Administrative and operational mutation APIs remain protected.

## What is Sentinel?

Sentinel helps an on-call engineer move from “an alert fired” to an evidence-backed, safely controlled response.

It brings deployments, metrics, logs, traces, and runbooks into one durable investigation. A local AI model classifies and proposes. Deterministic Java policy decides whether the proposal must be refused, skipped, simulated, approved by a human, or allowed to execute.

The public system uses synthetic operational data because a portfolio visitor must not receive customer data, credentials, or infrastructure authority. The database writes, queue delivery, evidence retrieval, AI calls, guardrail checks, generated identifiers, and audit records are real.

## Ten-minute reviewer journey

### 1. Understand the problem on Overview

Read the first screen from top to bottom.

- **Observed signal** represents the evidence an operator starts with.
- **AI proposal** shows that the model can recommend an action.
- **Java guardrail** shows that recommendation and authorization are separate.
- **Live database snapshot** is loaded from PostgreSQL; it is not a hard-coded marketing counter.

What this proves: the project has a user problem and a control model, not only a technology stack.

### 2. Create an incident in Live lab

Open **Live lab** and choose:

| Field | Suggested first run | Why |
|---|---|---|
| Affected service | `payments-api` | Clear customer-facing service |
| Observed failure | Release regression | Easy deployment/metric correlation |
| Severity | SEV2 | Serious but not an exaggerated total disaster |
| Signal strength | High | Produces clear correlated evidence |
| Customer impact | Partial outage | Some payment requests fail |
| Change context | Recent deployment | Gives the investigation a change hypothesis |

Before submitting, read the evidence contract. The backend—not the frontend—declares how many metric and log facts it will create.

Click **Create investigation** once.

What happens next:

```text
browser request
  -> validated bounded configuration
  -> generated public UUID
  -> PostgreSQL submission + synthetic evidence
  -> Redis suppression and capacity checks
  -> RabbitMQ durable triage message
  -> incident transaction
  -> local Qwen investigation
  -> pgvector runbook retrieval
  -> deterministic safety decision
  -> append-only audit event
```

The Azure VM runs the Qwen3 4B model on CPU. A complete grounded investigation can take approximately five minutes. The event stream reports elapsed time every 30 seconds and preserves the generated ID. This latency is real and intentionally documented rather than hidden behind a fake animation.

### 3. Read the Response brief

After completion, click **Open evidence-backed report**.

The first tab answers three operator questions:

- What was the customer impact?
- What incident type is most likely?
- What should happen next, and in which safety mode?

Below the brief, the persisted transcript explains how Sentinel reached the result.

### 4. Inspect Evidence & AI

This tab separates AI reasoning from the final safety decision.

Look for:

- classification;
- bounded evidence summary;
- retrieved runbook;
- remediation proposal;
- evaluator critique;
- remediation rationale and known risk.

What this proves: the model response is grounded in operational evidence and recorded for review.

### 5. Inspect Raw console

The terminal-like console is built from the selected run's actual API projection.

- `QUERY` shows deployment lookup and query results.
- `METRIC` shows sample count, minimum, maximum, and latest values.
- `LOG` shows timestamp, level, trace ID, and message.
- `AGENT` shows persisted model workflow events.
- `POLICY` shows the guardrail and ledger result.

Use the filters, toggle wrapping, or copy the evidence. These controls only change presentation; console text is never sent to the server as a command.

What this proves: the frontend reflects backend facts instead of imitating a terminal with static strings.

### 6. Defend the Safety decision

Open **Safety decision**.

The model does not own this result. Deterministic Java checks:

1. global kill switch;
2. per-service action allowlist;
3. deterministic risk score;
4. existing action claim/idempotency;
5. dry-run policy;
6. automatic threshold;
7. human approval where required.

The public environment returns `DRY_RUN` for a grounded proposal, so it records what would happen without changing infrastructure.

### 7. Verify the Audit ledger

Open **Audit ledger**.

The ledger is append-only. Actions, failures, approvals, results, and compensation are new facts; history is never rewritten to make an outcome look cleaner.

What this proves: Sentinel is designed for operational accountability and crash recovery, not only model output.

### 8. Learn the system

Open **Learn**.

Complete the six modules in order:

1. the 3 a.m. operator problem;
2. creating a bounded investigation;
3. reading raw evidence;
4. understanding the AI boundary;
5. defending the safety boundary;
6. connecting a sandbox design to production systems.

Each module has a knowledge check. Progress is stored only in the current browser.

### 9. Understand Admin

The **Admin** workspace manages generated-ID teams, services, dependencies, runbooks, and scenario templates.

It requires a short-lived `ADMIN` JWT. This is intentional evidence that hiding a frontend button is not authorization. Public visitors can see the authentication boundary but cannot mutate the catalog.

## What is real, synthetic, and deliberately disabled?

| Area | Public deployment |
|---|---|
| Spring services and transaction boundaries | Real |
| PostgreSQL records and Flyway schema | Real |
| Redis rate/suppression state | Real |
| RabbitMQ durable delivery and manual acknowledgement | Real |
| Qwen model inference | Real, CPU hosted |
| pgvector retrieval | Real |
| Guardrail and risk evaluation | Real deterministic Java |
| Audit ledger | Real append-only database history |
| Company, services, failures, telemetry | Deterministic synthetic digital twin |
| Infrastructure mutation | Disabled by dry-run |
| Public arbitrary prompts or commands | Not exposed |
| Public approvals or policy changes | Not exposed |

## Suggested questions for a technical review

1. Why is Redis not the correctness boundary?
2. Where can a crash happen between an external side effect and result recording?
3. Why does human approval re-enter the same gate?
4. How does the system prevent a model from selecting extra evidence or invoking a mutation?
5. Which fields and indexes bound telemetry queries?
6. Why are compensation and rollback recorded as new facts?
7. What would move out of the single Azure VM at production scale?

Answers and design context are in the [System Design Workbook](learning/SYSTEM_DESIGN_WORKBOOK.md), [learning path](learning/README.md), and [chronological project journal](PROJECT_JOURNAL.md).

## If a run appears slow

- Keep the page open; the event stream will report elapsed time.
- CPU inference commonly dominates the runtime. PostgreSQL vector retrieval is typically much faster.
- Do not click Create repeatedly. Each accepted request is intentionally rate-limited.
- A generated UUID means the request was durably accepted.
- If the system reports a safe failure, no infrastructure mutation occurred.

## Shareable links

- Live product: <https://sentinel-mofazzal874.centralindia.cloudapp.azure.com/>
- Project homepage: <https://github.com/Mofazzal874/Sentinel-Autonomous-On-Call-Incident-Response-Agent>
- This demo guide: <https://github.com/Mofazzal874/Sentinel-Autonomous-On-Call-Incident-Response-Agent/blob/main/docs/DEMO_GUIDE.md>
- Engineering journal: <https://github.com/Mofazzal874/Sentinel-Autonomous-On-Call-Incident-Response-Agent/blob/main/docs/PROJECT_JOURNAL.md>

## One-sentence explanation

Sentinel is a Spring-based incident-response control plane where AI investigates and proposes, while deterministic policy, durable idempotency, human approval, and an append-only ledger keep infrastructure authority accountable.
