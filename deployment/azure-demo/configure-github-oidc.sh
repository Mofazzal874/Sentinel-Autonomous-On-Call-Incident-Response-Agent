#!/usr/bin/env bash
set -euo pipefail

if [[ "${CONFIRM_CONFIGURE_AZURE_OIDC:-no}" != "yes" ]]; then
  echo 'No Azure identity changed. Review this script, then set CONFIRM_CONFIGURE_AZURE_OIDC=yes.' >&2
  exit 1
fi

for required_command in az curl jq; do
  command -v "$required_command" >/dev/null 2>&1 || {
    echo "Required command is missing: $required_command" >&2
    exit 2
  }
done

resource_group="${AZURE_RESOURCE_GROUP:-sentinel-demo-rg}"
vm_name="${AZURE_VM_NAME:-sentinel-demo-vm}"
application_name="sentinel-github-deployer"
role_name="Sentinel Demo Release Activator"
federated_name="sentinel-main-azure-demo"
github_subject="repo:Mofazzal874@35369040/Sentinel-Autonomous-On-Call-Incident-Response-Agent@1304261078:environment:azure-demo"
demo_address="${AZURE_DEMO_ADDRESS:-sentinel-mofazzal874.centralindia.cloudapp.azure.com}"

repository_metadata="$(curl --fail --silent --show-error \
  https://api.github.com/repos/Mofazzal874/Sentinel-Autonomous-On-Call-Incident-Response-Agent)"
observed_owner_id="$(jq -r '.owner.id' <<< "$repository_metadata")"
observed_repository_id="$(jq -r '.id' <<< "$repository_metadata")"
if [[ "$observed_owner_id" != "35369040" || "$observed_repository_id" != "1304261078" ]]; then
  echo 'The public GitHub owner/repository IDs no longer match the reviewed immutable OIDC subject.' >&2
  echo 'Stop and re-audit the repository identity before changing Azure federation.' >&2
  exit 2
fi

subscription_id="$(az account show --query id --output tsv)"
tenant_id="$(az account show --query tenantId --output tsv)"
resource_group_id="$(az group show --name "$resource_group" --query id --output tsv)"
vm_id="$(az vm show --resource-group "$resource_group" --name "$vm_name" --query id --output tsv)"

application_id="$(az ad app list --display-name "$application_name" --query '[0].appId' --output tsv)"
if [[ -z "$application_id" ]]; then
  application_id="$(az ad app create --display-name "$application_name" --query appId --output tsv)"
fi

principal_id="$(az ad sp list --filter "appId eq '$application_id'" --query '[0].id' --output tsv)"
if [[ -z "$principal_id" ]]; then
  principal_id="$(az ad sp create --id "$application_id" --query id --output tsv)"
fi

credential_file="$(mktemp)"
role_file="$(mktemp)"
trap 'rm -f "$credential_file" "$role_file"' EXIT

cat > "$credential_file" <<EOF
{
  "name": "$federated_name",
  "issuer": "https://token.actions.githubusercontent.com",
  "subject": "$github_subject",
  "description": "GitHub Actions azure-demo environment for Sentinel",
  "audiences": ["api://AzureADTokenExchange"]
}
EOF

existing_credential="$(az ad app federated-credential list --id "$application_id" --query "[?name=='$federated_name'].name | [0]" --output tsv)"
if [[ -z "$existing_credential" ]]; then
  az ad app federated-credential create --id "$application_id" --parameters "$credential_file" --output none
else
  az ad app federated-credential update --id "$application_id" --federated-credential-id "$federated_name" --parameters "$credential_file" --output none
fi

role_id="$(az role definition list --name "$role_name" --query '[0].id' --output tsv)"
if [[ -z "$role_id" ]]; then
  cat > "$role_file" <<EOF
{
  "Name": "$role_name",
  "Description": "Can inspect and invoke Run Command only on the Sentinel demo VM.",
  "Actions": [
    "Microsoft.Compute/virtualMachines/read",
    "Microsoft.Compute/virtualMachines/instanceView/read",
    "Microsoft.Compute/virtualMachines/runCommand/action"
  ],
  "NotActions": [],
  "DataActions": [],
  "NotDataActions": [],
  "AssignableScopes": ["$resource_group_id"]
}
EOF
  role_id="$(az role definition create --role-definition "$role_file" --query id --output tsv)"
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

cat <<EOF

Azure OIDC identity is configured without a client secret.

In GitHub -> Settings -> Environments -> azure-demo, add these environment variables:
AZURE_CLIENT_ID=$application_id
AZURE_TENANT_ID=$tenant_id
AZURE_SUBSCRIPTION_ID=$subscription_id
AZURE_RESOURCE_GROUP=$resource_group
AZURE_VM_NAME=$vm_name
AZURE_DEMO_ADDRESS=$demo_address
AZURE_DEMO_HEALTH_URL=https://$demo_address/actuator/health/readiness

After all seven variables exist, add this repository variable:
AZURE_DEPLOY_ENABLED=true

Expected immutable OIDC subject:
$github_subject
EOF
