#!/usr/bin/env bash
set -euo pipefail

for required_command in az jq curl; do
  command -v "$required_command" >/dev/null 2>&1 || {
    echo "Required command is missing: $required_command" >&2
    exit 2
  }
done

resource_group="${AZURE_RESOURCE_GROUP:-sentinel-demo-rg}"
vm_name="${AZURE_VM_NAME:-sentinel-demo-vm}"
target_daily_cost="${AZURE_DAILY_COST_TARGET_USD:-0.50}"
days="${AZURE_COST_LOOKBACK_DAYS:-7}"

case "$days" in
  ''|*[!0-9]*) echo 'AZURE_COST_LOOKBACK_DAYS must be an integer from 1 through 31.' >&2; exit 2 ;;
esac
if (( days < 1 || days > 31 )); then
  echo 'AZURE_COST_LOOKBACK_DAYS must be from 1 through 31.' >&2
  exit 2
fi

subscription_id="$(az account show --query id --output tsv)"
resource_group_id="$(az group show --name "$resource_group" --query id --output tsv)"
vm_id="$(az vm show --resource-group "$resource_group" --name "$vm_name" --query id --output tsv)"
vm_size="$(az vm show --resource-group "$resource_group" --name "$vm_name" --query hardwareProfile.vmSize --output tsv)"
power_state="$(az vm get-instance-view \
  --resource-group "$resource_group" \
  --name "$vm_name" \
  --query "instanceView.statuses[?starts_with(code,'PowerState/')].displayStatus | [0]" \
  --output tsv)"
disk_id="$(az vm show --resource-group "$resource_group" --name "$vm_name" --query storageProfile.osDisk.managedDisk.id --output tsv)"

echo 'Sentinel Azure FinOps snapshot'
echo '=============================='
echo "Captured UTC: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "Subscription: $(az account show --query name --output tsv)"
echo "Resource group: $resource_group"
echo "VM: $vm_name"
echo "VM size: $vm_size"
echo "Power state: $power_state"
az disk show --ids "$disk_id" --query '{disk:name,sizeGiB:diskSizeGb,sku:sku.name,state:diskState}' --output table
az network public-ip show \
  --resource-group "$resource_group" \
  --name sentinel-demo-ip \
  --query '{ip:ipAddress,fqdn:dnsSettings.fqdn,sku:sku.name,allocation:publicIPAllocationMethod}' \
  --output table

if [[ "$power_state" == "VM running" && "${AZURE_INCLUDE_RUNTIME_SNAPSHOT:-yes}" == "yes" ]]; then
  echo
  echo 'Live guest/container snapshot'
  echo '-----------------------------'
  az vm run-command invoke \
    --resource-group "$resource_group" \
    --name "$vm_name" \
    --command-id RunShellScript \
    --scripts @deployment/azure-demo/collect-runtime-snapshot.sh \
    --query 'value[0].message' \
    --output tsv
fi

from_date="$(date -u -d "$days days ago" +%Y-%m-%dT00:00:00Z)"
to_date="$(date -u -d 'tomorrow' +%Y-%m-%dT00:00:00Z)"
working_directory="$(mktemp -d)"
trap 'rm -rf "$working_directory"' EXIT

jq -n \
  --arg from "$from_date" \
  --arg to "$to_date" \
  '{
    type: "ActualCost",
    timeframe: "Custom",
    timePeriod: {from: $from, to: $to},
    dataset: {
      granularity: "Daily",
      aggregation: {
        totalCost: {name: "Cost", function: "Sum"}
      }
    }
  }' > "$working_directory/cost-query.json"

echo
echo "Recorded resource-group cost, last $days days"
echo '---------------------------------------------'
if az rest \
  --method post \
  --url "https://management.azure.com$resource_group_id/providers/Microsoft.CostManagement/query?api-version=2025-03-01" \
  --body "@$working_directory/cost-query.json" \
  > "$working_directory/cost-result.json"; then
  jq -r '
    (.properties.columns | map(.name) | @tsv),
    (.properties.rows[]? | @tsv)
  ' "$working_directory/cost-result.json" |
    column -t -s $'\t'
else
  echo 'Cost query was unavailable. Assign Cost Management Reader at resource-group scope or use Cost Analysis in the portal.' >&2
fi

hourly_rate="0.0492"
if [[ "$vm_size" == "Standard_B4as_v2" ]]; then
  hourly_rate="0.0984"
fi
disk_daily="$(awk 'BEGIN {printf "%.4f", 5.28 / 30}')"
ip_daily="$(awk 'BEGIN {printf "%.4f", 0.005 * 24}')"
deallocated_floor="$(awk -v disk="$disk_daily" -v ip="$ip_daily" 'BEGIN {printf "%.4f", disk + ip}')"
two_hour_daily="$(awk -v base="$deallocated_floor" -v hourly="$hourly_rate" 'BEGIN {printf "%.4f", base + (2 * hourly)}')"
always_on_daily="$(awk -v base="$deallocated_floor" -v hourly="$hourly_rate" 'BEGIN {printf "%.4f", base + (24 * hourly)}')"

echo
echo 'Retail-rate projection'
echo '----------------------'
printf 'VM hourly rate (%s):        $%s\n' "$vm_size" "$hourly_rate"
printf 'Retained 64-GiB SSD per day: $%s\n' "$disk_daily"
printf 'Static IPv4 per day:          $%s\n' "$ip_daily"
printf 'Deallocated daily floor:      $%s\n' "$deallocated_floor"
printf 'Two-hour daily session:       $%s\n' "$two_hour_daily"
printf 'Always-on daily projection:   $%s\n' "$always_on_daily"
printf 'Configured daily target:      $%s\n' "$target_daily_cost"

echo
echo 'Recent VM lifecycle operations'
echo '------------------------------'
az monitor activity-log list \
  --resource-id "$vm_id" \
  --start-time "$from_date" \
  --query "[?contains(operationName.value, 'start') || contains(operationName.value, 'deallocate') || contains(operationName.value, 'powerOff')].{time:eventTimestamp,operation:operationName.localizedValue,status:status.localizedValue,caller:caller}" \
  --output table

echo
echo 'Control-plane verification'
echo '--------------------------'
az logic workflow show \
  --resource-group "$resource_group" \
  --name sentinel-demo-session \
  --query '{name:name,state:state,identity:identity.type}' \
  --output table 2>/dev/null ||
  echo 'On-demand session workflow is not configured.'
az logic workflow show \
  --resource-group "$resource_group" \
  --name sentinel-budget-deallocate \
  --query '{name:name,state:state,identity:identity.type}' \
  --output table 2>/dev/null ||
  echo 'Budget deallocation workflow is not configured.'

cat <<'EOF'

Interpretation:
- Running and Stopped (Allocated) incur VM compute cost.
- VM deallocated stops compute cost; the retained disk and static IP remain billable.
- Cost Management normally lags real usage, so compare several consecutive daily snapshots.
- Runtime CPU and memory show whether the smaller VM is healthy; they do not change an allocated VM's hourly price.
EOF
