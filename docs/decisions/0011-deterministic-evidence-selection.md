# ADR 0011: Model Classification with Deterministic Evidence Selection

- Status: accepted
- Date: 2026-07-19

## Context

The local Qwen3 4B router classified incidents well after its decision boundaries were clarified, but repeatedly omitted required evidence sources. It also returned an empty signal list once, causing domain validation to fail before normalization. Allowing model output to choose which operational tools run made reliability depend on instruction following.

## Decision

The model router returns only an incident type and rationale. `EvidenceSelectionPolicy` maps that type to a fixed, bounded set of read-only signals. Ollama adapters request native JSON mode and activate only when the corresponding model property is `ollama`.

Semantic retrieval activates from the explicit retrieval-mode property. Constructor injection requires an embedding gateway, so semantic configuration without either a real provider or deterministic test gateway fails startup.

## Consequences

- Evidence collection is predictable, testable, and independent of small-model instruction-following variance.
- The model retains classification judgment but cannot expand tool scope.
- Some unnecessary reads may occur because policy is class-wide; every read remains typed, bounded, and side-effect free.
- Adding an incident type requires an explicit Java policy update and regression test.
