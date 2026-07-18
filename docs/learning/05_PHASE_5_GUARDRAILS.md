# Deterministic Guardrails and Safe Execution

The safety and execution layer is complete. The model can propose a grounded action, but it cannot score, authorize, approve, reserve, or execute it.

## 1. Prerequisites

Review immutable records, enums, database uniqueness, pessimistic/optimistic locking, `REQUIRES_NEW`, JWT roles, method authorization, the service action allowlist, incident fingerprints, and the difference between a proposal and permission.

## 2. Plain-language definitions

- **Risk fact:** trusted data used by policy, such as service tier or runbook similarity.
- **Guardrail gate:** the single component that evaluates every proposed or human-approved mutation.
- **Execution authorization:** an opaque capability the gate issues only for an eligible exact request.
- **Action claim:** mutable correctness state that reserves one fingerprint/action pair.
- **Action ledger:** immutable event history; PostgreSQL rejects updates and deletes.
- **Idempotent strategy:** repeating the same claim does not repeat its effect.
- **Compensation:** a new action/event that counteracts completed work; it never erases history.
- **Uncertain execution:** a stale in-progress action whose side effect may or may not have happened; it is escalated, not retried.

## 3. Data and execution flow

```text
grounded proposal (id + title + action + similarity >= 0.60)
             |
   remediation_request committed with triage completion
             |
             v
        GuardrailGate
 kill -> allowlist -> risk -> durable history -> dry-run -> threshold/approval
             |
    decision + opaque authorization (eligible outcomes only)
             |
    IN_PROGRESS claim + event committed (transaction 1)
             |
    package-private idempotent strategy (side-effect transaction)
             |
    APPLIED/FAILED event committed (transaction 2)
             |
 failed completed work -> COMPENSATION_* events + reversed effect
```

Human approval returns to the top of the gate. It does not bypass a newly engaged kill switch, a changed allowlist, an existing claim, dry-run, or risk calculation.

## 4. Code map

- `guardrail/DeterministicRiskScorer`, `RiskFacts`, `RiskBreakdown`: model-free safety policy.
- `guardrail/GuardrailGate`: ordered policy checks and execution authorization issuance.
- `DatabaseBackedKillSwitch`: Redis engaged shortcut plus authoritative PostgreSQL read; database failure closes the gate.
- `PersistedServiceActionAllowlist`, `PersistentActionHistory`, `ConfiguredDryRunPolicy`: deterministic gate inputs.
- `RemediationRequestStore`: grounded proposal persistence, risk/review data, and guarded state transitions.
- `RemediationDecisionCoordinator`: the only application route from proposal/approval to the executor.
- `ledger/ActionReservationService`, `ActionResultRecorder`: separate committed transaction boundaries.
- `action_claim`: durable uniqueness and recovery state.
- `action_ledger`: trigger-protected immutable events.
- `execution/RemediationExecutor`: validates gate authorization, reserves, invokes an internal strategy, records result, and compensates failure.
- `RemediationApprovalService` and controller: `SRE_APPROVER` review/approve/reject boundary.
- `RemediationRecoveryService`: retries pending decisions, escalates uncertain executions, and never repeats a possible effect.
- V5 migration: controls, request, claim, ledger, simulated state/effects, constraints, indexes, and append-only trigger.

## 5. Worked examples

### Automatic low risk

A high-confidence restart on a standard service outside peak traffic scores `1 + 1 = 2`. With kill switch off, action allowlisted, no prior claim, and dry-run disabled, the gate issues `AUTO_EXECUTE`. The database records `DECIDED → IN_PROGRESS → APPLIED`; the incident resolves.

### Human-controlled high risk

A critical rollback with similarity `0.60` scores at least `4 + 4 + 3 = 11`. It becomes `AWAITING_APPROVAL`. The approver reads the grounded runbook, steps, advisory model notes, authoritative score/breakdown, and expiry. Approval is recorded, then the gate rechecks every policy before issuing `APPROVED_EXECUTE`.

### Crash window

If the process crashes after the simulated effect but before `APPLIED`, the claim remains `IN_PROGRESS`. Recovery escalates after five minutes and the unique claim prevents another execution. This is not mathematically exactly-once delivery; it is effectively-once mutation through durable reservation plus idempotent strategy behavior.

## 6. Failure modes and tradeoffs

- Redis is not trusted for correctness. It can accelerate an engaged kill switch, but PostgreSQL is always consulted when Redis does not say engaged.
- A low risk score is not authorization; kill switch, allowlist, idempotency, and dry-run still apply.
- Similarity under `0.60` or mismatched runbook identity/title/action is rejected before a remediation request exists.
- The claim remains permanent after failure/uncertainty. This may require manual reconciliation, but it prevents a dangerous automatic repeat.
- PostgreSQL cannot atomically commit a real external side effect. Separate transactions make the unavoidable gap visible and recoverable.
- Simulated counters are educational stand-ins for infrastructure APIs. The gate, claim, transactions, ledger, authorization, idempotency, and compensation machinery are the production-relevant design.
- A restart is not truly reversible in a real platform. Production allowlists must encode each provider strategy's actual reversibility and blast radius rather than assuming the simulation's inverse counter is sufficient.

## 7. Verification evidence

Focused and integration tests prove:

- all gate short-circuit branches and inclusive risk threshold;
- kill-switch exception fail-closed behavior;
- Redis/DB-backed kill switch and persisted allowlist;
- default dry-run creates no action claim/effect;
- concurrent duplicate execution creates one claim and one effect;
- executor and reservation/result services reject missing gate authorization;
- PostgreSQL rejects ledger update/delete;
- `IN_PROGRESS` exists before a strategy effect and result is separate;
- induced later-step failure restores simulated state and appends compensation facts;
- low-risk auto-execution, high-risk approval, self-approval rejection, allowlist refusal, timeout, kill switch, pending recovery, and stale-execution escalation;
- approver review exposes grounded and authoritative fields;
- sub-threshold or mismatched grounding cannot be persisted.

The first full uncached run after the main implementation passed 88 tests in 2 minutes 20 seconds. After adding the grounding-boundary regression, the final uncached checkpoint passed 89 tests with zero failures, errors, or skips in 2 minutes 27 seconds.

## 8. Defend This

1. **Why deterministic risk instead of the LLM?** A model can hallucinate or be prompt-injected. Fixed Java policy is reproducible, inspectable, boundary-tested, and outside the actor requesting authority.
2. **How do you approach exactly-once execution across Redis eviction and crashes?** PostgreSQL permanently reserves `(fingerprint, action_type)` before the side effect; each strategy also keys its effect by claim. Redis is irrelevant to correctness. A crash may leave an uncertain result, so Sentinel blocks/reconciles rather than falsely claiming atomic exactly-once delivery.
3. **Walk the compensation scenario.** The executor commits a claim, step one changes simulated capacity, and a later step fails. It records `FAILED`, appends `COMPENSATION_STARTED`, reverses completed work, then appends `COMPENSATED`. Compensation runs in reverse responsibility order and never rewrites the original facts.
4. **What can a compromised agent do?** It can propose and read bounded evidence. It cannot approve, administer the kill switch, construct execution authorization, access package-private strategies, bypass the allowlist, repeat a claimed action, or expose mutation as an LLM tool.
5. **Why does approval still go through the gate?** State may change while a human reviews. The kill switch might engage, an allowlist might shrink, or another action might complete. Approval records human intent; the gate still owns current eligibility.
6. **Why append-only events instead of one changing row?** Immutable events retain every decision, failure, approval, and compensation for audit and metrics. Mutable claim state serves coordination separately. A database trigger makes the historical promise enforceable.
7. **Where are transaction boundaries?** Transaction 1 commits `IN_PROGRESS` plus its event. No outer transaction spans the strategy. The strategy owns its short effect transaction. Transaction 2 commits `APPLIED` or `FAILED`. This prevents holding locks/connections across external work and leaves a detectable crash marker.

## 9. Pen-and-paper exercises

1. Draw the gate order and explain what information is unavailable after each short circuit.
2. Draw a timeline for a crash before claim commit, after claim commit, after effect commit, and after result commit.
3. Explain why `action_claim` may update while `action_ledger` must not.
4. Score a standard scale-out affecting two dependents at similarity `0.74`, then at `0.75`.
5. Draw a three-step saga where step two fails and list compensation in reverse order.
6. Explain why a human approval is an input to policy rather than a bypass around policy.

## 10. Verification commands

```powershell
$env:JAVA_HOME='E:\DevTools\temurin-25\jdk-25.0.3+9'
$env:GRADLE_USER_HOME='E:\DevCaches\gradle'
.\gradlew.bat test --rerun-tasks
```
