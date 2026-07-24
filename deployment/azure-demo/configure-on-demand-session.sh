#!/usr/bin/env bash
set -euo pipefail

if [[ "${CONFIRM_CONFIGURE_ON_DEMAND_SESSION:-no}" != "yes" ]]; then
  echo 'No VM or Azure workflow changed. Review the guide, then set CONFIRM_CONFIGURE_ON_DEMAND_SESSION=yes.' >&2
  exit 1
fi

for required_command in az jq; do
  command -v "$required_command" >/dev/null 2>&1 || {
    echo "Required command is missing: $required_command" >&2
    exit 2
  }
done

resource_group="${AZURE_RESOURCE_GROUP:-sentinel-demo-rg}"
vm_name="${AZURE_VM_NAME:-sentinel-demo-vm}"
location="${AZURE_LOCATION:-centralindia}"
target_size="${AZURE_TARGET_VM_SIZE:-Standard_B2as_v2}"
session_hours="${AZURE_SESSION_HOURS:-2}"
workflow_name="sentinel-demo-session"
role_name="Sentinel Demo Session Operator"

[[ "$target_size" == "Standard_B2as_v2" ]] || {
  echo 'The reviewed cost profile permits only Standard_B2as_v2.' >&2
  exit 2
}

case "$session_hours" in
  ''|*[!0-9]*) echo 'AZURE_SESSION_HOURS must be an integer from 1 through 4.' >&2; exit 2 ;;
esac
if (( session_hours < 1 || session_hours > 4 )); then
  echo 'AZURE_SESSION_HOURS must be from 1 through 4.' >&2
  exit 2
fi

subscription_id="$(az account show --query id --output tsv)"
resource_group_id="$(az group show --name "$resource_group" --query id --output tsv)"
vm_id="$(az vm show --resource-group "$resource_group" --name "$vm_name" --query id --output tsv)"
workflow_id="$resource_group_id/providers/Microsoft.Logic/workflows/$workflow_name"

az provider register --namespace Microsoft.Compute --wait
az provider register --namespace Microsoft.Logic --wait
az provider register --namespace Microsoft.Authorization --wait

current_size="$(az vm show --resource-group "$resource_group" --name "$vm_name" --query hardwareProfile.vmSize --output tsv)"
power_state="$(az vm get-instance-view \
  --resource-group "$resource_group" \
  --name "$vm_name" \
  --query "instanceView.statuses[?starts_with(code,'PowerState/')].displayStatus | [0]" \
  --output tsv)"

echo "Current VM: $current_size, $power_state"

if [[ "$power_state" == "VM running" ]]; then
  echo 'Capturing a pre-resize runtime snapshot...'
  az vm run-command invoke \
    --resource-group "$resource_group" \
    --name "$vm_name" \
    --command-id RunShellScript \
    --scripts @deployment/azure-demo/collect-runtime-snapshot.sh \
    --query 'value[0].message' \
    --output tsv
fi

if [[ "$current_size" != "$target_size" ]]; then
  if ! az vm list-vm-resize-options \
    --resource-group "$resource_group" \
    --name "$vm_name" \
    --query "[?name=='$target_size'].name | [0]" \
    --output tsv |
    grep -qx "$target_size"; then
    echo "$target_size is not available in the VM's current allocation cluster."
    echo "Deallocating $vm_name so Azure can re-evaluate resize capacity..."
    az vm deallocate --resource-group "$resource_group" --name "$vm_name"
    if ! az vm list-vm-resize-options \
      --resource-group "$resource_group" \
      --name "$vm_name" \
      --query "[?name=='$target_size'].name | [0]" \
      --output tsv |
      grep -qx "$target_size"; then
      echo "$target_size remains unavailable. The VM was left deallocated to stop compute billing; no resize was attempted." >&2
      exit 3
    fi
  else
    echo "Deallocating $vm_name before resizing..."
    az vm deallocate --resource-group "$resource_group" --name "$vm_name"
  fi

  az vm resize --resource-group "$resource_group" --name "$vm_name" --size "$target_size"
else
  if [[ "$power_state" != "VM deallocated" ]]; then
    echo "Deallocating $vm_name to begin cost-controlled operation..."
    az vm deallocate --resource-group "$resource_group" --name "$vm_name"
  fi
fi

working_directory="$(mktemp -d)"
trap 'rm -rf "$working_directory"' EXIT

jq -n \
  --arg location "$location" \
  --arg vm_id "$vm_id" \
  --argjson session_hours "$session_hours" \
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
          owner_wake_request: {
            type: "Request",
            kind: "Http",
            inputs: {schema: {type: "object", additionalProperties: false}}
          }
        },
        actions: {
          start_demo_vm: {
            type: "Http",
            inputs: {
              method: "POST",
              uri: ("https://management.azure.com" + $vm_id + "/start?api-version=2024-03-01"),
              authentication: {
                type: "ManagedServiceIdentity",
                audience: "https://management.azure.com/"
              },
              retryPolicy: {
                type: "exponential",
                count: 4,
                interval: "PT10S",
                minimumInterval: "PT5S",
                maximumInterval: "PT1M"
              }
            },
            runAfter: {}
          },
          acknowledge_bounded_session: {
            type: "Response",
            inputs: {
              statusCode: 202,
              body: {
                state: "START_REQUEST_ACCEPTED",
                sessionHours: $session_hours,
                message: "The stable Sentinel URL normally becomes ready within several minutes. This lease cannot be extended; the VM will be deallocated when it expires."
              }
            },
            runAfter: {
              start_demo_vm: ["Succeeded"]
            }
          },
          hold_bounded_session: {
            type: "Wait",
            inputs: {
              interval: {
                count: $session_hours,
                unit: "Hour"
              }
            },
            runAfter: {
              start_demo_vm: ["Succeeded", "Failed", "Skipped", "TimedOut"]
            }
          },
          deallocate_demo_vm: {
            type: "Http",
            inputs: {
              method: "POST",
              uri: ("https://management.azure.com" + $vm_id + "/deallocate?api-version=2024-03-01"),
              authentication: {
                type: "ManagedServiceIdentity",
                audience: "https://management.azure.com/"
              },
              retryPolicy: {
                type: "exponential",
                count: 6,
                interval: "PT15S",
                minimumInterval: "PT10S",
                maximumInterval: "PT2M"
              }
            },
            runAfter: {
              hold_bounded_session: ["Succeeded", "Failed", "TimedOut"]
            }
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

principal_id="$(az rest \
  --method get \
  --url "https://management.azure.com$workflow_id?api-version=2019-05-01" \
  --query identity.principalId \
  --output tsv)"

cat > "$working_directory/role.json" <<EOF
{
  "Name": "$role_name",
  "Description": "Can inspect, start, and deallocate only the Sentinel demo VM for a bounded owner-started session.",
  "Actions": [
    "Microsoft.Compute/virtualMachines/read",
    "Microsoft.Compute/virtualMachines/instanceView/read",
    "Microsoft.Compute/virtualMachines/start/action",
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
  role_id="$(az role definition create \
    --role-definition "$working_directory/role.json" \
    --query id \
    --output tsv)"
fi

assignment_id="$(az role assignment list \
  --assignee-object-id "$principal_id" \
  --scope "$vm_id" \
  --query "[?roleDefinitionId=='$role_id'].id | [0]" \
  --output tsv)"
if [[ -z "$assignment_id" ]]; then
  for attempt in {1..8}; do
    if az role assignment create \
      --assignee-object-id "$principal_id" \
      --assignee-principal-type ServicePrincipal \
      --role "$role_name" \
      --scope "$vm_id" \
      --output none; then
      break
    fi
    if (( attempt == 8 )); then
      echo 'Role assignment did not become available after eight attempts. Rerun this idempotent script.' >&2
      exit 4
    fi
    sleep 10
  done
fi

callback_url="$(az rest \
  --method post \
  --url "https://management.azure.com$workflow_id/triggers/owner_wake_request/listCallbackUrl?api-version=2019-05-01" \
  --query value \
  --output tsv)"

wake_file="$HOME/.sentinel-demo-wake-url"
umask 077
printf '%s\n' "$callback_url" > "$wake_file"
chmod 600 "$wake_file"

final_size="$(az vm show --resource-group "$resource_group" --name "$vm_name" --query hardwareProfile.vmSize --output tsv)"
final_state="$(az vm get-instance-view \
  --resource-group "$resource_group" \
  --name "$vm_name" \
  --query "instanceView.statuses[?starts_with(code,'PowerState/')].displayStatus | [0]" \
  --output tsv)"

cat <<EOF

On-demand cost control is configured.

VM size:       $final_size
VM state:      $final_state
Session lease: ${session_hours} hour(s)
Wake URL file: $wake_file

To begin a demo session:
  curl --fail --show-error --silent --request POST "\$(cat "$wake_file")"

The callback URL is an owner credential. Do not commit it, publish it, put it in the frontend,
or send it to a recruiter. The stable application URL remains unchanged, but it is offline while
the VM is deallocated. A two-hour B2as v2 session plus the retained disk and static IP models about
\$0.3944/day using the reviewed July 2026 USD retail rates. Actual billing and taxes can differ.
EOF
