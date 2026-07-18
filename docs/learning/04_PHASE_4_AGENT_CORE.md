# Phase 4: Bounded Agent Orchestration and Audit Memory

The grounded proposal workflow is complete. A live Ollama download is intentionally deferred because deterministic and scripted-model tests prove correctness without consuming several gigabytes or coupling tests to probabilistic output.

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
- `agent/retrieval` and `V4__runbook_embeddings.sql`: explicit indexing, 768-dimension vectors, HNSW cosine search, and the `0.60` grounding threshold.
- `agent/model`: Spring AI structured-output adapters for router, generator, and evaluator.
- `RedisModelCallBudget`: atomic per-incident model-call ceiling.

Spring AI is an adapter dependency, not the owner of business policy. Chat, embedding, and vector-store integrations default to disabled, so normal application work and tests do not require Ollama.

The selected later live-demo models are Qwen3 4B (about 2.5 GB) and `nomic-embed-text` (about 274 MB, 768 dimensions). The machine has 16 GB RAM and integrated graphics, so this smaller pair is more appropriate than an 8B+ chat model. Ollama remains uninstalled; automatic pulling is disabled and future binaries/models must use `E:`.

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

## 8. How semantic retrieval works

Indexing converts each runbook's title, symptoms, and procedure into one 768-number embedding. PostgreSQL stores that vector beside the runbook ID. A query is embedded with the same model, then pgvector calculates cosine distance:

```text
similarity = 1 - cosine_distance(query_vector, document_vector)
```

Only hits at or above `0.60` are returned. HNSW is a graph index: it uses more memory and slower index construction than IVFFlat, but gives a strong speed/recall tradeoff and does not require a training step. Flyway creates the table and index because schema changes must be reviewable and reproducible; Spring auto-initialization is disabled.

Embedding calls run before the short read transaction that performs vector SQL. This prevents a database connection from being held while waiting for a local or remote model.

Embedding dimension is a database contract. `nomic-embed-text` produces 768 values, so `VECTOR(768)` rejects a mismatched model instead of silently storing incomparable data.

## 9. How tool calling works

1. Spring sends tool names, descriptions, and JSON input schemas with the prompt.
2. The model may return a tool-call request instead of final prose.
3. `ToolCallingManager` resolves the callback and invokes the annotated Java method.
4. The Java result becomes a `ToolResponseMessage` in conversation history.
5. The updated history returns to the model, which continues.

The test suite proves this exact two-call loop. Sentinel's primary workflow is stricter: structured router output selects evidence categories, while Java supplies the trusted service and incident time to the bounded tools. This prevents a hallucinated service argument from replacing the incident's real identity. The same four read methods remain valid Spring AI tools; no mutation is exposed.

## 10. Verification

```powershell
$env:JAVA_HOME='E:\DevTools\temurin-25\jdk-25.0.3+9'
$env:GRADLE_USER_HOME='E:\DevCaches\gradle'
.\gradlew.bat test --tests io.mofazzal.sentinel.agent.application.TriageWorkflowTest
.\gradlew.bat clean test
```

Current evidence: 62 tests pass. Unit/scripted-model tests prove structured conversion, tool callback schemas, the complete tool-call loop, refinement, empty-retrieval escalation, invented-runbook rejection, and the three-attempt cap. Redis proves the atomic call budget. PostgreSQL/pgvector proves idempotent indexing, threshold retrieval, grounded/ungrounded end-to-end outcomes, ordered transcripts, and concurrent run exclusion.

## 11. Pen-and-paper exercises

1. Draw the transaction boundaries and circle every network call. No network call should sit inside a transaction.
2. Write the exact transcript sequence for a proposal that fails twice and passes on attempt three.
3. Explain why “the evaluator approved it” is not proof that a runbook was retrieved.
4. Calculate the maximum number of generator and evaluator calls when the attempt limit is three.
5. Describe the race when two consumers begin the same incident and name both protections.

## 12. Defend This — completed answers

### 1. Why router, orchestrator–workers, and evaluator–optimizer?

The router cheaply narrows the incident and evidence categories. The orchestrator owns deterministic order, bounds, and tool inputs. Workers gather specialized evidence. The evaluator critiques a proposal against explicit criteria. One giant prompt would mix classification, retrieval, and refinement into an opaque operation that is harder to test, budget, and replay.

### 2. How does tool calling work end to end?

The model emits a named call with JSON arguments; Spring resolves a registered `ToolCallback`, invokes the annotated Java method, appends its result as a tool-response message, and asks the model to continue. `SpringAiToolCallingLoopTest` proves that sequence. Sentinel exposes only the four bounded read operations; the main path supplies trusted incident identifiers through Java.

### 3. How is an infinite or expensive evaluator loop prevented?

Java caps proposals at three. Every model role first acquires an atomic Redis budget; the total is twelve calls per incident per hour. Every attempt is recorded. Passing needs explicit evaluation plus deterministic grounding. Exhaustion or failure escalates.

### 4. What prevents hallucinated remediation?

Semantic search returns only runbooks with similarity at least `0.60`. Empty retrieval escalates before generation. Java then checks that the proposal's exact runbook title belongs to the retrieved set, even if the model evaluator approves it. Nothing here executes infrastructure.

### 5. Why structured output instead of parsing strings?

`.entity()` converts model JSON into validated Java records with enums and required fields. Schema mismatch fails immediately. Free-text parsing would depend on formatting, punctuation, or brittle regular expressions and could silently reinterpret an invalid decision.

### 6. HNSW versus IVFFlat, and why is schema initialization off?

HNSW provides strong query speed/recall and can be built before data, fitting this small corpus; it costs more memory/build work. IVFFlat is cheaper to build but needs representative training data and usually has a weaker speed/recall tradeoff. Flyway owns `VECTOR(768)` and the HNSW index so every environment applies the same reviewed schema.

### 7. What if the model invents a service name?

The primary workflow does not accept a model-selected service name: the service comes from the persisted incident and Java passes it to tools. Direct tool calls still validate format and existence; an invalid call becomes a controlled failure and the coordinator records failure/escalates. No wrong service is silently queried or mutated.

Phase 4 learning gate: **complete**.
