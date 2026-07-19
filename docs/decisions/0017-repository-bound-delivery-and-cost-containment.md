# ADR 0017: Repository-bound delivery and layered cost containment

- Status: Accepted
- Date: 2026-07-20

## Context

Sentinel has a stable Azure VM hostname and GitHub Actions already verifies and publishes commit-SHA images. The original optional deployment job used SSH. The VM NSG correctly restricts SSH to the owner's recorded `/32`, so a GitHub-hosted runner cannot reach it without weakening the firewall. The student's `$10` Azure budget originally sent notifications but could not stop consumption.

The public URL should update after each verified `main` commit, while a cost-control action must not be undone by a later push. Azure cost records and budget evaluation are delayed, and deallocating a VM does not eliminate disk or static-IP charges.

## Decision

GitHub Actions authenticates with Microsoft Entra through OIDC. The federated credential is restricted to the repository's immutable owner/repository IDs and the `azure-demo` environment. A custom role, assigned at the exact VM scope, permits only VM read, instance-view read, and action Run Command. It cannot start, stop, resize, create, or delete compute.

Every successful build publishes an image tagged with the full commit SHA. The deployment sends a versioned POSIX activator plus the source SHA, image URI, and hostname through Azure Run Command. The activator rejects mismatched image/source identities, refuses tracked VM changes, preserves ignored secrets and named volumes, converges Compose, and waits for readiness. Workflow concurrency serializes releases.

Cost containment uses a separate system-assigned Logic App identity. An Action Group connected to the existing budget invokes the workflow at an early actual-cost threshold, 50% by default. Its custom VM-scope role permits deallocation but not start, deployment, resize, or deletion. GitHub cannot restart a budget-stopped VM. Optional Azure VM auto-shutdown provides a time-based independent control.

Automatic resource-group deletion is rejected. It would destroy the stable DNS resource and database, and delayed cost data means it still cannot guarantee an exact final amount.

## Consequences

- Port 22 remains owner-only; GitHub stores no Azure password or SSH key.
- A green full workflow proves the same SHA passed tests, was published, activated, and passed public readiness.
- Deployment stops when the VM is deallocated and requires an explicit owner start after a cost review.
- The budget guard reduces exposure but is not a hard cap. Cost can exceed the nominal threshold because Azure's data is delayed.
- VM deallocation stops compute allocation, while disk, retained static IP, and automation executions can still cost money.
- Retiring every charge requires an explicit destructive resource-group deletion and loss of the stable demo state.

## Rejected alternatives

- **Open SSH broadly for GitHub runners:** weakens the reviewed network boundary and runner addresses are unsuitable as a small stable allowlist.
- **Store a service-principal client secret:** creates a long-lived credential with rotation and leakage risk.
- **Give GitHub Virtual Machine Contributor:** grants far more lifecycle authority than a release requires.
- **Allow GitHub to start the VM:** a code push could override budget deallocation.
- **Treat a `$10` budget as a hard limit:** contradicts Azure's documented budget and cost-data semantics.
