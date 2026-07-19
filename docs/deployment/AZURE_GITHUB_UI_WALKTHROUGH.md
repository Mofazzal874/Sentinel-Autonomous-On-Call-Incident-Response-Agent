# Sentinel Azure and GitHub deployment: click-by-click walkthrough

This is the practical companion to the deeper [Azure deployment and CI/CD guide](AZURE_BEGINNER_DEPLOYMENT_GUIDE.md). It follows the same order as the Azure and GitHub screens used for the real Sentinel deployment.

Use this document when you need to deploy, demonstrate, troubleshoot, or explain the project. Commands are complete unless a value is visibly marked as a placeholder.

## Security notation

This repository is public, so identifiers that help fingerprint the Azure account are partially masked even though they are not passwords.

| Symbol | Meaning |
|---|---|
| `****` | Deliberately hidden in public documentation |
| `<YOUR-...>` | A value the operator must supply |
| exact unmasked value | Public infrastructure or repository identity |

Never record the SSH private key, JWT secret, webhook secret, database password, RabbitMQ password, Logic App callback URL, browser session, or GitHub token here.

## Recorded deployment inventory

### Azure account and capacity

| Item | Recorded value | Why it matters |
|---|---|---|
| Subscription | `Azure for Students` | Owns billing and quota |
| Subscription ID | `de08****-****-****-****-********c74c` | Masked account identifier |
| Tenant ID | `7f24****-****-****-****-********562a` | Masked Microsoft Entra directory identifier |
| Subscription state | `Enabled` | Azure resource operations are allowed |
| Region | `centralindia` / Central India | Resource placement and pricing boundary |
| Total regional vCPU quota | `6` | Maximum general regional allocation observed |
| Standard BS-family quota | `4` vCPUs | Matches the selected four-vCPU VM family limit |
| Student credit reported by owner | approximately `$100`, one-year validity | Credit is not the same as a budget |
| Demo budget | `$10` | Alert/automation threshold, not a hard cap |

### Dedicated Azure resources

| Item | Exact value |
|---|---|
| Resource group | `sentinel-demo-rg` |
| VM | `sentinel-demo-vm` |
| VM size | `Standard_B4as_v2` |
| VM placement | Non-zonal in Central India |
| Operating system | Ubuntu 24.04 |
| Admin user | `azureuser` |
| OS disk | `64 GiB`, `StandardSSD_LRS` |
| Public IP resource | `sentinel-demo-ip` |
| Static IPv4 | `20.219.22.24` |
| DNS label | `sentinel-mofazzal874` |
| FQDN | `sentinel-mofazzal874.centralindia.cloudapp.azure.com` |
| Public URL | `https://sentinel-mofazzal874.centralindia.cloudapp.azure.com/` |
| VNet | `sentinel-demo-vnet`, `10.40.0.0/16` |
| Subnet | `sentinel-demo-subnet`, `10.40.1.0/24` |
| NIC | `sentinel-demo-nic` |
| NSG | `sentinel-demo-nsg` |
| SSH rule | TCP `22` from the owner's masked `***.***.***.***/32` only |
| Web rules | TCP `80` and `443` from Internet |

### GitHub and delivery identity

| Item | Recorded value |
|---|---|
| Owner/repository | `Mofazzal874/Sentinel-Autonomous-On-Call-Incident-Response-Agent` |
| GitHub owner ID | `35369040` |
| GitHub repository ID | `1304261078` |
| GitHub environment | `azure-demo` |
| Entra application | `sentinel-github-deployer` |
| Azure client ID | `196c****-****-****-****-********f0ec` |
| Federated credential | `sentinel-main-azure-demo` |
| Custom role | `Sentinel Demo Release Activator` |
| OIDC issuer | `https://token.actions.githubusercontent.com` |
| OIDC audience | `api://AzureADTokenExchange` |
| Immutable subject | `repo:Mofazzal874@35369040/Sentinel-Autonomous-On-Call-Incident-Response-Agent@1304261078:environment:azure-demo` |
| First automation commit | `7a05a88f6024cf6d5a050a4bd4efb47b39d32a72` |
| Immutable image form | `ghcr.io/mofazzal874/sentinel-autonomous-on-call-incident-response-agent:<40-character-SHA>` |

The client, tenant, and subscription IDs are configuration rather than credentials. They are still masked here to avoid unnecessarily publishing account fingerprints. The seven actual values live as GitHub environment variables, not in source control.

### Recruiter demo dataset and safety limits

| Demonstrable number | Exact value |
|---|---:|
| Teams | `4` |
| Services | `12` |
| Service dependencies | `18` |
| Historical deployments | at least `60` |
| Runbooks | `10` |
| Seeded incidents | `30` |
| Metric samples | `10,800` |
| Structured logs | `1,080` |
| Public live-scenario types | `4` |
| Scenario types | bad deploy, dependency timeout, capacity saturation, cache staleness |
| Per-client submissions | `3` per minute |
| Global concurrent scenarios | `2` |
| Global accepted scenarios | `25` per day |
| Scenario lease timeout | `15` minutes |
| Alert deduplication window | `10` minutes |
| Consumer attempts | maximum `3` |
| AI output bound | `256` tokens |
| Latest complete backend evidence before delivery work | `113` tests across `38` suites, zero failures/errors/skips |
| Public remediation mode | `dry-run`; zero real infrastructure mutations |

These are generated, persisted, deterministic synthetic operations facts—not hard-coded browser cards and not customer data. The four public scenarios create new generated IDs and flow through PostgreSQL, Redis, RabbitMQ, the grounded agent, deterministic guardrail, and append-only ledger.

### Pinned runtime topology

| Service | Pinned release | Memory limit | Public exposure |
|---|---|---:|---|
| Sentinel | immutable Git commit-SHA image | `2,048 MiB` | VM loopback `8080`; Caddy proxy only |
| PostgreSQL + pgvector | PostgreSQL `17`, pgvector image `0.8.2` | `1,536 MiB` | Private Compose network |
| Redis | `7.4.9` Alpine | `512 MiB` | Private Compose network |
| RabbitMQ | `4.3.2` management Alpine | `1,024 MiB` | Private Compose network |
| Ollama | `0.32.1` | `8 GiB` | Private Compose network |
| Chat model | `qwen3:4b` | bounded inside Ollama | No public model endpoint |
| Embedding model | `nomic-embed-text` | bounded inside Ollama | No public model endpoint |
| Caddy | `2.10.2` Alpine | `256 MiB` | Public TCP `80`/`443` |

Every third-party container is also pinned by image digest in Compose. The memory limits are independent ceilings and are not expected to peak simultaneously; the burstable VM relies on normal workload phasing and the bounded public sandbox.

## Screen 1 — Confirm the Azure subscription

In Azure Portal, open the top search bar and search for **Subscriptions**.

Expected row:

```text
Subscription name: Azure for Students
My role:           Owner
Status:            Active
```

Cloud Shell equivalent:

```bash
az account show \
  --query '{Name:name,State:state,Default:isDefault,User:user.name}' \
  --output table
```

If another subscription is selected:

```bash
az account set --subscription 'Azure for Students'
```

Why this screen matters: every resource, role assignment, budget, and charge is scoped beneath a subscription. Running commands against the wrong default subscription can create a correct-looking deployment in the wrong billing boundary.

## Screen 2 — Inspect the dedicated resource group

In Azure Portal:

```text
Search → Resource groups → sentinel-demo-rg → Overview
```

You should see only resources belonging to this demo. In Cloud Shell:

```bash
az resource list \
  --resource-group sentinel-demo-rg \
  --query '[].{Name:name,Type:type,Location:location}' \
  --output table
```

The resource group is the blast-radius boundary. Do not delete it to fix an application error: deletion also removes the persistent disk, static IP, and stable Azure hostname.

## Screen 3 — Verify VM and public identity

In Azure Portal:

```text
Resource groups → sentinel-demo-rg → sentinel-demo-vm → Overview
```

Expected:

```text
VM size:           Standard_B4as_v2
Public IP:         20.219.22.24
Computer name:     sentinel-demo-vm
Operating system:  Linux / Ubuntu 24.04
```

Cloud Shell verification:

```bash
az vm get-instance-view \
  --resource-group sentinel-demo-rg \
  --name sentinel-demo-vm \
  --query "instanceView.statuses[?starts_with(code,'PowerState/')].displayStatus" \
  --output tsv

az network public-ip show \
  --resource-group sentinel-demo-rg \
  --name sentinel-demo-ip \
  --query '{IP:ipAddress,Hostname:dnsSettings.fqdn,Allocation:publicIPAllocationMethod}' \
  --output table
```

`VM running` means compute is allocated. `VM deallocated` means the application is offline and compute allocation has stopped.

## Screen 4 — Understand the GitHub Actions page

Open:

```text
GitHub repository → Actions → Verify, publish, and deploy demo
```

The graph contains two jobs:

```text
verify-and-publish ──────> deploy
```

### `verify-and-publish`

This job:

1. checks out the exact commit;
2. installs Java 25 on the temporary runner;
3. installs pinned frontend dependencies;
4. type-checks and builds the Next.js static console;
5. runs the Spring/Gradle regression suite;
6. builds the container image;
7. publishes both the immutable SHA tag and moving `main` tag to GHCR.

If this job fails, Azure is not touched.

### `deploy`

This job:

1. enters the `azure-demo` GitHub environment;
2. requests a GitHub OIDC token;
3. exchanges it for a short-lived Azure access token;
4. invokes `activate-release.sh` through the Azure VM agent;
5. checks out the exact source SHA on the VM;
6. pulls the image tagged with that same SHA;
7. converges Docker Compose while preserving named volumes;
8. checks the stable public readiness URL.

Before `AZURE_DEPLOY_ENABLED=true`, a grey skipped deploy node is expected. After configuration, a skipped node means the repository variable is absent, misspelled, stored at the wrong scope, or not exactly lowercase `true`.

## Screen 5 — Configure the GitHub environment

Open:

```text
Repository → Settings → Environments
```

Select or create exactly:

```text
azure-demo
```

Under **Environment variables**, create seven entries. Use the real values printed by `configure-github-oidc.sh`; the public examples below are masked where appropriate.

| Variable name | Value shown in public documentation |
|---|---|
| `AZURE_CLIENT_ID` | `196c****-****-****-****-********f0ec` |
| `AZURE_TENANT_ID` | `7f24****-****-****-****-********562a` |
| `AZURE_SUBSCRIPTION_ID` | `de08****-****-****-****-********c74c` |
| `AZURE_RESOURCE_GROUP` | `sentinel-demo-rg` |
| `AZURE_VM_NAME` | `sentinel-demo-vm` |
| `AZURE_DEMO_ADDRESS` | `sentinel-mofazzal874.centralindia.cloudapp.azure.com` |
| `AZURE_DEMO_HEALTH_URL` | `https://sentinel-mofazzal874.centralindia.cloudapp.azure.com/actuator/health/readiness` |

These belong under **Variables**, not **Secrets**. No client secret exists.

Next open:

```text
Repository → Settings → Secrets and variables → Actions → Variables
```

Create the repository variable:

```text
Name:  AZURE_DEPLOY_ENABLED
Value: true
```

Why it is separate: the job-level `if` condition needs a repository-level switch before GitHub starts the environment-scoped deploy job.

## Screen 6 — Run and read the first automated deployment

Open:

```text
Repository → Actions → Verify, publish, and deploy demo → Run workflow
```

Select branch `main` and press **Run workflow**.

Healthy final state:

```text
Overall workflow:    Success
verify-and-publish:  Success
deploy:              Success
```

Recorded first proof on 20 July 2026:

| Evidence | Result |
|---|---|
| Workflow run | [`29699411314`](https://github.com/Mofazzal874/Sentinel-Autonomous-On-Call-Incident-Response-Agent/actions/runs/29699411314) |
| Released SHA | `7a05a88f6024cf6d5a050a4bd4efb47b39d32a72` |
| `verify-and-publish` | `success` |
| `deploy` | `success` |
| Public readiness | HTTP `200`, `{"status":"UP"}` |
| Public console | HTTP `200`, title `Sentinel | Incident Operations Console` |

The public verification performed by the workflow is:

```bash
curl --fail --show-error --silent --retry 12 --retry-delay 5 \
  'https://sentinel-mofazzal874.centralindia.cloudapp.azure.com/actuator/health/readiness'
```

The application URL does not change between releases because the static Public IP and DNS label are separate from the replaceable container image.

## Screen 7 — Diagnose the first red step, not the last symptom

| First red step | Meaning | First response |
|---|---|---|
| Frontend install/typecheck/build | UI dependency or compile failure | Read the first npm error; Azure was untouched |
| Gradle test/build | Backend regression or packaging failure | Fix the failing test/build locally |
| GHCR login/publish | Package permission or registry problem | Check workflow package permission and repository package visibility |
| Azure Login | OIDC subject, environment, client/tenant/subscription mismatch | Compare the seven variables and immutable subject; do not create a client secret |
| VM Run Command | VM deallocated, RBAC propagation, VM-agent, or release-script failure | Check VM power state and copy the complete command output |
| Public readiness | Stack started but did not become healthy or publicly reachable | Inspect bounded Compose status/logs through Run Command |

Do not recreate the VM, public IP, DNS label, Entra application, or role assignment as a first troubleshooting step. Idempotent setup scripts can be rerun after reading the actual failure.

## Screen 8 — Inspect the `$10` budget

In Azure Portal:

```text
Cost Management + Billing → Cost Management → Budgets
```

Open the existing `$10` budget and record its exact name. The amount is a comparison threshold; it does not reserve `$10` and does not stop consumption by itself.

Cloud Shell:

```bash
az consumption budget list --output table
```

After the first automated deployment is proven, connect the early deallocation action:

```bash
cd ~/sentinel-deploy
git pull --ff-only

export AZURE_BUDGET_NAME='<EXACT BUDGET NAME FROM THE TABLE>'
export AZURE_COST_GUARD_THRESHOLD_PERCENT='50'
export CONFIRM_CONFIGURE_COST_GUARD='yes'

bash deployment/azure-demo/configure-cost-guard.sh
```

For a `$10` budget, 50% nominally means `$5`. It is deliberately early because Azure cost records can arrive 8–24 hours after usage. This is a safety margin, not a promise that the final amount cannot exceed `$10`.

## Screen 9 — Verify the cost guard without burning credit

In Azure Portal, verify these resources exist inside `sentinel-demo-rg`:

```text
sentinel-budget-deallocate   Logic App
sentinel-budget-stop         Action Group
```

Then inspect the budget notification named:

```text
SentinelEarlyDeallocate
```

The identities intentionally have different authority:

```text
GitHub deployer → Run Command only → cannot start or stop VM
Budget Logic App → deallocate only → cannot start VM or deploy code
```

When the guard fires, confirm:

```bash
az vm get-instance-view \
  --resource-group sentinel-demo-rg \
  --name sentinel-demo-vm \
  --query "instanceView.statuses[?starts_with(code,'PowerState/')].displayStatus" \
  --output tsv
```

Expected: `VM deallocated`.

## Screen 10 — Start, deallocate, or retire deliberately

Before starting a budget-stopped VM, check Cost Analysis and remaining student credit.

Start:

```bash
az vm start \
  --resource-group sentinel-demo-rg \
  --name sentinel-demo-vm
```

Deallocate:

```bash
az vm deallocate \
  --resource-group sentinel-demo-rg \
  --name sentinel-demo-vm
```

Permanent retirement, destructive:

```bash
az group delete \
  --name sentinel-demo-rg \
  --yes \
  --no-wait
```

Do not run retirement merely to restart the application. It deletes the database, OS disk, Public IP, and stable `cloudapp.azure.com` hostname.

## What to say in a demonstration

“A push to `main` does not directly SSH into production. GitHub first compiles the Next.js console, runs the Spring regression suite, and publishes an immutable commit-SHA image. The deploy job uses repository-and-environment-bound OIDC to obtain a short-lived Azure token. Its custom role can invoke Run Command only on one VM. The VM activates the same source and image SHA, preserves named database volumes, and the workflow verifies the stable public readiness URL. A separate deallocate-only managed identity responds early to the budget, and GitHub cannot restart a financially stopped VM.”

## Five checks before sharing the résumé link

- [x] The first OIDC GitHub run showed both jobs green for SHA `7a05a88f6024cf6d5a050a4bd4efb47b39d32a72`.
- [ ] The stable HTTPS URL opens in a private browser window.
- [ ] A fixed public scenario creates a real persisted incident and reaches a dry-run ledger decision.
- [ ] The page clearly labels the operational dataset as deterministic synthetic data.
- [ ] Azure Cost Analysis, the `$10` budget, and VM power state have been reviewed.
