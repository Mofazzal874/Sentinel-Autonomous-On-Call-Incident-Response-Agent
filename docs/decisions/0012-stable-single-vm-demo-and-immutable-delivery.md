# ADR 0012: Stable single-VM demo and immutable delivery

## Status

Accepted for the portfolio demo; it is not the target high-availability production topology.

## Context

The demo needs PostgreSQL/pgvector, Redis, RabbitMQ, two Ollama models, and Sentinel. The user has a time-limited Azure for Students credit, wants one durable résumé URL, and needs changes deployed without replacing that URL. The application must remain dry-run and secrets must not enter Git or container layers.

## Decision

- Use one non-zonal Central India `Standard_B4as_v2` Ubuntu 24.04 VM with a 64 GB Standard SSD.
- Give its separate Standard static public-IP resource a DNS label. Application updates never replace this identity.
- Run pinned dependency images and the Sentinel image with Compose on one private Docker network.
- Publish each successful `main` commit to GHCR under an immutable commit-SHA tag; deploy that exact tag to the existing VM.
- Put Caddy at ports 80/443. Keep the application host mapping on loopback and do not publish database, Redis, RabbitMQ, or Ollama ports.
- Use the public Azure hostname directly. Caddy validates it through the public DNS record and ports 80/443, then obtains and renews HTTPS automatically. A user-owned domain is optional, not required for TLS.
- Keep deployment opt-in through the `AZURE_DEPLOY_ENABLED` repository variable and a protected GitHub environment.

## Consequences

The link remains stable across application deployments, and rollback means selecting an older commit-SHA image. The design is inexpensive and faithful to the tested topology, but every component shares one VM failure domain. Deallocating the VM stops compute charges and also makes the demo unavailable; the retained disk/static IP may still incur charges. Deleting the public-IP resource forfeits the address and can break a custom-domain A record.

The GitHub workflow needs an SSH key and pinned host key. A public GHCR package avoids keeping a registry password on the VM. A private package instead requires a narrowly scoped read-only package credential and explicit secret rotation.
