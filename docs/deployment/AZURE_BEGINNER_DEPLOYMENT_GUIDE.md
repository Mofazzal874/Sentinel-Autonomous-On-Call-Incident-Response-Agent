# Sentinel on Azure: beginner deployment and CI/CD guide

This is the start-to-finish learning and operations guide for the portfolio deployment. It records the deployment that was actually built, the commands used at each boundary, how to update the existing site safely, and what remains before deployment is fully automatic.

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
- CD: not yet automatic. The workflow's Azure job is disabled and still uses the legacy SSH design.

The site can therefore be older than the latest Git commit. A green CI run means “verified image published,” not “Azure updated.”

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

The user created a `$10` Azure budget alert while retaining approximately `$100` of student credit. This is useful, but an Azure budget is an alert—not an automatic spending stop.

Before and after deployment:

```bash
az resource list --resource-group sentinel-demo-rg --output table
```

Use Azure Portal **Cost Management + Billing → Budgets** and **Cost analysis** to inspect actual spend. Keep alerts at several percentages. Deallocate the VM when availability is not needed; do not assume the budget will stop it.

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

This is the current safe release method until OIDC CD is implemented.

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

Create a temporary VM script. The unquoted `SCRIPT` marker intentionally substitutes the two Cloud Shell variables now; no secret is included:

```bash
cat > /tmp/sentinel-release.sh <<SCRIPT
set -eu

release_sha='${RELEASE_SHA}'
image='${RELEASE_IMAGE}'
repo='https://github.com/Mofazzal874/Sentinel-Autonomous-On-Call-Incident-Response-Agent.git'
release_dir='/opt/sentinel/release'

install -d -o azureuser -g azureuser "\$release_dir"

if [ -d "\$release_dir/.git" ]; then
  sudo -u azureuser git -C "\$release_dir" diff --quiet
  sudo -u azureuser git -C "\$release_dir" diff --cached --quiet
  sudo -u azureuser git -C "\$release_dir" fetch --prune origin
else
  rmdir "\$release_dir"
  sudo -u azureuser git clone "\$repo" "\$release_dir"
fi

sudo -u azureuser git -C "\$release_dir" checkout --detach "\$release_sha"

export SENTINEL_DEMO_ADDRESS='sentinel-mofazzal874.centralindia.cloudapp.azure.com'
bash "\$release_dir/deployment/azure-demo/new-env.sh"

docker pull "\$image"
SENTINEL_IMAGE="\$image" bash "\$release_dir/deployment/azure-demo/start-azure.sh"
SCRIPT
```

Review exactly what Azure will run:

```bash
sed -n '1,240p' /tmp/sentinel-release.sh
```

Run it through the VM agent:

```bash
az vm run-command invoke \
  --resource-group sentinel-demo-rg \
  --name sentinel-demo-vm \
  --command-id RunShellScript \
  --scripts @/tmp/sentinel-release.sh \
  --query 'value[0].message' \
  --output tsv
```

Remove only the temporary Cloud Shell script:

```bash
rm /tmp/sentinel-release.sh
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

## 17. Current CI/CD gap

The workflow's `verify-and-publish` job is valid CI. Its current `deploy` job is opt-in and SSH-based:

- `AZURE_DEPLOY_ENABLED` is not `true`, so the job is skipped.
- the NSG allows port 22 only from the user's recorded `/32`;
- GitHub-hosted runner IPs are not that `/32` and are not stable enough to add broadly;
- therefore enabling the existing job would not create hassle-free delivery.

Do not solve this by opening SSH to the entire internet or continually adding GitHub runner IP ranges.

## 18. Target CD with Azure OIDC and VM Run Command

The target design is:

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

This removes the long-lived SSH private key and does not require opening port 22 to GitHub.

One-time OIDC work still to implement and verify:

1. create a Microsoft Entra application and service principal dedicated to this repository;
2. add a federated credential restricted to the repository's `azure-demo` GitHub environment;
3. grant only the VM Run Command permissions at the `sentinel-demo-vm` scope (built-in Virtual Machine Contributor is the initial bounded option; a smaller custom role is preferable after rehearsal);
4. create the GitHub `azure-demo` environment and restrict deployment to `main`;
5. add `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, and `AZURE_SUBSCRIPTION_ID` as environment configuration;
6. add workflow `id-token: write` and a pinned `azure/login` action;
7. replace `scp`/`ssh` steps with an `az vm run-command invoke` release;
8. keep `AZURE_DEPLOY_ENABLED=false` until a manual release and rollback are healthy;
9. enable it and observe one complete automated deployment;
10. remove obsolete SSH deployment secrets only after OIDC succeeds.

Do not blindly paste a federated subject string. GitHub changed the default OIDC subject behavior for repositories created, renamed, or transferred after 15 July 2026. The repository has also been renamed. The implementation must inspect the repository's actual token claims or use the current Azure/GitHub environment wizard, then bind the exact immutable identity.

The expected workflow shape is:

```yaml
permissions:
  contents: read
  packages: write
  id-token: write

jobs:
  deploy:
    environment: azure-demo
    steps:
      - uses: azure/login@<pinned-commit-sha>
        with:
          client-id: ${{ secrets.AZURE_CLIENT_ID }}
          tenant-id: ${{ secrets.AZURE_TENANT_ID }}
          subscription-id: ${{ secrets.AZURE_SUBSCRIPTION_ID }}
      - run: az vm run-command invoke ...
```

`id-token: write` permits requesting an OIDC identity token; it does not by itself grant Azure mutation authority. Azure grants only the scope assigned to the federated service principal.

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

“I deployed a Spring Boot/Next.js incident-response control plane to an isolated Azure VM. GitHub Actions builds and tests both stacks, publishes commit-SHA images to GHCR, and the release converges a private Compose topology behind Caddy TLS without changing the public DNS. The demo is intentionally single-node and dry-run. I use durable volumes, forward-only migrations, readiness verification, immutable rollback tags, bounded public workloads, and I am replacing the initial manual/SSH release with least-privilege OIDC plus Azure VM Run Command.”

## 21. Pen-and-paper exercises

1. Draw the four execution locations: local PC, GitHub runner, Cloud Shell, and Azure VM.
2. Explain why a green image publication is not proof that Azure changed.
3. Circle which resource owns the stable URL and which identifier owns the software version.
4. Mark every persistent volume and predict what happens during `docker compose up`.
5. Explain why Redis efficiency loss does not permit duplicate incidents in PostgreSQL.
6. Compare SSH secrets with OIDC short-lived federation.
7. Explain why a rollback cannot blindly reverse a Flyway migration.
8. List what continues to cost money after VM deallocation.

## 22. Official references

- [Azure CLI: VM Run Command](https://learn.microsoft.com/en-us/cli/azure/vm/run-command)
- [Azure Run Command overview](https://learn.microsoft.com/en-us/azure/virtual-machines/run-command-overview)
- [Azure public IP addresses and DNS labels](https://learn.microsoft.com/en-us/azure/virtual-network/ip-services/public-ip-addresses)
- [Azure cloud-init for Linux VMs](https://learn.microsoft.com/en-us/azure/virtual-machines/linux/using-cloud-init)
- [GitHub: configuring OIDC in Azure](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-azure)
- [GitHub deployment environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments)
- [GitHub Actions secrets](https://docs.github.com/en/actions/reference/security/secrets)
- [GitHub: publish Docker images](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images)
- [Docker Engine on Ubuntu](https://docs.docker.com/engine/install/ubuntu/)
- [Docker Compose startup order](https://docs.docker.com/compose/how-tos/startup-order/)
- [Caddy automatic HTTPS](https://caddyserver.com/docs/automatic-https)

