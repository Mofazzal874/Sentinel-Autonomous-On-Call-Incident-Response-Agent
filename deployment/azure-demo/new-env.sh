#!/usr/bin/env bash
set -euo pipefail
umask 077

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
secret_directory="$repo_root/.sentinel"
environment_file="$secret_directory/azure-demo.env"

if [[ -f "$environment_file" ]]; then
  echo "Reusing existing ignored environment file: $environment_file"
  exit 0
fi

: "${SENTINEL_DEMO_ADDRESS:?Set SENTINEL_DEMO_ADDRESS to your Azure FQDN without http://, or to a custom domain}"

mkdir -p "$secret_directory"
cat > "$environment_file" <<EOF
SENTINEL_DB_NAME=sentinel
SENTINEL_DB_USERNAME=sentinel
SENTINEL_DB_PASSWORD=$(openssl rand -hex 24)
SENTINEL_RABBITMQ_USERNAME=sentinel
SENTINEL_RABBITMQ_PASSWORD=$(openssl rand -hex 24)
SENTINEL_JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
SENTINEL_WEBHOOK_SECRET=$(openssl rand -base64 48 | tr -d '\n')
SENTINEL_OLLAMA_BASE_URL=http://ollama:11434
SENTINEL_APP_PORT=8080
SENTINEL_DEMO_ADDRESS=$SENTINEL_DEMO_ADDRESS
EOF
chmod 600 "$environment_file"
echo "Created permission-restricted environment file: $environment_file"
