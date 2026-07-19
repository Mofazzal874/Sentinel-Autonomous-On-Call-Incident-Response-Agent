#!/usr/bin/env bash
set -euo pipefail

: "${AZURE_DEMO_DNS_LABEL:?Choose a globally unique lowercase DNS label}"
: "${AZURE_SSH_SOURCE_IP:?Set your home public IPv4 address without /32}"

if [[ "${CONFIRM_CREATE_AZURE_RESOURCES:-no}" != "yes" ]]; then
  echo 'No resources created. Set CONFIRM_CREATE_AZURE_RESOURCES=yes only after reviewing names, region, and cost.' >&2
  exit 1
fi

location="centralindia"
resource_group="sentinel-demo-rg"
vm_name="sentinel-demo-vm"

az account show --query '{subscription:name,state:state}' --output table
az group create --name "$resource_group" --location "$location" --output none
az network public-ip create --resource-group "$resource_group" --name sentinel-demo-ip --location "$location" --sku Standard --allocation-method Static --dns-name "$AZURE_DEMO_DNS_LABEL" --output none
az network vnet create --resource-group "$resource_group" --name sentinel-demo-vnet --location "$location" --address-prefixes 10.40.0.0/16 --subnet-name sentinel-demo-subnet --subnet-prefixes 10.40.1.0/24 --output none
az network nsg create --resource-group "$resource_group" --name sentinel-demo-nsg --location "$location" --output none
az network nsg rule create --resource-group "$resource_group" --nsg-name sentinel-demo-nsg --name AllowSshFromOwner --priority 100 --access Allow --protocol Tcp --direction Inbound --source-address-prefixes "${AZURE_SSH_SOURCE_IP}/32" --destination-port-ranges 22 --output none
az network nsg rule create --resource-group "$resource_group" --nsg-name sentinel-demo-nsg --name AllowHttp --priority 110 --access Allow --protocol Tcp --direction Inbound --source-address-prefixes Internet --destination-port-ranges 80 --output none
az network nsg rule create --resource-group "$resource_group" --nsg-name sentinel-demo-nsg --name AllowHttps --priority 120 --access Allow --protocol Tcp --direction Inbound --source-address-prefixes Internet --destination-port-ranges 443 --output none
az network nic create --resource-group "$resource_group" --name sentinel-demo-nic --location "$location" --vnet-name sentinel-demo-vnet --subnet sentinel-demo-subnet --network-security-group sentinel-demo-nsg --public-ip-address sentinel-demo-ip --output none
az vm create --resource-group "$resource_group" --name "$vm_name" --location "$location" --nics sentinel-demo-nic --image Ubuntu2404 --size Standard_B4as_v2 --admin-username azureuser --generate-ssh-keys --os-disk-size-gb 64 --storage-sku StandardSSD_LRS --custom-data deployment/azure-demo/bootstrap-ubuntu.sh --output none

az network public-ip show --resource-group "$resource_group" --name sentinel-demo-ip --query '{ip:ipAddress,fqdn:dnsSettings.fqdn}' --output table
echo 'Provisioning submitted. Wait for cloud-init before the first Docker command: az vm run-command invoke --resource-group sentinel-demo-rg --name sentinel-demo-vm --command-id RunShellScript --scripts "cloud-init status --wait"'
