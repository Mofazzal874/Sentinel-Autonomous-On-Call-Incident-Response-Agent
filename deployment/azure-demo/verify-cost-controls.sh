#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

for required_command in bash docker awk grep sed; do
  command -v "$required_command" >/dev/null 2>&1 || {
    echo "Required verification command is missing: $required_command" >&2
    exit 2
  }
done

for script in deployment/azure-demo/*.sh; do
  bash -n "$script"
done

export SENTINEL_DB_NAME=sentinel
export SENTINEL_DB_USERNAME=sentinel
export SENTINEL_DB_PASSWORD=verification-only
export SENTINEL_RABBITMQ_USERNAME=sentinel
export SENTINEL_RABBITMQ_PASSWORD=verification-only
export SENTINEL_JWT_SECRET=verification-only
export SENTINEL_WEBHOOK_SECRET=verification-only
export SENTINEL_OLLAMA_BASE_URL=http://ollama:11434
export SENTINEL_DEMO_ADDRESS=example.test
export SENTINEL_IMAGE=sentinel:verification

working_directory="$(mktemp -d)"
trap 'rm -rf "$working_directory"' EXIT

docker compose \
  --file deployment/azure-demo/compose.yaml \
  --file deployment/azure-demo/compose.azure.yaml \
  config \
  --format json > "$working_directory/compose.json"

memory_limit_bytes="$(
  grep '"mem_limit":' "$working_directory/compose.json" |
    sed -E 's/[^0-9]*([0-9]+).*/\1/' |
    awk '{total += $1} END {print total + 0}'
)"
maximum_memory_bytes="$((7 * 1024 * 1024 * 1024))"
if (( memory_limit_bytes > maximum_memory_bytes )); then
  echo "Container memory ceilings total $memory_limit_bytes bytes; the B2as v2 safety ceiling is $maximum_memory_bytes." >&2
  exit 3
fi

service_count="$(grep -c '"mem_limit":' "$working_directory/compose.json")"
[[ "$(grep -c '"driver": "json-file"' "$working_directory/compose.json")" -ge "$service_count" ]]
[[ "$(grep -c '"max-size": "10m"' "$working_directory/compose.json")" -ge "$service_count" ]]
[[ "$(grep -c '"max-file": "3"' "$working_directory/compose.json")" -ge "$service_count" ]]

grep -q '"OLLAMA_MAX_LOADED_MODELS": "1"' "$working_directory/compose.json"
grep -q '"OLLAMA_NUM_PARALLEL": "1"' "$working_directory/compose.json"
grep -q '"OLLAMA_CONTEXT_LENGTH": "4096"' "$working_directory/compose.json"

grep -q -- '--size Standard_B2as_v2' deployment/azure-demo/provision-azure.sh
grep -q 'steps.power.outputs.activate' .github/workflows/deploy-azure-demo.yml
if grep -qE 'az vm (start|restart)' .github/workflows/deploy-azure-demo.yml; then
  echo 'The GitHub deployment workflow must never start a cost-stopped VM.' >&2
  exit 4
fi
grep -q "cron: '30 20 \\* \\* \\*'" .github/workflows/monitor-azure-demo-cost.yml
grep -q '"Standard_B2as_v2"' .github/workflows/monitor-azure-demo-cost.yml
grep -q '"VM deallocated"' .github/workflows/monitor-azure-demo-cost.yml
if grep -qE 'az vm (start|restart|deallocate|stop|resize)' .github/workflows/monitor-azure-demo-cost.yml; then
  echo 'The nightly cost-state monitor must remain read-only.' >&2
  exit 4
fi

grep -q '"Microsoft.Compute/virtualMachines/start/action"' deployment/azure-demo/configure-on-demand-session.sh
grep -q '"Microsoft.Compute/virtualMachines/deallocate/action"' deployment/azure-demo/configure-on-demand-session.sh
grep -q 'timeout 30s az rest' deployment/azure-demo/audit-runtime-and-cost.sh
if grep -q 'az logic workflow show' deployment/azure-demo/audit-runtime-and-cost.sh; then
  echo 'The audit must use bounded REST reads instead of the CLI workflow helper.' >&2
  exit 4
fi
grep -q 'Microsoft.CostManagement 2025-03-01' deployment/azure-demo/configure-cost-guard.sh
grep -q 'Microsoft.Consumption 2024-08-01' deployment/azure-demo/configure-cost-guard.sh
grep -q 'budget_api_version=' deployment/azure-demo/configure-cost-guard.sh
grep -q 'scope_matches_before=' deployment/azure-demo/configure-cost-guard.sh
grep -q 'returned_scope_id=' deployment/azure-demo/configure-cost-guard.sh
grep -q 'matched_scope_ids=' deployment/azure-demo/configure-cost-guard.sh
grep -q 'budget_id="$candidate_id"' deployment/azure-demo/configure-cost-guard.sh
if grep -q 'budget_id="$returned_id"' deployment/azure-demo/configure-cost-guard.sh; then
  echo 'Budget updates must keep the probed provider ID paired with its API version.' >&2
  exit 4
fi
grep -q 'budget_matches > 1' deployment/azure-demo/configure-cost-guard.sh
grep -q 'eTag: .eTag' deployment/azure-demo/configure-cost-guard.sh
grep -q 'CONFIRM_CREATE_DEDICATED_BUDGET' deployment/azure-demo/configure-cost-guard.sh
grep -q 'sentinel-demo-rg-budget' deployment/azure-demo/configure-cost-guard.sh
grep -q 'amount: 10' deployment/azure-demo/configure-cost-guard.sh
grep -q -- '--fill-principal-name false' deployment/azure-demo/configure-cost-guard.sh
grep -q -- '--fill-role-definition-name false' deployment/azure-demo/configure-cost-guard.sh
grep -q 'Budget exists, but its early-deallocation notification did not pass strict verification' deployment/azure-demo/configure-cost-guard.sh
grep -q 'dedicated-budget.json' deployment/azure-demo/audit-runtime-and-cost.sh

if CONFIRM_CONFIGURE_ON_DEMAND_SESSION=no \
  bash deployment/azure-demo/configure-on-demand-session.sh \
  > "$working_directory/refusal.out" \
  2>&1; then
  echo 'The on-demand bootstrap did not enforce explicit confirmation.' >&2
  exit 5
fi
grep -q 'No VM or Azure workflow changed' "$working_directory/refusal.out"

projected_daily_cost="$(awk 'BEGIN {printf "%.4f", (5.28 / 30) + (0.005 * 24) + (0.0492 * 2)}')"
awk -v projected="$projected_daily_cost" 'BEGIN {exit !(projected <= 0.50)}'

echo "Cost-control verification passed."
echo "Container memory ceiling: $memory_limit_bytes bytes (maximum $maximum_memory_bytes)."
echo "Modeled B2as v2 two-hour daily cost: \$$projected_daily_cost."
