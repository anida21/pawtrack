#!/usr/bin/env sh
# Adds *.pawtrack.local aliases to /etc/hosts (Linux/macOS, needs sudo).
set -e
for name in keycloak gateway identity pet health notification; do
  host="$name.pawtrack.local"
  grep -q "[[:space:]]$host\$" /etc/hosts || echo "127.0.0.1	$host" | sudo tee -a /etc/hosts >/dev/null
done
echo "hosts ok"
