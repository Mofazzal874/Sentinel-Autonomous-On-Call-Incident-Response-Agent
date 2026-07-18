# ADR 0008: Protected, Low-Cardinality Observability

- Status: accepted
- Date: 2026-07-19

## Context

Sentinel needs metrics for operations and portfolio evidence, but observability endpoints expose process, capacity, and workflow information. Unbounded tags such as incident IDs, fingerprints, arbitrary tool names, or generated model text can also create an expensive number of time series.

## Decision

- Use Spring Boot Actuator and Micrometer's Prometheus registry under Boot's dependency management.
- Expose only `health`, `info`, `metrics`, and `prometheus`; keep them behind the existing stateless JWT security boundary.
- Use fixed low-cardinality tags: severity enum, triage/gate decision enum, fixed success/failure status, four known tool names, and four known model roles. Unknown input collapses to `unknown`.
- Use a timer for triage because timers already include count and duration. Use a distribution summary for bounded evaluator attempts and counters for discrete incident/tool/model/remediation events.
- Do not claim token/cost data until a live provider returns authoritative usage metadata. Scripted correctness tests measure call count, not fabricated tokens.
- Create fixed-name Micrometer observations for triage, classify, gather, propose, and evaluate. Only incident type and bounded attempt number are span dimensions.
- Keep trace export disabled by default. The `otlp` profile enables Spring Boot's OpenTelemetry/OTLP support and takes its collector endpoint and sampling probability from environment variables.

## Consequences

- Prometheus can scrape operational metrics without a host installation, but callers still need authentication.
- Cardinality remains bounded independently of traffic volume and incident count.
- Metrics remain vendor-neutral; an OTLP trace exporter can be enabled later without changing safety policy.
- An in-memory handler can prove parent continuity without installing or contacting a collector.
- A deployment must configure authenticated scraping or isolate the management endpoint on a trusted network.
