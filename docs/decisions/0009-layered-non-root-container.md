# ADR 0009: Layered Non-Root Application Container

- Status: accepted
- Date: 2026-07-19

## Context

Sentinel needs a small, reproducible Java 25 image that can run on a VM or managed container platform. Build tooling and source code do not belong in the runtime image. Platform probes need an unauthenticated status signal, but operational metrics and health details must remain protected.

## Decision

- Build the executable archive with the checked-in Gradle wrapper and the E-backed cache before invoking Docker.
- Use the patch-and-manifest-digest-pinned official `eclipse-temurin:25.0.3_9-jre-noble` runtime for both extraction and execution.
- Extract Spring Boot layers so dependency layers remain reusable when application code changes.
- Run as numeric user/group `10001:10001` and support a read-only root filesystem with a bounded `/tmp` mount.
- Expose only `/actuator/health/liveness` and `/actuator/health/readiness` anonymously, with health details disabled. Keep Prometheus, metrics, and every business route behind JWT/HMAC controls.
- Keep probes in the deployment specification rather than embedding `curl` or another package into the application image.

## Consequences

- The verified local image is about 200 MB and contains no compiler or Gradle installation.
- The same image can run on Azure Container Apps or a Linux VM; platform-specific probes and secrets remain outside it.
- Image rebuilds require `bootJar` first, which makes the artifact boundary explicit.
- Base-image maintenance still requires deliberate patch updates and a new regression/image scan.
