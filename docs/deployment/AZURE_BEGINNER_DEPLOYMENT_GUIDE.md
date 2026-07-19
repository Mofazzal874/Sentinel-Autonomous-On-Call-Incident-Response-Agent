# Sentinel on Azure: beginner deployment and CI/CD guide

This is the start-to-finish learning and operations guide for the portfolio deployment. It records the deployment that was actually built, the commands used at each boundary, automated delivery, cost containment, recovery, and the remaining one-time account configuration.

Do not paste the whole document into a terminal. Run one checkpoint at a time, read its expected result, and stop when the result differs.

## 1. Current state

As of 20 July 2026:

- Azure subscription: `Azure for Students`.
- Region: `centralindia`.
- Resource group: `sentinel-demo-rg`.
- VM: `sentinel-demo-vm`, non-zonal `Standard_B4as_v2`, Ubuntu 24.04.
- Public hostname: `sentinel-mofazzal874.centralindia.cloudapp.azure.com`.
- Stable HTTPS URL: `https://sentinel-mofazzal874.centralindia.cloudapp.azure.com/`.
- Container registry: public GitHub Container Registry package under `ghcr.io/mofazzal874/`.
- Safety mode: `SENTINEL_REMEDIATION_DRY_RUN=true`.
- CI: active. Each `main` push verifies and publishes an immutable image.
- CD implementation: OIDC plus Azure VM Run Command is committed; one-time Azure/GitHub configuration must be completed before enabling it.
- Cost guard implementation: an early budget action can deallocate the VM; it must be connected to the user's existing budget once from Cloud Shell.

Until `AZURE_DEPLOY_ENABLED=true` is added, the site can be older than the latest Git commit. After it is enabled, a green workflow means the exact commit-SHA image was published, activated, and checked at the stable readiness URL.

## 2. Mental model

```text
source commit
    |
    v
GitHub Actions CI
    |- build Next.js static frontend
    |- test Java/Spring backend
    |- build OCI image
    `- publish GHCR image tagged with the full commit SHA
                         |
                         v
                release command / future CD
                         |
                         v
Azure static IP + DNS -> Caddy HTTPS -> Sentinel
                                          |- PostgreSQL + pgvector
                                          |- Redis
                                          |- RabbitMQ
                                          `- Ollama
```

The URL and software have different identities:

- The Azure Public IP and DNS label own the stable address.
- The full Git commit SHA identifies source.
- The GHCR SHA tag identifies the application image released from that source.
- Updating the image does not recreate the IP, DNS name, VM, or Docker volumes.

## 3. Vocabulary

- **Azure Cloud Shell:** a browser terminal already authenticated to your Azure account. Its filesystem and network identity are not your laptop's.
- **Resource group:** an Azure lifecycle boundary containing only this demo's resources.
- **VM:** the Ubuntu computer rented from Azure.
- **NSG:** the network firewall attached to the VM network interface.
- **Static Public IP:** the retained public network identity.
- **DNS label/FQDN:** the human-readable Azure hostname mapped to that IP.
- **Cloud-init:** the first-boot script that installs Docker on the VM.
- **OCI image:** the immutable application package built by GitHub Actions.
- **GHCR:** GitHub Container Registry, where Sentinel images are published.
- **CI:** verify and package every change.
- **CD:** deliberately deliver a verified package to Azure.
- **OIDC:** short-lived identity federation; GitHub proves which workflow it is without storing an Azure password.
- **Run Command:** Azure asks the VM agent to run a script inside the VM; it does not require public SSH.

## 4. What runs where

| Command prefix/location | Where it runs | Purpose |
|---|---|---|
| Windows PowerShell in `E:\New folder (2)` | Local PC | Develop, test, and push source |
| Azure Cloud Shell prompt | Microsoft-managed shell | Inspect and control Azure resources |
| `az vm run-command invoke` script | Inside the Ubuntu VM as root | Pull/restart/diagnose the deployed stack |
| GitHub Actions runner | Temporary GitHub Linux VM | Test, build, publish, and eventually deploy |

This distinction explains the earlier `pipefail` error. Azure action Run Command invoked the supplied text with `/bin/sh`; `set -o pipefail` is not portable to every `/bin/sh`. Repository scripts declare `#!/usr/bin/env bash`, while small inline Run Command scripts use portable `set -eu`.

## 5. One-time local rehearsal

Before cloud provisioning, from repository-root PowerShell:

```powershell
$env:JAVA_HOME='E:\DevTools\temurin-25\jdk-25.0.3+9'
$env:GRADLE_USER_HOME='E:\DevCaches\gradle'
.\deployment\azure-demo\test-local.ps1
```

This reuses the E-drive Java, Gradle cache, Docker storage, and local Ollama models. It creates only the ignored `.sentinel/azure-demo-local.env`, a separately named Compose stack, and task-specific containers.

Expected checks:

- readiness and liveness return HTTP `200`;
- Prometheus remains protected with `401` anonymously;
- Flyway migrations finish before Hibernate validation;
- semantic runbook embeddings are indexed;
- the frontend is served by the Spring Boot image.

Stop the rehearsal without deleting its data:

```powershell
.\deployment\azure-demo\stop-local.ps1
```

Delete only the rehearsal volumes when their data is no longer needed:

```powershell
.\deployment\azure-demo\stop-local.ps1 -DeleteData
```

## 6. One-time Azure subscription checks

Open Azure Cloud Shell and select Bash. Confirm the active subscription:

```bash
az account show \
  --query '{Name:name, State:state, Default:isDefault, Tenant:tenantId, Subscription:id}' \
  --output table
```

Expected: `Azure for Students`, `Enabled`, and `True` under Default.

Select it explicitly and register the providers used by the bundle:

```bash
az account set --subscription 'Azure for Students'

az provider register --namespace Microsoft.Compute --wait
az provider register --namespace Microsoft.Network --wait

az provider show --namespace Microsoft.Compute --query registrationState --output tsv
az provider show --namespace Microsoft.Network --query registrationState --output tsv
```

Expected: both print `Registered`.

Check region and quota:

```bash
az account list-locations \
  --query "[?name=='centralindia'].{Name:name,DisplayName:displayName}" \
  --output table

az vm list-usage \
  --location centralindia \
  --query "[?contains(name.localizedValue, 'vCPUs')].{Name:name.localizedValue,Used:currentValue,Limit:limit}" \
  --output table
```

An empty first `az vm list-usage` result was caused by `Microsoft.Compute` not being registered. It did not mean the subscription had no quota.

Check the chosen SKU:

```bash
az vm list-skus \
  --location centralindia \
  --resource-type virtualMachines \
  --size Standard_B4as_v2 \
  --query "[].{Name:name,Restrictions:restrictions}" \
  --output json
```

For this subscription, the restriction applied to zone 3 rather than the whole Central India location. The provisioning script therefore creates a non-zonal VM. `Standard_B4ms` was location-restricted and was not selected.

## 7. Budget checkpoint

The user created a `$10` Azure budget while retaining approximately `$100` of student credit. The amount is a sensible experiment limit, but an Azure budget is an alert—not a prepaid wallet, quota, or instantaneous circuit breaker.

Microsoft documents two limitations that control this design:

- crossing a budget does not stop resources or consumption;
- cost records are normally delayed by 8–24 hours and budgets are evaluated periodically, not per request.

Therefore nobody can truthfully guarantee that Azure will stop at exactly `$10.00`. An automation triggered at 100% can run after more cost has already accrued. Sentinel instead uses **early containment**: at 50% actual usage by default, an Action Group invokes a Logic App, whose managed identity has permission to deallocate only `sentinel-demo-vm`.

```text
delayed Azure cost record -> existing budget reaches 50%
                         -> Action Group webhook
                         -> Logic App managed identity
                         -> VM deallocate API
                         -> compute billing stops
```

Deallocation does not erase anything and does not change the hostname. It also does not remove every charge: the managed OS disk and Standard static Public IP can still be billed. The only way to end all future charges from this dedicated resource group is to delete the group, which also destroys the database, disk, IP, and stable résumé URL. That destructive tradeoff is intentionally not automated.

Before and after deployment:

```bash
az resource list --resource-group sentinel-demo-rg --output table
```

Use Azure Portal **Cost Management + Billing → Budgets** and **Cost analysis** to inspect actual spend. Keep the original email notifications as well as the automated early action. Do not treat the action as an exact financial guarantee.

## 8. DNS label and source preparation

The chosen label was `sentinel-mofazzal874`, producing:

```text
sentinel-mofazzal874.centralindia.cloudapp.azure.com
```

The hostname belongs to the separate static Public IP resource. Ordinary application releases must never delete that resource.

In Cloud Shell, clone or update the canonical public repository:

```bash
cd ~

if [ -d sentinel-deploy/.git ]; then
  cd sentinel-deploy
  git pull --ff-only
else
  git clone \
    https://github.com/Mofazzal874/Sentinel-Autonomous-On-Call-Incident-Response-Agent.git \
    sentinel-deploy
  cd sentinel-deploy
fi

git status --short
git log -1 --oneline
```

Expected: `git status --short` prints nothing.

## 9. One-time Azure resource provisioning

Record the public IPv4 address from which direct SSH would be allowed. Do not use the VM's public IP, and do not add `/32` yourself:

```bash
read -r -p 'Enter your recorded home public IPv4 address: ' AZURE_SSH_SOURCE_IP
export AZURE_SSH_SOURCE_IP
```

Set the reviewed values:

```bash
export AZURE_DEMO_DNS_LABEL='sentinel-mofazzal874'
export CONFIRM_CREATE_AZURE_RESOURCES='yes'
```

Review before running:

```bash
sed -n '1,220p' deployment/azure-demo/provision-azure.sh
```

Then provision:

```bash
bash deployment/azure-demo/provision-azure.sh
```

The script creates only:

- `sentinel-demo-rg`;
- VNet and subnet;
- NSG with public TCP 80/443 and TCP 22 only from the recorded `/32`;
- Standard static Public IP plus DNS label;
- NIC;
- non-zonal Ubuntu 24.04 `Standard_B4as_v2` VM;
- 64 GB Standard SSD OS disk.

It does not create AKS, ACR, Azure OpenAI, managed PostgreSQL, managed Redis, or resources in another project.

## 10. First-boot verification

Wait for cloud-init through the Azure VM agent:

```bash
az vm run-command invoke \
  --resource-group sentinel-demo-rg \
  --name sentinel-demo-vm \
  --command-id RunShellScript \
  --scripts 'cloud-init status --wait' \
  --query 'value[0].message' \
  --output tsv
```

Expected stdout: `status: done`.

Verify network identity and power state:

```bash
az network public-ip show \
  --resource-group sentinel-demo-rg \
  --name sentinel-demo-ip \
  --query '{IP:ipAddress,Hostname:dnsSettings.fqdn}' \
  --output table

az vm get-instance-view \
  --resource-group sentinel-demo-rg \
  --name sentinel-demo-vm \
  --query "instanceView.statuses[?starts_with(code,'PowerState/')].displayStatus" \
  --output tsv
```

Expected: the stable FQDN and `VM running`.

The bootstrap installs Docker Engine from Docker's Ubuntu repository, enables it, adds `azureuser` to the Docker group, and creates `/opt/sentinel`. It does not install Docker Desktop or change the local Windows installation.

## 11. Understand GitHub CI and GHCR

The workflow is `.github/workflows/deploy-azure-demo.yml`.

Every push to `main` currently performs:

1. checkout;
2. Java 25 setup;
3. `npm ci`, frontend type checking, and frontend static export;
4. Gradle clean tests and executable JAR build;
5. OCI image build;
6. publication of two GHCR tags:
   - immutable `:<full-40-character-commit-sha>`;
   - convenient moving `:main`.

Use the SHA tag for deployment and rollback. Never use `:main` as historical evidence because it changes after each successful build.

The workflow uses GitHub's short-lived `GITHUB_TOKEN` with `packages: write` to publish. The package was made public so the VM can pull without storing a GitHub registry password.

Check a workflow in the GitHub UI:

```text
Repository → Actions → Verify, publish, and deploy demo → selected commit
```

Do not deploy until `verify-and-publish` is green. If CI is red, open the failed step and repair the source; do not bypass it by building an untracked image on the VM.

## 12. First deployment or manual immutable update

This is the fallback release method and the correct way to rehearse the same versioned script before OIDC CD is enabled.

In Cloud Shell, update the repository and capture the complete verified SHA:

```bash
cd ~/sentinel-deploy
git fetch --prune origin
git checkout main
git pull --ff-only

export RELEASE_SHA="$(git rev-parse HEAD)"
export RELEASE_IMAGE="ghcr.io/mofazzal874/sentinel-autonomous-on-call-incident-response-agent:${RELEASE_SHA}"

printf 'Release SHA: %s\nImage: %s\n' "$RELEASE_SHA" "$RELEASE_IMAGE"
```

Confirm the printed SHA is the green GitHub Actions run you intend to deploy.

Run the repository-owned activator through the VM agent. Linux Action Run Command supplies parameters as positional `$1`, `$2`, and `$3` values:

```bash
az vm run-command invoke \
  --resource-group sentinel-demo-rg \
  --name sentinel-demo-vm \
  --command-id RunShellScript \
  --scripts @deployment/azure-demo/activate-release.sh \
  --parameters \
    "$RELEASE_SHA" \
    "$RELEASE_IMAGE" \
    'sentinel-mofazzal874.centralindia.cloudapp.azure.com' \
  --query 'value[0].message' \
  --output tsv
```

What the release does:

1. refuses to overwrite tracked VM changes;
2. checks out the exact source commit;
3. creates the ignored permission-restricted environment file only if absent;
4. pulls the exact public GHCR image;
5. asks Compose to converge the stack;
6. preserves named PostgreSQL, Redis, RabbitMQ, Ollama, and Caddy volumes;
7. runs forward-only Flyway migrations during Sentinel startup;
8. waits up to 180 seconds for readiness.

The first model download is large and can take much longer than an ordinary application update. Later releases reuse the Ollama volume.

## 13. Post-deployment verification

From Cloud Shell, test the public boundary:

```bash
curl --fail --show-error --silent \
  https://sentinel-mofazzal874.centralindia.cloudapp.azure.com/actuator/health/readiness

curl --head --fail --show-error \
  https://sentinel-mofazzal874.centralindia.cloudapp.azure.com/
```

Expected: readiness JSON with status `UP`, and HTTP `200` for `/`.

Inspect running containers inside the VM:

```bash
cat > /tmp/sentinel-status.sh <<'SCRIPT'
set -eu
cd /opt/sentinel/release
docker compose \
  --project-name sentinel-azure-demo \
  --env-file .sentinel/azure-demo.env \
  --file deployment/azure-demo/compose.yaml \
  --file deployment/azure-demo/compose.azure.yaml \
  ps
SCRIPT

az vm run-command invoke \
  --resource-group sentinel-demo-rg \
  --name sentinel-demo-vm \
  --command-id RunShellScript \
  --scripts @/tmp/sentinel-status.sh \
  --query 'value[0].message' \
  --output tsv

rm /tmp/sentinel-status.sh
```

Browser acceptance:

1. open the stable HTTPS URL;
2. perform a hard refresh (`Ctrl+Shift+R`);
3. confirm the operator console, not the old landing page, appears;
4. select a fixed scenario and launch it;
5. wait while the UI polls its durable status;
6. inspect classification, evidence, proposal, guardrail decision, and ledger;
7. confirm the result says dry-run and never claims a real infrastructure mutation.

## 14. Diagnosing common failures

### Browser says `ERR_CONNECTION_REFUSED`

DNS and the VM can be healthy while no process listens on ports 80/443. Check VM power, Compose containers, Caddy logs, and Sentinel readiness before blaming campus Wi-Fi. A refused connection normally means the destination actively rejected the TCP connection; switching to a hotspot is only useful after server-side checks.

### `set: Illegal option -o pipefail`

The inline Run Command was interpreted by `/bin/sh`. Use `set -eu` inline, or run a repository Bash script with `bash script-name`.

### Compose reports `stat .: permission denied`

Azure Run Command starts in a root-only `waagent` working directory. `start-azure.sh` now changes to the repository directory before invoking Compose.

### Image pull says manifest unknown

The CI build may still be running, may have failed, or the tag may be abbreviated. GHCR uses the full 40-character SHA tag. Wait for green CI and use `git rev-parse HEAD`.

### Public page still looks old

A Git push does not currently update Azure. Confirm the exact SHA image was manually released, then hard-refresh the browser. The static frontend is inside the backend image; there is no separate frontend server to deploy.

### Run remains queued for a long time

The CPU-hosted Qwen3 model is the slow part. Check Sentinel and Ollama logs. The public sandbox also intentionally limits concurrent and daily work.

## 15. Rollback

Rollback means deploy a previously green full SHA image through the same release procedure. It does not mean delete the database or edit a Flyway migration.

1. Choose a previously successful commit in GitHub Actions.
2. Set `RELEASE_SHA` to its full 40-character SHA.
3. Set `RELEASE_IMAGE` to the matching GHCR tag.
4. run the reviewed release script again.
5. repeat readiness and browser checks.

Important: Flyway migrations are forward-only. An older application image is safe only if it understands the current database schema. For an incompatible schema change, fix forward with a new migration or restore a deliberate backup into a separately reviewed recovery environment. Never rewrite a migration that has run.

The current data is synthetic portfolio data, but the production lesson remains: back up before schema-changing releases and test restore—not only backup creation.

## 16. Start, stop, and cost control

Check state:

```bash
az vm get-instance-view \
  --resource-group sentinel-demo-rg \
  --name sentinel-demo-vm \
  --query "instanceView.statuses[?starts_with(code,'PowerState/')].displayStatus" \
  --output tsv
```

Deallocate compute when the public demo can be offline:

```bash
az vm deallocate \
  --resource-group sentinel-demo-rg \
  --name sentinel-demo-vm
```

Start it again:

```bash
az vm start \
  --resource-group sentinel-demo-rg \
  --name sentinel-demo-vm
```

The static hostname remains, but the site is unavailable while the VM is deallocated. Disk and public-IP charges may remain. Docker services use `restart: unless-stopped`, so they return after VM startup; verify readiness.

Final teardown is destructive and deletes the database, disk, static IP, and stable hostname:

```bash
az group delete --name sentinel-demo-rg --yes --no-wait
```

Do not run teardown merely to stop compute charges; use deallocation. Run deletion only when intentionally retiring the entire demo.

## 17. Automated CD with Azure OIDC and VM Run Command

The implemented design is:

```text
green verify-and-publish job
      |
GitHub requests a short-lived OIDC token
      |
Microsoft Entra validates repository + azure-demo environment identity
      |
Azure Login receives a short-lived access token
      |
az vm run-command invoke updates the existing VM
      |
workflow verifies the stable public readiness URL
```

This removes the long-lived SSH private key and does not require opening port 22 to GitHub. The custom role can read and invoke Run Command on this VM only. It cannot start, deallocate, resize, create, or delete a VM.

### One-time Azure identity setup

In Azure Cloud Shell, update the repository, review the script, and run it:

```bash
cd ~/sentinel-deploy
git pull --ff-only
sed -n '1,260p' deployment/azure-demo/configure-github-oidc.sh

export CONFIRM_CONFIGURE_AZURE_OIDC='yes'
bash deployment/azure-demo/configure-github-oidc.sh
```

It creates or reuses:

1. a Microsoft Entra application and service principal named `sentinel-github-deployer`;
2. one federated credential for the exact repository and GitHub `azure-demo` environment;
3. a custom `Sentinel Demo Release Activator` role;
4. one assignment of that role at the exact VM resource scope.

No client secret is created. The repository was created on 17 July 2026, so GitHub's post-15-July immutable subject format applies. The audited subject includes owner ID `35369040` and repository ID `1304261078`; a rename cannot silently transfer deployment authority to a different repository.

### One-time GitHub environment setup

Go to **GitHub repository → Settings → Environments → New environment** and create exactly `azure-demo`. Under its **Environment variables**, copy the seven values printed by the script:

- `AZURE_CLIENT_ID`
- `AZURE_TENANT_ID`
- `AZURE_SUBSCRIPTION_ID`
- `AZURE_RESOURCE_GROUP`
- `AZURE_VM_NAME`
- `AZURE_DEMO_ADDRESS`
- `AZURE_DEMO_HEALTH_URL`

These are identifiers and routing configuration, not passwords. Then go to **Settings → Secrets and variables → Actions → Variables** and create:

```text
AZURE_DEPLOY_ENABLED = true
```

This variable is deliberately last. Before it exists, `verify-and-publish` succeeds and `deploy` is shown as **skipped**. After it is `true`, every `main` push runs both jobs.

### What happens on every later push

1. GitHub tests the frontend and backend.
2. It builds and publishes `ghcr.io/...:<full commit SHA>`.
3. The deploy job requests an OIDC token for the `azure-demo` environment.
4. Microsoft Entra checks the immutable subject and gives a short-lived Azure token.
5. Run Command sends `activate-release.sh` and three non-secret positional arguments to the VM agent.
6. The script validates that image tag and source SHA are identical, refuses tracked VM edits, checks out the exact commit, pulls the image, and converges Compose.
7. GitHub checks the stable public readiness URL. Only then is the deployment green.

The workflow concurrency group serializes releases. If commits A and B arrive close together, B waits; they do not race two Compose updates. `id-token: write` only lets the job request identity proof. The exact Azure role assignment decides what that proof can do.

### First automated proof

Use **Actions → Verify, publish, and deploy demo → Run workflow**, or push a small reviewed commit. Confirm both jobs are green. Then verify:

```bash
curl --fail --silent \
  https://sentinel-mofazzal874.centralindia.cloudapp.azure.com/actuator/health/readiness
```

If OIDC succeeds but Run Command says the VM is deallocated, that is expected after a cost guard or manual stop. Start it manually only after checking the budget:

```bash
az vm start --resource-group sentinel-demo-rg --name sentinel-demo-vm
```

## 18. Automated cost containment

The cost guard is intentionally a separate identity from deployment:

- GitHub identity: may activate a release, but cannot start or stop compute.
- Logic App identity: may deallocate this VM, but cannot deploy, start, resize, or delete it.

This separation prevents a later code push from undoing a budget stop.

### Connect the existing `$10` budget once

Find the exact budget name in **Cost Management → Budgets**, then in Cloud Shell:

```bash
cd ~/sentinel-deploy
git pull --ff-only
sed -n '1,340p' deployment/azure-demo/configure-cost-guard.sh

export AZURE_BUDGET_NAME='PUT YOUR EXACT EXISTING BUDGET NAME HERE'
export AZURE_COST_GUARD_THRESHOLD_PERCENT='50'
export CONFIRM_CONFIGURE_COST_GUARD='yes'

bash deployment/azure-demo/configure-cost-guard.sh
```

The script preserves the existing budget and adds a `SentinelEarlyDeallocate` notification. It creates a Consumption Logic App, Action Group, narrowly scoped custom role, and VM-scope assignment. The default 50% threshold is deliberately conservative: with a `$10` budget the signal nominally starts at `$5`, leaving room for delayed cost records. Even this cannot guarantee a final amount below `$10`.

The Logic App is a Consumption resource and an invocation can itself have a small cost. Action Group notification limits and pricing also depend on the subscription. Inspect the resulting resources in Cost Analysis rather than assuming they are free.

### Optional daily shutdown

Daily shutdown is a different control: it limits unattended runtime even if no budget event arrives. It also makes the résumé URL unavailable until you manually start the VM. To add midnight Bangladesh-time shutdown (`18:00 UTC`), set these before running the same cost script:

```bash
export AZURE_DAILY_SHUTDOWN_UTC='1800'
export AZURE_BUDGET_EMAIL='YOUR EMAIL ADDRESS'
```

Azure auto-shutdown does not auto-start the VM. Omit `AZURE_DAILY_SHUTDOWN_UTC` if continuous portfolio availability matters more than that daily cap.

### Verify without intentionally spending more

Inspect wiring rather than forcing the budget to be consumed:

```bash
az logic workflow show \
  --resource-group sentinel-demo-rg \
  --name sentinel-budget-deallocate \
  --query '{State:state,Identity:identity.principalId}' \
  --output table

az monitor action-group show \
  --resource-group sentinel-demo-rg \
  --name sentinel-budget-stop \
  --query '{Enabled:enabled,LogicAppCount:length(logicAppReceivers)}' \
  --output table

az consumption budget show \
  --budget-name "$AZURE_BUDGET_NAME" \
  --query 'notifications.SentinelEarlyDeallocate' \
  --output json
```

When the action fires, verify deallocation:

```bash
az vm get-instance-view \
  --resource-group sentinel-demo-rg \
  --name sentinel-demo-vm \
  --query "instanceView.statuses[?starts_with(code,'PowerState/')].displayStatus" \
  --output tsv
```

Expected: `VM deallocated`. `VM stopped` is not sufficient evidence; a stopped-but-allocated VM can still incur compute charges.

### Cost states worth memorizing

| State | Site | Compute | Disk/IP | Recovery |
|---|---|---:|---:|---|
| VM running | Online | Billed | Billed | None |
| VM deallocated | Offline | Not billed | May remain billed | Manual `az vm start` |
| Resource group deleted | Gone | None from group | None from group | Recreate; old data/URL are lost |

Never automate resource-group deletion for this portfolio without a separate backup and retirement decision.

## 19. Security boundaries to defend

- Only Caddy publishes ports 80/443.
- Sentinel maps only to VM loopback; dependencies remain on the private Compose network.
- PostgreSQL, Redis, RabbitMQ, and Ollama are not public.
- secrets live in `/opt/sentinel/release/.sentinel/azure-demo.env`, mode `600`, and never enter Git or the image.
- the public demo accepts only server-owned fixed scenarios.
- ordinary catalog, approval, operations, metrics, and actuator APIs remain authenticated.
- public remediation is always dry-run.
- every image is referenced by immutable commit SHA.
- GitHub build permission and Azure deployment permission are separate.
- OIDC should be constrained to the exact repository/environment and VM scope.

## 20. Three-level explanation

### Locally

GitHub turns one commit into one tested image. Azure pulls that exact image and Compose replaces the application while preserving named data volumes.

### System design

Stable service identity is separated from replaceable software identity. CI proves an artifact; CD promotes it. Caddy owns the edge, Compose isolates dependencies, Flyway owns schema evolution, and readiness decides whether the release can serve traffic.

### Interview defense

“I deployed a Spring Boot/Next.js incident-response control plane to an isolated Azure VM. GitHub Actions tests both stacks, publishes commit-SHA images to GHCR, exchanges repository-bound OIDC for a short-lived Azure token, and activates the exact release through VM Run Command without exposing SSH. A separate least-privilege budget action deallocates compute early, while immutable tags, forward-only migrations, readiness checks, and durable volumes make delivery explainable and recoverable.”

## 21. Beginner questions, answers, and scenarios

### Why was `deploy` skipped even though the workflow was green?

The job had `if: vars.AZURE_DEPLOY_ENABLED == 'true'`. GitHub correctly completed CI and skipped CD because the opt-in variable did not exist. This was a safety switch, not a runner failure. After the one-time OIDC variables exist, set the switch to `true` and the same workflow will deploy every green `main` commit.

### Does automated deployment mean every edit instantly reaches production?

No. Only a commit pushed to `main` triggers this workflow. It must pass frontend and backend checks, publish an image, authenticate to Azure, activate the image, and pass public readiness. A failing test stops before Azure. A failed readiness check leaves the run red and visible even if Compose attempted the update.

Example: commit A has a TypeScript error. `npm run typecheck` fails, so no A image is published and the VM remains on the previous healthy SHA.

### Why use both a Git SHA and a container image tag?

The SHA connects source, build, and runtime evidence. `activate-release.sh` rejects an image whose tag differs from the requested source SHA. Without that check, the VM could display source A while actually running image B, making debugging and résumé claims unreliable.

### Why OIDC instead of storing an Azure password in GitHub?

OIDC creates a short-lived token only when the exact repository/environment workflow runs. There is no reusable client secret to leak, rotate, or forget. The token still has minimal authority because Azure RBAC permits only Run Command on one VM.

### Could a malicious pull request deploy itself?

Not through this workflow. Deployment triggers on `main`, uses the `azure-demo` environment identity, and requires the exact immutable repository subject. Protect `main` with pull-request review and branch protection for a stronger human gate. Never add a `pull_request_target` deployment path for untrusted code.

### What if two pushes happen within one minute?

The workflow uses one `azure-demo` concurrency group with `cancel-in-progress: false`. The first release finishes and the second waits. This avoids simultaneous Git checkouts and Compose updates on one VM. The later successful SHA becomes active.

### What happens to PostgreSQL data during deployment?

Compose replaces containers, not named volumes. PostgreSQL data, message state, model cache, and Caddy certificates persist. Flyway applies forward-only migrations at startup. An image rollback cannot blindly reverse a database migration, so schema changes must remain backward compatible across the rollback window.

### Why can the deployment identity not start the VM?

Because a budget stop must dominate delivery. If GitHub also had `start/action`, a routine push after budget deallocation could restart compute billing. Recovery is a conscious owner action after checking Cost Analysis.

### Does the `$10` budget guarantee a maximum charge of `$10`?

No. Azure explicitly says budgets do not stop consumption. Cost data can arrive 8–24 hours later, so even automation at 100% can overshoot. A 50% action creates safety margin but still cannot mathematically guarantee the final bill.

Scenario: the VM costs accrue today, but the cost record reaches Cost Management tomorrow. The recorded total may jump from `$4.80` to `$8.20`; the guard then deallocates. The threshold worked, but not in real time.

### Why not automatically delete the resource group at the limit?

Deletion would stop the disk/IP lifecycle too, but destroys the database and stable DNS resource. Delayed cost records still mean it cannot guarantee an exact cap. For a résumé demo, early deallocation is reversible; group deletion is an explicit retirement operation.

### What still costs money after deallocation?

Compute allocation stops, but the OS disk and retained Standard static Public IP may still be billed. The Logic App can also have per-execution cost. Check the subscription's current pricing and Cost Analysis. “VM deallocated” means “compute stopped,” not “account guaranteed at zero.”

### Should I enable daily shutdown?

Enable it for a classroom or occasional demo where predictable offline hours are acceptable. Leave it off for a résumé link expected to work at any hour. A practical alternative is manually start it before recruiting activity and deallocate after, but that sacrifices always-on availability.

### What should I inspect when a deployment fails?

Read the first failing boundary in order: CI test, GHCR publication, OIDC login, Run Command, VM-side Compose, then public readiness. Do not randomly recreate resources. For VM-side errors, use Run Command to inspect `docker compose ps` and bounded logs; for OIDC errors, compare environment name and the immutable federated subject.

### What does an FDE or DevOps engineer learn from this design?

Delivery is a chain of evidence and permissions, not a single shell command. The artifact is immutable, identity is short-lived, RBAC is resource-scoped, releases are serialized, readiness is externally verified, database evolution is forward-only, and cost automation has documented latency and blast radius. Those decisions remain useful when moving from one VM to Container Apps or Kubernetes.

## 22. Pen-and-paper exercises

1. Draw the four execution locations: local PC, GitHub runner, Cloud Shell, and Azure VM.
2. Explain why a green image publication is not proof that Azure changed.
3. Circle which resource owns the stable URL and which identifier owns the software version.
4. Mark every persistent volume and predict what happens during `docker compose up`.
5. Explain why Redis efficiency loss does not permit duplicate incidents in PostgreSQL.
6. Compare SSH secrets with OIDC short-lived federation.
7. Explain why a rollback cannot blindly reverse a Flyway migration.
8. List what continues to cost money after VM deallocation.

## 23. Official references

- [Azure CLI: VM Run Command](https://learn.microsoft.com/en-us/cli/azure/vm/run-command)
- [Azure Run Command overview](https://learn.microsoft.com/en-us/azure/virtual-machines/run-command-overview)
- [Azure CLI: VM auto-shutdown](https://learn.microsoft.com/en-us/cli/azure/vm#az-vm-auto-shutdown)
- [Azure budgets and delayed cost data](https://learn.microsoft.com/en-us/azure/cost-management-billing/costs/tutorial-acm-create-budgets)
- [Azure budget action automation scenario](https://learn.microsoft.com/en-us/azure/cost-management-billing/manage/cost-management-budget-scenario)
- [Azure Logic Apps managed identity authentication](https://learn.microsoft.com/en-us/azure/logic-apps/authenticate-with-managed-identity)
- [Azure Consumption Budget REST API](https://learn.microsoft.com/en-us/rest/api/consumption/budgets/get?view=rest-consumption-2024-08-01)
- [Azure public IP addresses and DNS labels](https://learn.microsoft.com/en-us/azure/virtual-network/ip-services/public-ip-addresses)
- [Azure cloud-init for Linux VMs](https://learn.microsoft.com/en-us/azure/virtual-machines/linux/using-cloud-init)
- [GitHub: configuring OIDC in Azure](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-azure)
- [GitHub deployment environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments)
- [GitHub Actions secrets](https://docs.github.com/en/actions/reference/security/secrets)
- [GitHub: publish Docker images](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images)
- [Docker Engine on Ubuntu](https://docs.docker.com/engine/install/ubuntu/)
- [Docker Compose startup order](https://docs.docker.com/compose/how-tos/startup-order/)
- [Caddy automatic HTTPS](https://caddyserver.com/docs/automatic-https)
