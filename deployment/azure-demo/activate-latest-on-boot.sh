#!/usr/bin/env bash
set -euo pipefail

repository_url="https://github.com/Mofazzal874/Sentinel-Autonomous-On-Call-Incident-Response-Agent.git"
image_repository="ghcr.io/mofazzal874/sentinel-autonomous-on-call-incident-response-agent"
release_directory="/opt/sentinel/release"
environment_file="$release_directory/.sentinel/azure-demo.env"

[[ -f "$environment_file" ]] || {
  echo "Missing Azure demo environment file: $environment_file" >&2
  exit 1
}

demo_address="$(sed -n 's/^SENTINEL_DEMO_ADDRESS=//p' "$environment_file" | head -n 1)"
case "$demo_address" in
  http://*|https://*|*/*|*[!A-Za-z0-9.-]*|'')
    echo 'The stored Azure demo address is invalid.' >&2
    exit 2
    ;;
esac

release_sha=""
for attempt in $(seq 1 12); do
  release_sha="$(git ls-remote "$repository_url" refs/heads/main 2>/dev/null | awk 'NR == 1 {print $1}')"
  case "$release_sha" in
    ''|*[!0-9a-f]*) ;;
    *) [[ "${#release_sha}" -eq 40 ]] && break ;;
  esac
  release_sha=""
  sleep 10
done

[[ -n "$release_sha" ]] || {
  echo 'Could not resolve the latest main commit; preserving the installed release.' >&2
  exit 3
}

image="$image_repository:$release_sha"
if ! docker pull "$image"; then
  echo "The verified image for $release_sha is not published; preserving the installed release." >&2
  exit 0
fi

exec /bin/sh "$release_directory/deployment/azure-demo/activate-release.sh" \
  "$release_sha" \
  "$image" \
  "$demo_address"
