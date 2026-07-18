# Operability, Evaluation, Packaging, and Deployment

This chapter now includes protected metrics, nested workflow observations, opt-in trace export, and versioned dashboard/alert assets. Offline evaluation, packaging, and deployment are still open. No cloud resource has been created.

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

## 8. Next dependency-ordered step

The fixed corpus now separates development, validation, and holdout scenarios and scores classification, required evidence, retrieval, grounded outcome, and hallucination count. The live baseline remains deliberately marked `NOT_RUN` until Qwen3 4B and `nomic-embed-text` are enabled on `E:` or a paid provider is explicitly approved.

On paper, classify this example and list the minimum evidence you would request: “CPU is saturated, there was no recent deployment, dependencies are healthy, and latency keeps rising.” Then explain why seeing the words “deployment” in the negative statement must not turn it into a bad-deploy incident.

Next, run the model baseline and preserve raw per-scenario mismatches. Change one variable at a time, validate, and touch holdout only after choosing the candidate. Packaging and Azure provisioning remain separate and require the user handoff.
