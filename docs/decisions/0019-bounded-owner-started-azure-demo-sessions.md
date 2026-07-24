# ADR 0019: Bound the Azure demo with owner-started compute sessions

- Status: Accepted
- Date: 2026-07-24

## Context

The public demo ran continuously on one non-zonal `Standard_B4as_v2` in Central India. PostgreSQL/pgvector, Redis, RabbitMQ, Ollama, Sentinel, and Caddy shared its 16 GiB memory. Traffic was negligible, but Azure charged the allocated VM capacity rather than its CPU utilization.

The owner observed roughly `$2.50/day`. The official July 2026 USD retail meters explain that result:

- B4as v2 Linux: `$0.0984/hour`, or `$2.3616/day`;
- 64-GiB E6 LRS Standard SSD: `$5.28/month`, modeled as `$0.176/day`;
- Standard static IPv4: `$0.005/hour`, or `$0.12/day`.

The modeled always-on total is `$2.6576/day`. Docker/database idleness cannot lower an allocated VM's hourly meter. The target is below `$0.50/day` without discarding the stable hostname, disk, data, real message broker, real database, or local AI path.

Azure budget records arrive too late to act as a precise runtime lease. Public auto-wake would convert anonymous traffic into spending authority. A push-triggered start would let ordinary delivery override financial deallocation.

## Decision

Operate the portfolio deployment as a normally deallocated `Standard_B2as_v2` with an owner-started two-hour lease.

1. Aggregate container memory ceilings remain below 7 GiB on the 8-GiB VM.
2. Ollama loads one model and one inference at a time; chat and embedding keep-alives are short.
3. Every container has bounded local logs.
4. Successful releases retain only the current and previous Sentinel application images.
5. GitHub may verify, publish, inspect VM state, and activate only when already running. It has no start authority.
6. A separate Consumption Logic App identity may read, start, and deallocate only the exact demo VM. Its signed trigger is an owner credential.
7. Each accepted wake starts one non-extendable two-hour session and then calls Azure deallocation.
8. On boot, the VM activates current `main` only when the matching immutable GHCR SHA image already exists. A missing image preserves the installed release.
9. A separate budget identity remains deallocate-only and can stop compute if the time lease fails.
10. A read-only audit records VM/disk/IP state, runtime resource use, Docker storage, delayed daily cost, and lifecycle operations.

The static IP, DNS label, OS disk, named volumes, and public URL remain. The URL is offline while compute is deallocated.

## Cost model

Using the reviewed retail rates:

```text
deallocated floor = 5.28 / 30 + 0.005 × 24
                  = $0.2960/day

two-hour B2 session = 0.2960 + 0.0492 × 2
                    = $0.3944/day
```

This is a model rather than a billing guarantee. Subscription offers, taxes, exchange rates, month length, Logic App operations, and later prices can differ. Cost Management data is delayed, so acceptance needs multiple daily observations.

## Authority boundaries

| Identity | May do | Must not do |
|---|---|---|
| GitHub deployer | Read VM, invoke exact-VM Run Command | Start, deallocate, resize, delete |
| Owner session Logic App | Read, start, deallocate exact VM | Run commands, resize, deploy, delete |
| Budget Logic App | Read and deallocate exact VM | Start, run commands, resize, deploy, delete |
| Anonymous visitor | Use bounded application demo while online | Start compute or supply infrastructure inputs |

## Consequences

### Positive

- Modeled daily cost falls by about 85%.
- Existing data and the résumé URL survive deallocation.
- Forgotten sessions end without relying on delayed billing data.
- Delivery cannot undo a financial stop.
- CI can continue while the VM is offline.
- Stale logs and application images cannot grow without a bound.

### Negative

- The public URL is unavailable outside an owner-opened session.
- Startup and exact-SHA convergence take several minutes.
- Two-vCPU inference is expected to be slower and needs production measurement.
- The retained disk and static IP still cost about `$0.296/day`.
- The owner must protect a signed wake URL and deliberately open demo windows.

## Alternatives rejected

- **Always-on B2as v2:** approximately `$1.4768/day`, still above target.
- **Rely only on the `$10` budget:** delayed cost evaluation cannot bound each session or guarantee a hard limit.
- **Public wake endpoint:** scanners and bots could directly create spend.
- **Let GitHub start the VM:** a normal push could override a budget or operator stop.
- **Delete the resource group after each demo:** destroys the stable DNS identity and persistent data.
- **Immediate Container Apps migration:** true scale-to-zero is attractive, but the current stateful PostgreSQL/RabbitMQ/Redis topology and 4B local model need a separately designed persistence/provider migration. It is not a safe emergency cost patch.
- **Spot VM:** cheaper but eviction, capacity, and stable-demo behavior are unsuitable as the only control.

## Verification

- `deployment/azure-demo/verify-cost-controls.sh` enforces the memory/log/model/workflow/cost invariants in CI.
- The owner bootstrap must record the pre-resize runtime snapshot and finish at `Standard_B2as_v2`, `VM deallocated`.
- One real live investigation must complete on B2as v2 without an OOM or container restart.
- The session workflow must deallocate automatically at lease expiry.
- Three delayed daily cost snapshots should converge toward the `$0.3944/day` model.
