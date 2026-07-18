# ADR 0006: Deterministic Risk Before the Single Gate

- Status: accepted
- Date: 2026-07-19

## Context

The model produces a remediation proposal and may describe risk in prose, but model output is probabilistic and can be mistaken or manipulated. Execution needs a stable safety input that can be reproduced from trusted system facts and inspected by an approver.

## Decision

Compute authoritative risk in plain Java from five validated facts: action type, persisted service tier, affected-dependent count, retrieved-runbook similarity, and whether the request falls in a peak-traffic window. Return every component plus the verified sum as `RiskBreakdown`.

Use the private plan's relative weights. Restart, scale, cache clear, and rollback contribute 1, 2, 3, and 4. Critical and standard tiers contribute 4 and 1. Each affected dependent contributes one point, capped at 10. Similarity below `0.75` contributes 3, and a peak window contributes 2.

This scorer makes no execution decision. A later `GuardrailGate` will combine the breakdown with kill-switch, allowlist, idempotency, approval, and dry-run policy. No executor may call a strategy without passing that gate.

## Consequences

- The same facts always produce the same score, so boundary behavior is exhaustively testable without an LLM.
- The breakdown explains a decision instead of hiding policy in a single number.
- Invalid similarity or blast-radius facts fail before policy evaluation.
- Capping the blast-radius contribution keeps the scale bounded while still making unknown or broad impact more conservative.
- Weight or threshold changes are policy changes and require tests plus an ADR update; they are not prompt changes.
