# Deterministic Guardrails and Safe Execution

This chapter begins with the risk scorer. The scorer is complete and tested; the gate, ledger, execution strategies, approval flow, and compensation are deliberately still open.

## 1. Prerequisite concepts

Review enums and immutable records, the service allowlist, incident fingerprints, database uniqueness, transaction boundaries, method authorization, and the difference between a proposal and permission.

## 2. Plain-language definitions

- **Risk fact:** trusted data used by policy, such as service tier or runbook similarity.
- **Risk breakdown:** the named contribution of each fact plus their checked sum.
- **Guardrail gate:** the only component allowed to decide what may proceed toward execution.
- **Blast radius:** how much of the system could be affected if an action is wrong.
- **Kill switch:** an operator-controlled stop that prevents all execution.
- **Dry-run:** record what would happen without performing the side effect.
- **Action ledger:** append-only evidence of every decision and execution attempt.
- **Compensation:** a new action that counteracts a completed action; it does not erase history.

## 3. Request and decision flow

```text
grounded proposal + trusted fleet/incident facts
                     |
                     v
       DeterministicRiskScorer
                     |
              RiskBreakdown
                     |
                     v
   future single GuardrailGate only
     | refuse / dry-run / approve / execute
                     |
          future append-only ledger
```

The model's `riskNotes` never enter the numeric calculation. Locally, this makes the scorer a small pure policy component. In the overall system, it prevents persuasive generated text from becoming authorization. In an interview: **the agent proposes, deterministic code scores, and one gate enforces.**

## 4. Current code map

- `guardrail/RiskFacts`: validates the five trusted inputs.
- `guardrail/DeterministicRiskScorer`: maps those inputs to fixed weights.
- `guardrail/RiskBreakdown`: verifies that the published total equals its components.
- `DeterministicRiskScorerTest`: covers normal, boundary, capped, and invalid inputs.
- ADR 0006: records why weights are code-owned policy rather than prompt text.

## 5. Worked example

A rollback on a critical service affects three dependents, has retrieval similarity `0.60`, and occurs during peak traffic:

```text
rollback 4 + critical 4 + dependents 3 + low confidence 3 + peak 2 = 16
```

A high-confidence restart on a standard service outside peak traffic is:

```text
restart 1 + standard 1 = 2
```

The future gate will compare such totals with a documented threshold. It will still check the kill switch, allowlist, idempotency, and dry-run policy; a low number alone will never authorize a mutation.

## 6. Failure modes and tradeoffs

- Trusting the proposal's own risk label lets a hallucinated or injected model authorize itself.
- Accepting `NaN`, infinity, negative dependents, or similarity outside 0–1 makes policy behavior ambiguous; validation rejects them.
- Unbounded dependent counts can overflow or make the scale meaningless; the risk contribution is capped at 10.
- A simple additive score is explainable and testable but does not model every operational interaction. Policy evolution must stay versioned and regression-tested.
- Similarity is retrieval confidence, not proof that execution is safe. It is only one conservative input.

## 7. Verification

Run the focused policy tests:

```powershell
$env:JAVA_HOME='E:\DevTools\temurin-25\jdk-25.0.3+9'
$env:GRADLE_USER_HOME='E:\DevCaches\gradle'
.\gradlew.bat test --tests 'io.mofazzal.sentinel.guardrail.DeterministicRiskScorerTest'
```

The focused run passed six tests, including the exact `0.75` confidence boundary and sum-invariant rejection. A forced full regression then passed 68 tests with zero failures, errors, or skips.

## 8. Pen-and-paper exercises

1. Score a standard scale-out affecting two dependents at similarity `0.74` outside peak traffic.
2. Change only similarity to `0.75`. Which component changes, and by how much?
3. Explain why an allowlisted action with score 2 must still stop when the kill switch is engaged.
4. Draw the two database transactions surrounding a future external side effect: committed `IN_PROGRESS`, then committed result.
5. Explain why compensation must be a new ledger row rather than an update that changes `APPLIED` to `UNDONE`.

## 9. What remains

The next safe slice is the decision-only `GuardrailGate` with fake/read-only collaborators and exhaustive ordering tests. It must not invoke a remediation strategy yet. Persistence and execution follow only after that decision boundary is proven.
