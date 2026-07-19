#!/usr/bin/env sh
set -eu

repository_url="https://github.com/Mofazzal874/Sentinel-Autonomous-On-Call-Incident-Response-Agent.git"
release_directory="/opt/sentinel/release"

release_sha="${1:-}"
image="${2:-}"
demo_address="${3:-}"

: "${release_sha:?Run Command must pass the 40-character Git commit SHA as argument 1}"
: "${image:?Run Command must pass the GHCR image as argument 2}"
: "${demo_address:?Run Command must pass the Azure hostname as argument 3}"

case "$release_sha" in
  *[!0-9a-f]*|'')
    echo "release_sha must contain only lowercase hexadecimal characters." >&2
    exit 2
    ;;
esac

if [ "${#release_sha}" -ne 40 ]; then
  echo "release_sha must be the full 40-character Git commit SHA." >&2
  exit 2
fi

expected_image="ghcr.io/mofazzal874/sentinel-autonomous-on-call-incident-response-agent:${release_sha}"
if [ "$image" != "$expected_image" ]; then
  echo "The image must be the immutable GHCR image for release_sha." >&2
  exit 2
fi

case "$demo_address" in
  http://*|https://*|*/*|*[!A-Za-z0-9.-]*|'')
    echo "demo_address must be a hostname without a scheme, path, or whitespace." >&2
    exit 2
    ;;
esac

if ! command -v git >/dev/null 2>&1; then
  apt-get update
  DEBIAN_FRONTEND=noninteractive apt-get install -y git
fi

install -d -o azureuser -g azureuser "$release_directory"

if [ -d "$release_directory/.git" ]; then
  if [ -n "$(sudo -u azureuser git -C "$release_directory" status --porcelain --untracked-files=no)" ]; then
    echo "Tracked files on the VM were modified; refusing to overwrite them." >&2
    exit 3
  fi
  sudo -u azureuser git -C "$release_directory" remote set-url origin "$repository_url"
  sudo -u azureuser git -C "$release_directory" fetch --prune origin
else
  if [ -n "$(find "$release_directory" -mindepth 1 -maxdepth 1 -print -quit)" ]; then
    echo "$release_directory is non-empty but is not a Git checkout." >&2
    exit 3
  fi
  sudo -u azureuser git clone "$repository_url" "$release_directory"
fi

sudo -u azureuser git -C "$release_directory" cat-file -e "${release_sha}^{commit}"
sudo -u azureuser git -C "$release_directory" checkout --detach --force "$release_sha"

activated_sha="$(sudo -u azureuser git -C "$release_directory" rev-parse HEAD)"
if [ "$activated_sha" != "$release_sha" ]; then
  echo "Checked-out source does not match the requested release." >&2
  exit 3
fi

export SENTINEL_DEMO_ADDRESS="$demo_address"
export SENTINEL_IMAGE="$image"

bash "$release_directory/deployment/azure-demo/new-env.sh"
docker pull "$image"
bash "$release_directory/deployment/azure-demo/start-azure.sh"

echo "Activated source and image for commit $release_sha"
