# Operability, Evaluation, Packaging, and Deployment

This chapter includes protected metrics, nested workflow observations, opt-in trace export, versioned dashboard/alert assets, a live local-model baseline, and verified non-root packaging. Cloud provisioning remains open and requires explicit approval.

## 1. Prerequisites

Review dependency injection, application configuration, JWT authorization, timers versus counters, bounded tags, and the distinction among logs, metrics, and traces.

## 2. Plain-language definitions

- **Actuator:** Spring Boot's production endpoints for health and operational inspection.
- **Meter:** a named measurement such as a counter, timer, gauge, or distribution summary.
- **Tag/dimension:** a bounded label used to slice one metric, such as `decision=AUTO_EXECUTE`.
- **Cardinality:** the number of distinct time series created by names plus tag-value combinations.
- **Prometheus scrape:** Prometheus pulls the text-format measurements from an HTTP endpoint.
- **Observation:** Micrometer's lifecycle abstraction that can feed metrics and traces.
- **Trace/span:** a trace follows one request; spans represent timed stages inside it.
- **OTLP:** the vendor-neutral protocol used to send traces to a compatible collector or backend.

## 3. Current flow

```text
incident / agent / gate events
            |
      SentinelMetrics
            |
       MeterRegistry
            |
 authenticated /actuator/prometheus
            |
 future Prometheus/dashboard

triage observation
   +-- classify
   +-- gather
   +-- propose
   `-- evaluate
            |
    optional `otlp` profile -> collector
```

Locally, `SentinelMetrics` records cheap measurements without changing decisions. In system design, the measurements reveal throughput, latency, repeated evaluator loops, model-call pressure, tool selection, and remediation outcomes. In an interview, emphasize that observability watches safety behavior but never grants safety authority.

## 4. Code map

- `build.gradle.kts`: Actuator and Prometheus registry dependencies managed by Spring Boot.
- `application.yml`: allowlisted management endpoints and application tag.
- `observability/SentinelMetrics`: bounded metric names and tag normalization.
- `observability/SentinelObservations`: fixed-name observation wrapper with paired, low-cardinality dimensions.
- `AgentTriageCoordinator`: end-to-end triage timer and attempt distribution.
- `DeterministicEvidenceCollector`: records only the four known tool categories.
- `RedisModelCallBudget`: records bounded model roles alongside its capacity enforcement.
- `IncidentCreationService`: increments only for a successful durable insert.
- `RemediationDecisionCoordinator`: records deterministic decision success/failure.
- ADR 0008: endpoint security, cardinality, and authoritative token/cost policy.
- `application-otlp.yml`: opt-in OTLP endpoint and sample rate; the default profile disables trace export.
- `observability/grafana-dashboard.json` and `prometheus-alerts.yml`: versioned operations views and human-response rules.

## 5. Example and failure modes

One successful automatic restart contributes:

```text
sentinel_incidents_created_total{severity="SEV2"} +1
sentinel_agent_tool_calls_total{tool="runbook"} +1
sentinel_triage_duration_seconds{decision="PROPOSED"} count/time
sentinel_remediation_outcome_total{decision="AUTO_EXECUTE",status="SUCCESS"} +1
```

Never tag metrics with incident UUID, fingerprint, service supplied by arbitrary users, exception message, prompt, or generated text. Those values create high cardinality and can leak sensitive information. Use logs/traces with controlled retention for per-incident detail.

A public Prometheus endpoint leaks internal runtime data. Sentinel leaves the endpoint behind JWT authentication. A future deployment must either configure authenticated scraping or put the management interface on a private network.

The remediation-failure rule pages immediately because an external effect may be partially complete and the ledger may require human review. Escalation surges and missing incidents create tickets rather than immediate pages because they usually indicate degraded assistance or intake, not proof of an unsafe mutation. The missing-incident rule should only be enabled in an environment where steady alerts are expected.

## 6. Verification

Focused tests prove fixed/unknown tag normalization, timers/counters/distributions, model/tool call wiring, and an authenticated Prometheus scrape containing JVM metrics plus the `application="sentinel"` tag. A separate in-memory observation handler proves that a stage sees the triage observation as its parent. No test starts an exporter or needs a collector.

Official references used for this slice:

- [Spring Boot metrics and Prometheus endpoint](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Spring Boot observability and Micrometer Observation](https://docs.spring.io/spring-boot/reference/actuator/observability.html)
- [Micrometer meter concepts](https://docs.micrometer.io/micrometer/reference/concepts/meters.html)
- [Spring Boot tracing and OpenTelemetry/OTLP](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)

```powershell
$env:JAVA_HOME='E:\DevTools\temurin-25\jdk-25.0.3+9'
$env:GRADLE_USER_HOME='E:\DevCaches\gradle'
.\gradlew.bat test --tests 'io.mofazzal.sentinel.observability.SentinelMetricsTest'
```

## 7. Pen-and-paper exercises

1. Explain why a timer does not need a second counter for the same operation.
2. Calculate the maximum remediation time-series count from seven decisions and two statuses.
3. Compare a metric tag containing `serviceTier` with one containing `incidentId`; which is bounded?
4. Draw how one future Observation can feed both a timer and a trace span.
5. List which management endpoints could be public, authenticated, or network-private in a production design and defend each choice.

## 8. Evaluation lesson

The fixed corpus separates development, validation, and holdout scenarios and scores classification, required evidence, retrieval, grounded outcome, and hallucination count. The local Qwen3 4B baseline proved why these layers must be separated: JSON transport failed first, classification failed next, and evidence selection failed even when classification was correct. Each required a different repair.

On paper, classify this example and list the minimum evidence you would request: “CPU is saturated, there was no recent deployment, dependencies are healthy, and latency keeps rising.” Then explain why seeing the words “deployment” in the negative statement must not turn it into a bad-deploy incident.

The final design gives the model classification and rationale only. Java chooses bounded evidence tools. Use probabilistic judgment where ambiguity exists, then narrow its output through deterministic policy before it touches operational capabilities. See the versioned baseline under `docs/evaluation/`.

## 9. Packaging flow

```text
Gradle wrapper -> reproducible executable JAR -> Spring Boot layer extraction
                                                |
                               digest-pinned Java 25 runtime
                                                |
                            numeric non-root, read-only container
                                                |
                         liveness/readiness platform probes
```

Locally, the image carries the application and runtime without carrying source code or Gradle. In system design, immutable images make rollback and environment comparison tractable: the same digest is promoted while secrets and endpoints come from each environment. In an interview, explain why the image is non-root, why its base is pinned by digest, and why readiness differs from liveness.

The first smoke test ran against the real local dependency network, used a read-only root filesystem, reached `UP`, and still rejected anonymous Prometheus access. The container was temporary and removed; the image remains because it is the next deployment input.

Pen-and-paper exercise: draw what happens when PostgreSQL is unavailable during startup. Flyway cannot complete, readiness never becomes healthy, and the platform must not send traffic. Explain why restarting forever is not the same as recovery and where an operator would inspect the failure.

## 10. Complete asynchronous handoff

```text
signed alert -> Redis first-seen -> RabbitMQ command
                                      |
                         commit unique incident
                                      |
                     optional enabled agent dispatch
                                      |
             classify -> bounded tools -> retrieve -> evaluate
                                      |
                    deterministic GuardrailGate
                                      |
                     dry-run/approval/execution ledger
                                      |
                         RabbitMQ acknowledgement
```

The incident commit happens before model work, so no database transaction stays open during a slow network call. The message acknowledgement happens after the durable outcome, so a process crash causes redelivery rather than pretending the work finished. Redelivery checks the incident state: terminal work is safe to acknowledge, concurrent triage receives a bounded retry, and stale uncertain triage eventually escalates for manual review.

In an interview, do not call this exactly-once processing. It is at-least-once delivery plus idempotent persistence, explicit states, and fail-closed recovery. Pen-and-paper exercise: mark every crash point between publish, incident commit, triage start, proposal commit, gate decision, and acknowledgement; state what redelivery observes at each point.

## 11. Defend This

1. **Why are model tests separate from normal tests?** Live models are slow, variable, and environment-dependent. Deterministic tests protect contracts; explicit evaluation measures quality.
2. **Why is evidence selection Java, not prompt text?** A small model repeatedly ignored the requested signal set. A deterministic map bounds read scope and is exhaustively testable.
3. **Why report recall and negative matching?** Recall can be perfect while irrelevant runbooks are returned for every negative. Both false negatives and false positives matter.
4. **Why is dry-run still enabled after good scores?** A tiny synthetic benchmark cannot authorize infrastructure mutation. The independent gate remains authoritative.
5. **Why not deploy directly to AKS?** The deadline benefits from the tested Compose topology; AKS adds cluster, ingress, identity, and storage work without validating the product better.
6. **Why separate liveness from readiness?** Liveness decides restart; readiness decides traffic. A process can be alive while a migration or dependency is unavailable.
7. **What is the current performance bottleneck?** CPU model generation: the grounded loop took about 100 seconds while semantic retrieval took about 0.16 seconds.

## 12. Stable identity and replaceable software

The résumé URL and the application image solve different problems. A static public IP plus DNS name answers “where is the service?” An immutable commit-SHA image answers “which exact software is running?” GitHub Actions can replace the second without replacing the first.

Locally, Caddy is unnecessary because loopback already bounds access. In Azure, Caddy is the only public container and routes to Sentinel over a private Compose network. Databases and model servers never receive public ports. A user-owned DNS name enables automatic HTTPS; the Azure DNS label is the stable no-domain fallback.

Failure modes to remember:

- A moving `latest` tag makes rollback and incident reconstruction ambiguous; deploy SHA tags.
- Recreating a dynamic IP can silently break DNS; retain a Standard static Public IP resource.
- A budget sends notifications but does not stop a VM; monitor and deallocate deliberately.
- CI holding an unverified SSH host key can connect to an impostor; pin the host identity after a trusted first connection.
- An application can start before runbook embeddings exist; the deployment enables an idempotent startup index and fails before readiness when the embedding contract is unavailable.

Pen-and-paper exercise: draw DNS, static IP, Caddy, Sentinel, and the four private dependencies. Now draw an update from Git commit to GHCR SHA tag to VM pull. Circle the one component whose identity must never change during an ordinary release.
