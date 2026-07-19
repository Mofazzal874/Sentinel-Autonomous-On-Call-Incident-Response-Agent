#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
environment_file="$repo_root/.sentinel/azure-demo.env"
base_compose="$repo_root/deployment/azure-demo/compose.yaml"
azure_compose="$repo_root/deployment/azure-demo/compose.azure.yaml"

[[ -f "$environment_file" ]] || "$repo_root/deployment/azure-demo/new-env.sh"
sentinel_image="${SENTINEL_IMAGE:-sentinel:azure-demo}"
docker image inspect "$sentinel_image" >/dev/null 2>&1 || {
  echo "$sentinel_image is not loaded. Pull it from GHCR or load the exported local image first." >&2
  exit 1
}

docker compose --project-name sentinel-azure-demo --env-file "$environment_file" --file "$base_compose" --file "$azure_compose" up --detach --wait

for _ in $(seq 1 90); do
  if curl --fail --silent http://127.0.0.1:8080/actuator/health/readiness >/dev/null; then
    echo 'Azure demo stack is ready on VM loopback port 8080.'
    exit 0
  fi
  sleep 2
done

docker compose --project-name sentinel-azure-demo --env-file "$environment_file" --file "$base_compose" --file "$azure_compose" logs --tail 120 sentinel
echo 'Sentinel did not become ready within 180 seconds.' >&2
exit 1
