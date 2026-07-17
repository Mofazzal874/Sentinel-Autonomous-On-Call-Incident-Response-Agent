# ADR 0001: Keep large development artifacts on E

- Status: Accepted
- Date: 2026-07-18

## Context

The system drive has limited free space. Sentinel will accumulate Java dependencies, container images, database volumes, message-broker data, and test container layers. Default Windows locations would put most of that growth on `C:`.

The machine already had WSL 2.3.24 and an Ubuntu 24.04 WSL 2 distribution. Docker Desktop was absent. The Ubuntu distribution's virtual disk was already under the user's `C:` profile and was not required as Docker Desktop's data disk.

## Decision

- Reuse the existing compatible WSL installation rather than reinstall it.
- Install Java SDKs and portable build tools under `E:\DevTools`.
- Keep Gradle caches under `E:\DevCaches\gradle`.
- Install Docker Desktop under `E:\Docker\Docker`.
- Keep Docker Desktop's private WSL disks under `E:\Docker\wsl`.
- Use the WSL 2 Linux-container backend and disable Windows-container support.
- Keep project source in the existing `E:` workspace.
- Audit installed software before every installation and remove verified-unnecessary installers after verification.
- Do not migrate or unregister an existing WSL distribution as a side effect of installing Docker. Relocation requires its own verified backup and explicit scope.

## Consequences

- Container images, writable layers, and volumes consume `E:` rather than `C:`.
- Phase 1 Compose services and Testcontainers reuse the same Docker WSL disk.
- Small Windows-managed files remain in the registry and user profile; a literal zero-byte `C:` footprint is not possible.
- `E:` free space must be monitored and unused Docker objects pruned intentionally, never indiscriminately during active development.
- The existing Ubuntu virtual disk still consumes significant `C:` space until a separate migration is approved and completed.

## Verification

- Docker Desktop 4.76.0 application exists at `E:\Docker\Docker`.
- Docker Engine 29.5.2 and Docker Compose v5.1.4 respond successfully.
- Docker settings record `CustomWslDistroDir` as `E:\Docker\wsl`.
- Docker virtual disks exist under `E:\Docker\wsl`.
- `C:\Program Files\Docker\Docker` and the default local-app-data Docker WSL directory do not exist.
- An end-to-end `hello-world` container completed successfully and its test image was removed.
