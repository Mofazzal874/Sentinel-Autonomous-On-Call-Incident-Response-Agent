# Deployment Readiness and Fastest Safe Route

## Verified local artifact

- The deployment-specific `sentinel:azure-demo` image ID is `9fc9da4697728200b59c4a28a696494cd23cf239c9ccebd09ec4cf82218370f7`, 200,462,107 bytes, and runs as `10001:10001`.
- The isolated full topology reached readiness/liveness `200`, kept Prometheus at `401`, and indexed all three runbooks through the real host Ollama embedding model. The Caddy landing/proxy edge also returned `200`.
- The final uncached regression passed 103 tests across 35 suites with zero failures, errors, or skips.
- Two clean `bootJar --no-build-cache` runs produced the same SHA-256: `5A1B87B51234FF465F21E3CA816A358DEF6A120CACB10F4612F6B03870F7DD61`.
- Final `sentinel:local` image ID is `fdbfe1198c88759141f2d45b9771386a0e48016f27e8d74d2036779f25322295`, 200,461,161 bytes, and runs as `10001:10001` with a read-only root filesystem.
- A real container reached both liveness and readiness against the isolated Compose PostgreSQL/pgvector, Redis, and RabbitMQ services.
- Anonymous Prometheus access returned `401`; only status-only platform probes are public.
- The scratch smoke container was stopped and automatically removed. Docker application/image data remains on `E:`.

## Can this be online tonight or tomorrow?

Yes, conditionally. The fastest honest demo is one Azure Linux VM running the already verified container plus the pinned Compose dependencies. It preserves the current topology and avoids rewriting RabbitMQ for a different broker hours before a demo. Restrict inbound access to the user's IP and use an SSH tunnel initially; a genuinely public demo also needs TLS, a hostname, secret rotation, and webhook ingress controls.

This route is a demo topology, not the final highly available architecture. PostgreSQL, Redis, RabbitMQ, and the app share one failure domain. Back up the Docker volumes and delete/deallocate the VM when the demo window ends to control cost.

A production-shaped Azure route is slower: Container Apps for Sentinel, PostgreSQL Flexible Server 17 with `vector` allowlisted, Azure Managed Redis, and a deliberate RabbitMQ decision (persistent self-hosted container or managed third party). Azure Container Apps can deploy an existing image quickly, but wiring private networking, persistent broker storage, secrets, probes, and every managed dependency is the real work.

AKS is not recommended for this deadline. It adds cluster, ingress, identity, storage-class, upgrade, and cost work without improving the portfolio demonstration enough to justify it.

## Current blockers to an actual deployment

1. Azure for Students is active with about `$100` remaining; Central India has six regional vCPUs and four Standard BS-family vCPUs. `Standard_B4as_v2` is available for a non-zonal deployment, while zone 3 is restricted and `Standard_B4ms` is unavailable.
2. The user created a `$10` budget alert. It is useful but does not cap or stop spending.
3. No registry package, VM, resource group, DNS record, or public endpoint may be created until the user explicitly approves the reviewed bundle.
4. The stable hostname label must be selected. The resulting Azure FQDN can receive automatic HTTPS through Caddy; a custom domain is optional branding.
5. GitHub deployment secrets and the opt-in repository variable remain intentionally unset.
6. CPU-only grounded triage took about 100 seconds locally. The VM demo must accept that latency or later adopt a separately implemented and approved accelerated/managed provider.

## Required secret/configuration set

- Database: `SENTINEL_DB_URL`, `SENTINEL_DB_USERNAME`, `SENTINEL_DB_PASSWORD`.
- Redis: `SENTINEL_REDIS_HOST`, `SENTINEL_REDIS_PORT` and future TLS/auth settings for a managed service.
- RabbitMQ: host, port, username, password; production also needs TLS configuration.
- Identity: random base64 `SENTINEL_JWT_SECRET`, issuer, audience. The HS256 local implementation is acceptable for the demo only; production should use an external issuer and asymmetric JWK validation.
- Alert intake: independent random base64 `SENTINEL_WEBHOOK_SECRET`.
- Safety: leave `SENTINEL_REMEDIATION_DRY_RUN=true` for deployment verification.
- Agent: the deployment enables it only after the one-shot Ollama model initialization completes; an idempotent startup runner creates and verifies all runbook embeddings before readiness.

Never pass secrets as Docker build arguments or bake them into the image. Use the platform secret store or a permission-restricted environment file on the demo VM.

## Migration, rollback, and recovery

Flyway runs forward-only migrations before Hibernate validation. Back up PostgreSQL before deploying a new migration. Roll back application code by redeploying the prior immutable image only when that image is schema-compatible; never undo a committed migration by editing it. A failed migration stops startup rather than letting Hibernate change the schema.

For uncertain remediation, inspect `action_claim` and the append-only ledger; never delete or replay a claim to make a demo pass. Keep dry-run enabled. RabbitMQ redelivery is safe through database idempotency, and Redis loss may reduce suppression efficiency but does not remove the database correctness boundary.

## Cost controls

- Put all demo resources in one dedicated resource group so the complete environment can be enumerated and removed.
- Apply a subscription budget alert before provisioning; budgets warn but do not automatically stop resources.
- Use one fixed small VM only after checking that 16 GB RAM is sufficient for the app, infrastructure, and 4B model; deallocate it outside the demo.
- Do not create legacy Azure Cache for Redis. Microsoft recommends Azure Managed Redis for new work.
- Do not enable paid Azure OpenAI until model availability, quota, region, and a hard evaluation call budget are confirmed.

## Official references

- [Azure CLI Linux VM creation](https://learn.microsoft.com/en-us/azure/virtual-machines/linux/create-cli-complete)
- [Deploy an existing image to Azure Container Apps](https://learn.microsoft.com/en-us/azure/container-apps/get-started-existing-container-image-portal)
- [Azure Container Apps health probes](https://learn.microsoft.com/en-ca/azure/container-apps/health-probes)
- [PostgreSQL Flexible Server pgvector](https://learn.microsoft.com/en-us/azure/postgresql/extensions/how-to-use-pgvector)
- [Azure Cache for Redis retirement and migration direction](https://learn.microsoft.com/en-us/azure/azure-cache-for-redis/cache-migration-guide)
- [Create and deploy an Azure OpenAI resource/model](https://learn.microsoft.com/en-us/azure/ai-services/openai/how-to/create-resource)
