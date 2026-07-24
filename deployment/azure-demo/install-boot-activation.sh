#!/usr/bin/env bash
set -euo pipefail

[[ "$(id -u)" -eq 0 ]] || {
  echo 'Boot activation installation must run as root.' >&2
  exit 1
}

cat > /etc/systemd/system/sentinel-activate-latest.service <<'EOF'
[Unit]
Description=Activate the latest verified Sentinel release after an owner-started VM session
Wants=network-online.target
After=network-online.target docker.service
Requires=docker.service

[Service]
Type=oneshot
ExecStart=/usr/bin/bash /opt/sentinel/release/deployment/azure-demo/activate-latest-on-boot.sh
TimeoutStartSec=1800

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable sentinel-activate-latest.service >/dev/null
