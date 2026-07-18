# Qwen3 4B Local Baseline — 2026-07-19

## Environment

- Chat: `qwen3:4b`, Ollama 0.32.1, thinking disabled, temperature 0.1, maximum 256 output tokens.
- Embeddings: `nomic-embed-text`, 768 dimensions.
- Hardware: local Windows PC, CPU execution, 16 GB system RAM.
- Storage: Ollama under `E:\DevTools\Ollama`; model blobs under `E:\DevModels\ollama` (2.58 GB measured). Only tiny identity/configuration files remain in the Windows user profile on `C:`.
- Isolation: PostgreSQL/pgvector and Redis were disposable Testcontainers instances. Generated JSON stayed under ignored `build/reports/evaluation/`.

## Iterations

1. The first four development calls failed parsing because Spring AI described a JSON schema but sent Ollama `format=null`. Result: zero valid predictions; calls took 38–51 seconds.
2. Native Ollama JSON mode fixed the transport. The unchanged development split then scored 75% classification, 0% required-signal coverage, and 0 recall because the model omitted `RUNBOOKS`.
3. Explicit class boundaries fixed an ambiguous false bad-deploy classification, but the 4B model still ignored evidence-selection instructions.
4. Evidence selection moved to deterministic Java. The model now classifies only; Java assigns a fixed, bounded read set. Development reached 4/4 classification, 100% signal coverage, and 100% recall@3.
5. Validation exposed a boundary bug when an empty model signal list failed before normalization. The router DTO was reduced to `{type, rationale}` so model-owned signals no longer exist.
6. The scorer gained a retrieval ground-truth-match metric so irrelevant results on negatives cannot hide behind recall.
7. The frozen candidate ran once on holdout and was not tuned afterward.

## Results

| Run | Cases | Classification | Signal coverage | Recall@3 | Retrieval truth match | Outcome | Grounding violations |
|---|---:|---:|---:|---:|---:|---:|---:|
| Development, repaired routing/RAG | 4 | 100% | 100% | 100% | not recorded by earlier scorer | not run | not run |
| Validation, before DTO repair | 4 | 75% | 75% | 100% | not recorded | not run | not run |
| Validation failing case, after repair | 1 | 100% | 100% | 100% | 0% (irrelevant runbooks returned) | not run | not run |
| Holdout, frozen candidate | 4 | 100% | 100% | 100% | 100% | not run | not run |
| Full loop, grounded + negative development cases | 2 | 100% | 100% | 100% | 100% | 100% | 0 |

The one-case validation rerun deliberately does not rewrite the earlier four-case result. The holdout is the clean final routing/RAG checkpoint, but 12 synthetic scenarios are far too few for a production-quality claim.

## Performance

- Holdout classification: 14.5–16.0 seconds per case.
- Holdout semantic retrieval: 155–189 milliseconds per case.
- Full grounded rollback: 100.5 seconds — classification 17.4s, generation 41.6s, evaluation 41.3s, retrieval 157ms.
- Full no-runbook escalation: 14.8 seconds; generation and evaluation were skipped.
- Cold embedding smoke: about 1.18 seconds.

The dominant local cost is CPU model generation, not PostgreSQL vector search. This setup is useful for learning and a slow demo, not a low-latency on-call service.

## Honest limitations and next ground truth

- Expand to hundreds of anonymized incidents with independently reviewed labels.
- Add adversarial negatives, negated deployment language, prompt injection, simultaneous causes, and novel services.
- Record provider-reported input/output tokens once the adapter exposes usage; never estimate cost from text length.
- Measure repeat variance and calculate median/p95 over a meaningful sample.
- Calibrate retrieval thresholds on a larger corpus. One validation UNKNOWN retrieved irrelevant runbooks above 0.60; the full safety path must continue to escalate ungrounded proposals.

## Reproduction

Normal tests never invoke a model. The live task must be requested explicitly:

```powershell
$env:SENTINEL_EVAL_SPLIT='HOLDOUT'
$env:SENTINEL_EVAL_MODE='ROUTING_RETRIEVAL'
.\gradlew.bat liveAgentEvaluation --no-daemon
```

For a bounded full-loop sample, set `SENTINEL_EVAL_MODE=FULL`, select comma-separated IDs through `SENTINEL_EVAL_SCENARIOS`, and keep `SENTINEL_EVAL_MAX_ATTEMPTS=1` while profiling locally.
