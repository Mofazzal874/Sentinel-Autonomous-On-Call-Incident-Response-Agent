# ADR 0005: Bounded Agent Workflow and Audit Transcript

- Status: Accepted
- Date: 2026-07-18

## Context

Sentinel is ready to add model-driven triage, but models are nondeterministic, slow compared with database calls, and capable of inventing unsupported facts. The workflow must remain testable without a model, must not hold database locks during network calls, and must preserve enough evidence to explain every proposal.

## Decision

- Model roles implement narrow Java ports: router, evidence collector, proposal generator, and evaluator. The orchestration policy is ordinary Java rather than hidden in a prompt.
- The first implementation is sequential. Parallel workers remain deferred until ordering, bounds, and failure behavior are proven.
- Refinement is limited to three proposals. The workflow escalates when no proposal passes inside that bound.
- Retrieved runbooks are a deterministic grounding boundary. A proposal citing a runbook outside the retrieved set is rejected before the model evaluator is consulted.
- The model may describe risk, but it does not assign the authoritative risk score or approve execution. Those remain deterministic responsibilities of the later guardrail layer.
- Every run and transcript entry is persisted through Flyway-owned tables. A partial unique index permits at most one `RUNNING` workflow per incident.
- Transcript sequence allocation uses a pessimistic row lock and a short `REQUIRES_NEW` transaction. Model/network calls occur outside persistence transactions.
- Spring AI and pgvector libraries are present, while chat, embedding, and vector-store auto-configuration default to `none`. Model weights are never pulled automatically.
- Flyway owns a 768-dimension runbook vector table and HNSW cosine index. Indexing is explicit and idempotent; semantic queries require similarity of at least `0.60`.
- The audited local targets are Qwen3 4B for chat and `nomic-embed-text` for 768-dimension embeddings. Installation is deferred until a live demo needs it; runtime and model data will be placed on `E:`.
- Router output selects evidence categories, then Java invokes bounded tools with the incident's trusted service/time context. Read methods also expose Spring AI `@Tool` definitions, but the main workflow does not let a model invent core incident identifiers.
- A Redis counter allows at most twelve model calls per incident per hour. Budget exhaustion and adapter/tool failures fail safe by completing the run as failed and escalating the incident.
- Orchestration is disabled by default and requires an explicit `sentinel.agent.enabled=true`; retrieval independently defaults to lexical unless `semantic` is selected.

## Consequences

Benefits:

- Workflow policy and failure paths are deterministic unit-test targets.
- A favorable model critique cannot override missing grounding.
- Attempt bounds cap token consumption and latency per workflow stage.
- The transcript is an explicit audit record rather than unreliable chat memory.
- Database connections and locks are not held while waiting for an LLM.

Costs:

- More adapter code is required between Spring AI and domain ports.
- Text transcript payloads are currently human-inspectable but will need stable structured serialization before external APIs consume them.
- Changing embedding models to a different dimension requires a new migration and complete re-index; vector dimensions are schema, not a casual runtime toggle.

## Rejected alternatives

- Put the entire agent in one prompt: hides routing, retry, grounding, and stopping policy from normal tests.
- Trust the evaluator to decide grounding: lets one nondeterministic model validate another without a deterministic evidence check.
- Persist only the final answer: loses the tool evidence and critique trail needed for audit and regression tests.
- Hold one transaction for the entire workflow: wastes connections and increases lock duration across slow external calls.
- Auto-pull local models on application startup: creates surprise downloads, startup failures, and uncontrolled disk use.
