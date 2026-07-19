#!/usr/bin/env bash
set -euo pipefail

if [[ "${CONFIRM_CONFIGURE_COST_GUARD:-no}" != "yes" ]]; then
  echo 'No cost-control resource changed. Review the guide, then set CONFIRM_CONFIGURE_COST_GUARD=yes.' >&2
  exit 1
fi

for required_command in az jq; do
  command -v "$required_command" >/dev/null 2>&1 || {
    echo "Required command is missing: $required_command" >&2
    exit 2
  }
done

: "${AZURE_BUDGET_NAME:?Set this to the exact name of your existing Azure budget}"

resource_group="${AZURE_RESOURCE_GROUP:-sentinel-demo-rg}"
vm_name="${AZURE_VM_NAME:-sentinel-demo-vm}"
location="${AZURE_LOCATION:-centralindia}"
threshold="${AZURE_COST_GUARD_THRESHOLD_PERCENT:-50}"
workflow_name="sentinel-budget-deallocate"
action_group_name="sentinel-budget-stop"
role_name="Sentinel Demo VM Deallocator"

case "$threshold" in
  ''|*[!0-9]*) echo 'AZURE_COST_GUARD_THRESHOLD_PERCENT must be an integer from 1 through 100.' >&2; exit 2 ;;
esac
if (( threshold < 1 || threshold > 100 )); then
  echo 'AZURE_COST_GUARD_THRESHOLD_PERCENT must be from 1 through 100.' >&2
  exit 2
fi

subscription_id="$(az account show --query id --output tsv)"
resource_group_id="$(az group show --name "$resource_group" --query id --output tsv)"
vm_id="$(az vm show --resource-group "$resource_group" --name "$vm_name" --query id --output tsv)"
workflow_id="$resource_group_id/providers/Microsoft.Logic/workflows/$workflow_name"
action_group_id="$resource_group_id/providers/Microsoft.Insights/actionGroups/$action_group_name"
encoded_budget_name="$(printf '%s' "$AZURE_BUDGET_NAME" | jq -sRr @uri)"
budget_id="/subscriptions/$subscription_id/providers/Microsoft.Consumption/budgets/$encoded_budget_name"

az provider register --namespace Microsoft.Logic --wait
az provider register --namespace Microsoft.Insights --wait
az provider register --namespace Microsoft.Consumption --wait

working_directory="$(mktemp -d)"
trap 'rm -rf "$working_directory"' EXIT

jq -n \
  --arg location "$location" \
  --arg vm_id "$vm_id" \
  '{
    location: $location,
    identity: {type: "SystemAssigned"},
    properties: {
      state: "Enabled",
      definition: {
        "$schema": "https://schema.management.azure.com/providers/Microsoft.Logic/schemas/2016-06-01/workflowdefinition.json#",
        contentVersion: "1.0.0.0",
        parameters: {},
        triggers: {
          budget_alert: {
            type: "Request",
            kind: "Http",
            inputs: {schema: {type: "object"}}
          }
        },
        actions: {
          deallocate_demo_vm: {
            type: "Http",
            inputs: {
              method: "POST",
              uri: ("https://management.azure.com" + $vm_id + "/deallocate?api-version=2024-03-01"),
              authentication: {
                type: "ManagedServiceIdentity",
                audience: "https://management.azure.com/"
              }
            },
            runAfter: {}
          }
        },
        outputs: {}
      },
      parameters: {}
    }
  }' > "$working_directory/workflow.json"

az rest --method put \
  --url "https://management.azure.com$workflow_id?api-version=2019-05-01" \
  --body "@$working_directory/workflow.json" \
  --output none

principal_id="$(az rest --method get --url "https://management.azure.com$workflow_id?api-version=2019-05-01" --query identity.principalId --output tsv)"

cat > "$working_directory/role.json" <<EOF
{
  "Name": "$role_name",
  "Description": "Can inspect and deallocate only the Sentinel demo VM.",
  "Actions": [
    "Microsoft.Compute/virtualMachines/read",
    "Microsoft.Compute/virtualMachines/instanceView/read",
    "Microsoft.Compute/virtualMachines/deallocate/action"
  ],
  "NotActions": [],
  "DataActions": [],
  "NotDataActions": [],
  "AssignableScopes": ["$resource_group_id"]
}
EOF

role_id="$(az role definition list --name "$role_name" --query '[0].id' --output tsv)"
if [[ -z "$role_id" ]]; then
  role_id="$(az role definition create --role-definition "$working_directory/role.json" --query id --output tsv)"
fi

assignment_id="$(az role assignment list --assignee-object-id "$principal_id" --scope "$vm_id" --query "[?roleDefinitionId=='$role_id'].id | [0]" --output tsv)"
if [[ -z "$assignment_id" ]]; then
  for attempt in {1..6}; do
    if az role assignment create \
      --assignee-object-id "$principal_id" \
      --assignee-principal-type ServicePrincipal \
      --role "$role_name" \
      --scope "$vm_id" \
      --output none; then
      break
    fi
    if (( attempt == 6 )); then
      echo 'Role assignment did not become available after six attempts. Rerun this idempotent script.' >&2
      exit 4
    fi
    sleep 5
  done
fi

callback_url="$(az rest --method post --url "https://management.azure.com$workflow_id/triggers/budget_alert/listCallbackUrl?api-version=2019-05-01" --query value --output tsv)"

jq -n \
  --arg location "global" \
  --arg callback "$callback_url" \
  --arg workflow_id "$workflow_id" \
  '{
    location: $location,
    properties: {
      enabled: true,
      groupShortName: "SentBudget",
      logicAppReceivers: [{
        name: "Deallocate Sentinel demo VM",
        resourceId: $workflow_id,
        callbackUrl: $callback,
        useCommonAlertSchema: true
      }]
    }
  }' > "$working_directory/action-group.json"

az rest --method put \
  --url "https://management.azure.com$action_group_id?api-version=2023-01-01" \
  --body "@$working_directory/action-group.json" \
  --output none

az rest --method get \
  --url "https://management.azure.com$budget_id?api-version=2024-08-01" \
  > "$working_directory/budget-current.json"

jq \
  --arg action_group_id "$action_group_id" \
  --argjson threshold "$threshold" \
  '{
    properties: {
      amount: .properties.amount,
      category: .properties.category,
      timeGrain: .properties.timeGrain,
      timePeriod: .properties.timePeriod,
      filter: .properties.filter,
      notifications: (
        (.properties.notifications // {}) + {
          SentinelEarlyDeallocate: {
            enabled: true,
            operator: "GreaterThanOrEqualTo",
            threshold: $threshold,
            thresholdType: "Actual",
            contactEmails: [],
            contactRoles: [],
            contactGroups: [$action_group_id],
            locale: "en-us"
          }
        }
      )
    }
  }' "$working_directory/budget-current.json" > "$working_directory/budget-update.json"

az rest --method put \
  --url "https://management.azure.com$budget_id?api-version=2024-08-01" \
  --body "@$working_directory/budget-update.json" \
  --output none

cat <<EOF
Cost guard connected to budget '$AZURE_BUDGET_NAME'.
At ${threshold}% actual budget consumption, Azure can invoke the Logic App to deallocate $vm_name.

This is containment, not a hard cap: Azure cost data can be delayed. The OS disk and static IP can
continue to cost money after VM deallocation. Delete the dedicated resource group only when you no
longer need the stable URL or its data.
EOF

if [[ -n "${AZURE_DAILY_SHUTDOWN_UTC:-}" ]]; then
  shutdown_arguments=(
    --resource-group "$resource_group"
    --name "$vm_name"
    --time "$AZURE_DAILY_SHUTDOWN_UTC"
    --output none
  )
  if [[ -n "${AZURE_BUDGET_EMAIL:-}" ]]; then
    shutdown_arguments+=(--email "$AZURE_BUDGET_EMAIL")
  fi
  az vm auto-shutdown "${shutdown_arguments[@]}"
  echo "Daily auto-shutdown also configured for ${AZURE_DAILY_SHUTDOWN_UTC} UTC. Azure will not auto-start the VM."
fi
