#!/usr/bin/env sh
set -eu

echo "captured_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "uptime=$(uptime -p)"
echo
echo 'Memory (MiB):'
free -m
echo
echo 'Root filesystem:'
df -h /
echo
echo 'Sentinel container resources:'
docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.PIDs}}' \
  $(docker ps --filter 'name=sentinel-azure-demo-' --quiet) 2>/dev/null || true
echo
echo 'Docker storage:'
docker system df
echo
echo 'Largest Docker logs:'
find /var/lib/docker/containers -name '*-json.log' -type f -printf '%s %p\n' 2>/dev/null |
  sort -nr |
  head -n 10 |
  awk '{printf "%.1f MiB %s\n", $1 / 1048576, $2}'
