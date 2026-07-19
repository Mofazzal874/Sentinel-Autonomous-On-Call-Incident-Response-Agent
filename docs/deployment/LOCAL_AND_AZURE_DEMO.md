# Local Rehearsal, Stable Demo URL, and Automated Delivery

This is the deployment runbook. Local rehearsal is safe to run now. Azure creation remains a separate, explicit action.

## What the bundle deploys

```text
Internet
   |
static Azure IP + stable DNS/custom domain
   |
Caddy :80/:443 -- public landing page
   |
Sentinel :8080 -- loopback host mapping, private Docker network
   |------------ PostgreSQL 17 + pgvector
   |------------ Redis 7
   |------------ RabbitMQ 4
   `------------ Ollama (Qwen3 4B + nomic-embed-text)
```

Only Caddy is public in Azure. PostgreSQL, Redis, RabbitMQ, and Ollama have no host ports. Sentinel stays in dry-run mode. Health probes reveal only status, while metrics and business APIs retain JWT protection.

## Check the deployment locally first

Prerequisites are audited by the script: the existing E-drive Java 25, E-drive Gradle cache, Docker Engine, Ollama API, Qwen3 4B, and nomic embeddings. It does not reinstall or pull those models.

From PowerShell in the repository root:

```powershell
.\deployment\azure-demo\test-local.ps1
```

The script creates strong secrets only in ignored `.sentinel/azure-demo-local.env`, builds the JAR/image, starts a separately named Compose project, and proves:

- readiness and liveness return HTTP 200;
- anonymous Prometheus returns HTTP 401;
- Flyway and Hibernate startup completes;
- the semantic startup job writes exactly three 768-dimension runbook embeddings.

Open `http://127.0.0.1:18080/actuator/health/readiness`. This local stack intentionally omits the Azure-only Caddy/Ollama containers and reuses Windows Ollama through `host.docker.internal`, avoiding a duplicate 2.8 GB model copy.

Stop only the rehearsal containers and network:

```powershell
.\deployment\azure-demo\stop-local.ps1
```

To also delete only this rehearsal's PostgreSQL/Redis/RabbitMQ volumes:

```powershell
.\deployment\azure-demo\stop-local.ps1 -DeleteData
```

`-DeleteData` is destructive for the rehearsal database but cannot target the ordinary `sentinel` Compose project or another project.

## Why the résumé URL does not change

The URL belongs to the Azure Public IP resource, not to a container. Use a Standard static IP and attach one Azure DNS label, producing a hostname such as `sentinel-mofazzal.centralindia.cloudapp.azure.com`. Replacing the app container does not replace that resource.

For a durable HTTPS résumé link, register or reuse a domain you control and add an A record such as `sentinel.example.com` pointing to the static IP. Set `SENTINEL_DEMO_ADDRESS=sentinel.example.com`; Caddy then obtains and renews TLS. Without a custom domain, set `SENTINEL_DEMO_ADDRESS=http://<label>.centralindia.cloudapp.azure.com` and use the stable Azure HTTP hostname until a domain is available.

Do not delete the Public IP resource during normal updates. Deallocating the VM preserves the resource and link identity but makes the site unavailable. Deleting the whole resource group deletes the link.

## Provision only after final approval

In Azure Cloud Shell, clone the public repository and enter it. Determine the public IPv4 address of the computer that will SSH to the VM; this is not necessarily the Cloud Shell address. Then review and run:

```bash
export AZURE_DEMO_DNS_LABEL='choose-a-unique-lowercase-label'
export AZURE_SSH_SOURCE_IP='YOUR.HOME.PUBLIC.IP'
export CONFIRM_CREATE_AZURE_RESOURCES=yes
bash deployment/azure-demo/provision-azure.sh
```

The script deliberately refuses to create anything without the confirmation value. It creates only the dedicated resource group, VNet/subnet, NSG, static IP/DNS, NIC, and non-zonal `Standard_B4as_v2` VM. SSH is allowed only from the supplied `/32`; ports 80 and 443 are public. It uses Ubuntu 24.04, a 64 GB Standard SSD, and the reviewed Docker bootstrap. No ACR, managed database, AKS, Azure OpenAI, or unrelated project resource is created.

Wait for bootstrap and reconnect so Docker group membership applies:

```bash
az vm run-command invoke --resource-group sentinel-demo-rg --name sentinel-demo-vm \
  --command-id RunShellScript --scripts 'cloud-init status --wait'
ssh azureuser@YOUR_AZURE_HOSTNAME
docker version
docker compose version
```

On the VM, create the ignored secret file once:

```bash
cd /opt/sentinel/release
export SENTINEL_DEMO_ADDRESS='http://YOUR_AZURE_HOSTNAME'
bash deployment/azure-demo/new-env.sh
```

Use a bare custom domain instead of the `http://` value when DNS already points at the VM and HTTPS is desired.

## GitHub CI/CD setup

`.github/workflows/deploy-azure-demo.yml` does two jobs:

1. Every push to `main` runs the full regression suite, builds the application, and publishes `ghcr.io/<owner>/<repository>:<commit-sha>` plus the convenience `:main` tag.
2. When deployment is enabled, it copies only the versioned deployment bundle, pulls the immutable SHA image on the existing VM, recreates changed containers, and verifies the same stable readiness URL.

After the first package publish, make the GHCR container package public. Public read access avoids storing a registry token on the VM. Then create a GitHub environment named `azure-demo` and configure:

Repository/environment secrets:

- `AZURE_VM_HOST`: stable Azure hostname or custom domain used for SSH.
- `AZURE_VM_USER`: `azureuser`.
- `AZURE_VM_SSH_KEY`: the complete private key generated for this VM.
- `AZURE_VM_KNOWN_HOSTS`: the already-verified SSH host-key line; capture it after the first trusted manual SSH connection, not blindly during each workflow.

Repository variables:

- `AZURE_DEPLOY_ENABLED`: keep `false` until the manual deployment is healthy, then set `true`.
- `AZURE_DEMO_HEALTH_URL`: `http://<azure-host>/actuator/health/readiness` or the custom HTTPS equivalent.

Use environment approval protection if the repository plan supports it. A failed test never publishes or deploys. A failed deployment does not change DNS. Roll back by manually running the prior workflow/commit image tag on the VM; never use the moving `main` tag as rollback evidence.

## Budget and lifecycle

The `$10` budget is a warning threshold, not a hard spending cap. With `$100` student credit, keep alerts at several levels (for example 10%, 50%, 80%, and 95%), inspect Cost Analysis daily during the demo window, and deallocate compute when continuous availability is not required. A résumé link and a deallocated VM are a direct tradeoff: the name stays stable, but the service is offline.

Never delete individual unknown resources to save space or money. This demo is intentionally isolated in `sentinel-demo-rg`, so `az resource list --resource-group sentinel-demo-rg --output table` shows its full blast radius. Deleting that resource group is the final teardown and destroys its disks, data, IP, and Azure hostname.

## System-design defense

Locally, Compose proves that configuration, migrations, networking, model connectivity, and security compose correctly. At system level, the static edge identity is separated from the replaceable application artifact. In an interview: “A commit SHA names the exact software bytes; the static public IP and DNS name identify the service. CI tests and promotes bytes, while deployment updates the VM behind the same identity.”

Official references:

- [GitHub: publishing Docker images to GHCR](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images)
- [Azure: public IP addresses, static allocation, and DNS labels](https://learn.microsoft.com/en-us/azure/virtual-network/ip-services/public-ip-addresses)
- [Azure: create an FQDN for a Linux VM](https://learn.microsoft.com/en-us/azure/virtual-machines/create-fqdn)
- [Azure: cloud-init for Linux VMs](https://learn.microsoft.com/en-us/azure/virtual-machines/linux/using-cloud-init)
- [Docker: install Engine on Ubuntu](https://docs.docker.com/engine/install/ubuntu/)
- [Docker Compose: dependency startup order](https://docs.docker.com/compose/how-tos/startup-order/)
- [Ollama: Docker deployment](https://docs.ollama.com/docker)
- [Caddy: automatic HTTPS](https://caddyserver.com/docs/automatic-https)
