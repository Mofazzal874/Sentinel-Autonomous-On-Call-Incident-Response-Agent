#!/usr/bin/env bash
set -euo pipefail

if [[ "${CONFIRM_CONFIGURE_WEEKDAY_SCHEDULE:-no}" != "yes" ]]; then
  echo 'No Azure schedule changed. Review the guide, then set CONFIRM_CONFIGURE_WEEKDAY_SCHEDULE=yes.' >&2
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
target_size="Standard_B2as_v2"
start_workflow_name="sentinel-demo-weekday-start"
stop_workflow_name="sentinel-demo-weekday-stop"
start_role_name="Sentinel Demo Weekday Starter"
stop_role_name="Sentinel Demo Weekday Deallocator"
schedule_anchor="2026-01-05T00:00:00Z"
weekdays='["Monday","Tuesday","Wednesday","Thursday","Friday"]'

resource_group_id="$(az group show --name "$resource_group" --query id --output tsv)"
vm_id="$(az vm show --resource-group "$resource_group" --name "$vm_name" --query id --output tsv)"

current_size="$(az vm show --resource-group "$resource_group" --name "$vm_name" --query hardwareProfile.vmSize --output tsv)"
if [[ "$current_size" != "$target_size" ]]; then
  echo "Expected $target_size but found $current_size. No schedule changed." >&2
  exit 3
fi

az provider register --namespace Microsoft.Compute --wait
az provider register --namespace Microsoft.Logic --wait
az provider register --namespace Microsoft.Authorization --wait

working_directory="$(mktemp -d)"
trap 'rm -rf "$working_directory"' EXIT

ensure_role_assignment() {
  local principal_id="$1"
  local role_name="$2"
  local role_action="$3"
  local role_file="$4"
  local role_id
  local assignment_id
  local verified_role_file="$role_file.verified"

  jq -n \
    --arg name "$role_name" \
    --arg action "$role_action" \
    --arg scope "$resource_group_id" \
    '{
      Name: $name,
      Description: ("Can inspect and invoke only " + $action + " on the exact Sentinel demo VM."),
      Actions: [
        "Microsoft.Compute/virtualMachines/read",
        "Microsoft.Compute/virtualMachines/instanceView/read",
        $action
      ],
      NotActions: [],
      DataActions: [],
      NotDataActions: [],
      AssignableScopes: [$scope]
    }' > "$role_file"

  role_id="$(az role definition list --name "$role_name" --query '[0].id' --output tsv)"
  if [[ -z "$role_id" ]]; then
    role_id="$(az role definition create --role-definition "$role_file" --query id --output tsv)"
  fi

  az role definition list --name "$role_name" --output json > "$verified_role_file"
  if ! jq -e \
    --arg action "$role_action" \
    --arg scope "$resource_group_id" \
    '
      length == 1 and
      .[0].assignableScopes == [$scope] and
      (
        (.[0].permissions[0].actions | sort) ==
        ([
          "Microsoft.Compute/virtualMachines/read",
          "Microsoft.Compute/virtualMachines/instanceView/read",
          $action
        ] | sort)
      )
    ' "$verified_role_file" >/dev/null; then
    echo "$role_name exists but does not have the exact reviewed permissions. No assignment changed." >&2
    exit 4
  fi

  assignment_id="$(az role assignment list \
    --assignee-object-id "$principal_id" \
    --scope "$vm_id" \
    --fill-principal-name false \
    --fill-role-definition-name false \
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
        echo "Role assignment for $role_name was unavailable after eight attempts. Rerun this idempotent script." >&2
        exit 4
      fi
      sleep 10
    done
  fi


  assignment_id="$(az role assignment list \
    --assignee-object-id "$principal_id" \
    --scope "$vm_id" \
    --fill-principal-name false \
    --fill-role-definition-name false \
    --query "[?roleDefinitionId=='$role_id'].id | [0]" \
    --output tsv)"
  [[ -n "$assignment_id" ]] || {
    echo "The exact-VM role assignment for $role_name did not pass read-back verification." >&2
    exit 4
  }
}

configure_workflow() {
  local workflow_name="$1"
  local role_name="$2"
  local operation="$3"
  local utc_hour="$4"
  local utc_minute="$5"
  local workflow_id="$resource_group_id/providers/Microsoft.Logic/workflows/$workflow_name"
  local workflow_file="$working_directory/$workflow_name.json"
  local verified_file="$working_directory/$workflow_name-verified.json"
  local principal_id
  local role_action="Microsoft.Compute/virtualMachines/$operation/action"

  jq -n \
    --arg location "$location" \
    --arg vm_id "$vm_id" \
    --arg operation "$operation" \
    --arg start_time "$schedule_anchor" \
    --argjson weekdays "$weekdays" \
    --argjson hour "$utc_hour" \
    --argjson minute "$utc_minute" \
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
            weekday_schedule: {
              type: "Recurrence",
              recurrence: {
                frequency: "Week",
                interval: 1,
                startTime: $start_time,
                schedule: {
                  weekDays: $weekdays,
                  hours: [$hour],
                  minutes: [$minute]
                }
              }
            }
          },
          actions: {
            invoke_exact_vm_operation: {
              type: "Http",
              inputs: {
                method: "POST",
                uri: ("https://management.azure.com" + $vm_id + "/" + $operation + "?api-version=2024-03-01"),
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
              runAfter: {}
            }
          },
          outputs: {}
        },
        parameters: {}
      }
    }' > "$workflow_file"

  az rest \
    --only-show-errors \
    --method put \
    --url "https://management.azure.com$workflow_id?api-version=2019-05-01" \
    --body "@$workflow_file" \
    --output none

  az rest \
    --only-show-errors \
    --method get \
    --url "https://management.azure.com$workflow_id?api-version=2019-05-01" \
    > "$verified_file"

  principal_id="$(jq -r '.identity.principalId // empty' "$verified_file")"
  [[ -n "$principal_id" ]] || {
    echo "$workflow_name has no managed identity after creation." >&2
    exit 5
  }

  ensure_role_assignment \
    "$principal_id" \
    "$role_name" \
    "$role_action" \
    "$working_directory/$workflow_name-role.json"

  if ! jq -e \
    --arg vm_id "$vm_id" \
    --arg operation "$operation" \
    --arg start_time "$schedule_anchor" \
    --argjson weekdays "$weekdays" \
    --argjson hour "$utc_hour" \
    --argjson minute "$utc_minute" \
    '
      .properties.state == "Enabled" and
      .identity.type == "SystemAssigned" and
      .properties.definition.triggers.weekday_schedule.type == "Recurrence" and
      .properties.definition.triggers.weekday_schedule.recurrence.frequency == "Week" and
      .properties.definition.triggers.weekday_schedule.recurrence.interval == 1 and
      (.properties.definition.triggers.weekday_schedule.recurrence.startTime | startswith("2026-01-05T00:00:00")) and
      .properties.definition.triggers.weekday_schedule.recurrence.schedule.weekDays == $weekdays and
      .properties.definition.triggers.weekday_schedule.recurrence.schedule.hours == [$hour] and
      .properties.definition.triggers.weekday_schedule.recurrence.schedule.minutes == [$minute] and
      .properties.definition.actions.invoke_exact_vm_operation.inputs.method == "POST" and
      .properties.definition.actions.invoke_exact_vm_operation.inputs.uri == ("https://management.azure.com" + $vm_id + "/" + $operation + "?api-version=2024-03-01") and
      .properties.definition.actions.invoke_exact_vm_operation.inputs.authentication.type == "ManagedServiceIdentity"
    ' "$verified_file" >/dev/null; then
    echo "$workflow_name did not pass strict schedule read-back verification." >&2
    exit 5
  fi

  echo "Verified $workflow_name at $(printf '%02d:%02d' "$utc_hour" "$utc_minute") UTC on weekdays."
}

# Bangladesh is UTC+06:00 and does not currently observe daylight saving time.
# Start ten minutes early so the application can normally be ready by 10:00.
configure_workflow "$start_workflow_name" "$start_role_name" start 3 50
configure_workflow "$stop_workflow_name" "$stop_role_name" deallocate 12 0

power_state="$(az vm get-instance-view \
  --resource-group "$resource_group" \
  --name "$vm_name" \
  --query "instanceView.statuses[?starts_with(code,'PowerState/')].displayStatus | [0]" \
  --output tsv)"

cat <<EOF

Weekday schedule configured and read back successfully.

Availability target: Monday-Friday, 10:00-18:00 Bangladesh time
VM start request:   Monday-Friday, 09:50 Bangladesh time (03:50 UTC)
VM deallocation:    Monday-Friday, 18:00 Bangladesh time (12:00 UTC)
Current VM state:   $power_state

The ten-minute lead time is for VM, Docker, and Spring Boot startup. The independent stop workflow
deallocates the VM even if the application is unhealthy. Weekends remain deallocated unless the
owner invokes the private bounded-session workflow manually.
EOF
