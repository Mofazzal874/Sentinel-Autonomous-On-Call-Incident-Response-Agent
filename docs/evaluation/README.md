# Agent Evaluation Method

Sentinel measures model quality separately from deterministic workflow and safety correctness. Scripted responses prove orchestration contracts; they are never reported as model accuracy.

## Ground truth

`src/test/resources/evaluation/incident-ground-truth.json` contains 12 deliberately small, synthetic scenarios balanced across four incident types and three splits:

- `DEVELOPMENT`: inspect failures and choose changes.
- `VALIDATION`: compare candidate changes without editing expectations.
- `HOLDOUT`: run only at a checkpoint after the change is selected; do not tune against its failures.

Each scenario fixes the expected type, minimum evidence signals, relevant runbook/action or mandatory escalation. A retrieved runbook is relevant only when its exact title matches ground truth. A proposal is hallucinated when its cited runbook was not in retrieval results.

## Scores and gates

- Classification accuracy: exact incident-type match; target at least 80% on validation.
- Required-signal coverage: prediction includes every required evidence source; target at least 90%.
- Retrieval recall@3: expected runbook appears in the first three results; target at least 90% on eligible cases.
- Retrieval ground-truth match: a positive contains its expected runbook and a negative returns no runbooks; report this beside recall so false positives remain visible.
- Outcome accuracy: exact grounded runbook/action, or correct fail-safe escalation; target at least 80%.
- Grounding violations: proposed runbook absent from retrieval; hard gate is zero.
- Latency: report median and p95 per role and end-to-end. Establish hardware/provider baselines before choosing a latency gate.
- Usage/cost: accept provider-reported token counts only; do not infer token cost from prompt length.

The initial corpus is useful for regression mechanics but too small to claim production-level statistical confidence. Expand it with anonymized real incidents, ambiguous negatives, prompt-injection attempts, and changing operational language before production use.

## Iteration loop

1. Run development and record errors by classification, signal choice, retrieval, structure, grounding, and latency.
2. Change one controlled variable: prompt, runbook content, retrieval threshold, model, or context—not all at once.
3. Run validation and compare the same metrics.
4. Reject a change that improves averages while introducing a grounding violation.
5. Run holdout only after selecting the candidate, record the report, then freeze a new baseline.

## Current baseline

The local baseline is recorded in [2026-07-19-qwen3-4b-baseline.md](2026-07-19-qwen3-4b-baseline.md). The frozen holdout routing/RAG run scored 4/4 on classification, required evidence, recall@3, and retrieval ground-truth match. A two-case full-loop sample scored 2/2 outcomes with zero grounding violations. These are engineering smoke results over a tiny synthetic corpus, not production accuracy claims.
