# Phase 4: Bounded Agent Orchestration and Audit Memory

This lesson grows with the implementation. The workflow skeleton and transcript are complete; semantic retrieval and live-model adapters are still open.

## 1. Prerequisites

Review interfaces and dependency injection, service-layer transactions, the four bounded read tools, incident state transitions, Redis capacity controls, and the difference between authentication and authorization.

## 2. Plain-language model

An LLM is a probabilistic text engine, not a transaction manager or safety authority. Sentinel gives it narrow thinking jobs while ordinary Java owns the workflow:

```text
incident -> router -> bounded evidence -> proposal -> evaluator
                         |                    ^          |
                         v                    | critique |
                   retrieved runbook         +----------+
                              maximum three proposals
```

- The **router** labels the incident and chooses relevant evidence categories.
- The **evidence collector** obtains bounded DTOs from already-tested tools.
- The **generator** drafts a proposal grounded in retrieved evidence.
- The **evaluator** critiques the draft against explicit criteria.
- The **orchestrator** owns order, stopping conditions, and escalation.

## 3. Local code map

- `agent/domain`: immutable request, classification, evidence, proposal, evaluation, and outcome types.
- `agent/application/TriageWorkflow`: sequential control flow and the three-attempt bound.
- `agent/application/*` ports: model/provider-independent role contracts.
- `agent/persistence`: durable run and transcript entities plus ordered short writes.
- `V3__agent_run_transcript.sql`: constraints, indexes, and one-running-workflow uniqueness.

Spring AI is an adapter dependency, not the owner of business policy. Chat, embedding, and vector-store integrations default to disabled, so normal application work and tests do not require Ollama.

## 4. Transaction and concurrency design

Never wrap an LLM network call in `@Transactional`. A model response may take seconds; holding a connection and row locks that long reduces capacity and increases deadlock/contention risk.

Sentinel instead uses short boundaries:

1. Lock the incident, move it to `TRIAGING`, and create an `agent_run`.
2. Commit.
3. Call a model role outside a database transaction.
4. Persist each transcript fact in a short independent transaction.
5. Lock the run, record its terminal outcome, and transition the incident.

The partial unique index on a running incident is the durable concurrency backstop. The transcript locks the run row while allocating the next sequence number, so concurrent writers cannot claim the same position.

## 5. Grounding and safety separation

A proposal is grounded only when its cited runbook is present in the retrieved evidence set. The Java workflow checks this before calling the evaluator. If retrieval is empty, it escalates immediately. If a generator repeatedly cites an invented runbook, it stops after three attempts and escalates.

The model's `riskNotes` are advisory explanation. A future deterministic Java risk scorer will own the numeric score and a single guardrail gate will own execution eligibility. This separation prevents persuasive model text from becoming authorization.

## 6. Concrete example

For “payments-api error rate rose after deployment”:

1. Router returns `BAD_DEPLOY` and requests deployments, metrics, and runbooks.
2. Workers obtain the latest deploy, bounded error-rate window, and rollback runbook.
3. Generator proposes the retrieved rollback procedure.
4. Evaluator rejects an unsafe first draft that skips correlation.
5. Generator incorporates that critique; the second proposal passes.
6. Sentinel stores each stage and returns a proposal. It executes nothing.

If the generator cites “Invented emergency runbook,” Java rejects it even when the evaluator says it passes.

## 7. Failure modes and tradeoffs

- Missing runbook: escalate; never improvise remediation.
- Malformed structured output: adapter validation will retry only within the call budget, then escalate.
- Slow/unavailable model: record failure and escalate without holding a database transaction.
- Duplicate consumer delivery: incident locking plus the partial unique index prevent two active runs.
- Runaway critique loop: hard maximum of three proposals.
- Huge transcript/tool result: existing tools bound evidence; transcript entries have a 32,000-character ceiling.

Sequential workers cost latency but are easier to reason about. Parallelism can be introduced only after correct aggregation, per-worker budgets, and transcript ordering are proven.

## 8. Verification

```powershell
$env:JAVA_HOME='E:\DevTools\temurin-25\jdk-25.0.3+9'
$env:GRADLE_USER_HOME='E:\DevCaches\gradle'
.\gradlew.bat test --tests io.mofazzal.sentinel.agent.application.TriageWorkflowTest
.\gradlew.bat clean test
```

Current evidence: 54 tests pass. Unit tests prove refinement, empty-retrieval escalation, invented-runbook rejection, and the three-attempt cap. PostgreSQL evidence proves V3 migration, ordered transcript writes, and incident/run state transitions.

## 9. Pen-and-paper exercises

1. Draw the transaction boundaries and circle every network call. No network call should sit inside a transaction.
2. Write the exact transcript sequence for a proposal that fails twice and passes on attempt three.
3. Explain why “the evaluator approved it” is not proof that a runbook was retrieved.
4. Calculate the maximum number of generator and evaluator calls when the attempt limit is three.
5. Describe the race when two consumers begin the same incident and name both protections.

## 10. Interview defense checkpoint

The current implementation can defend structured ports, sequential orchestration, bounded evaluation, deterministic grounding, and transcript transaction design. The complete seven-question gate remains open until Spring AI tool calling, pgvector similarity retrieval, call-rate limiting, and both end-to-end scenarios pass.
