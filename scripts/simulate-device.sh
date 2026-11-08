#!/usr/bin/env bash
# Simulates the ESP32 without hardware: places an order, then checks the pending count.
# Usage: ./scripts/simulate-device.sh [host:port] [MILK-1L=2 EGGS-12=1 ...]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOST="${1:-localhost:8443}"; shift || true
DEVICE_ID="${DEVICE_ID:-esp32-kitchen-01}"
API_KEY="${DEVICE_API_KEY:-change-me-device-key}"
ITEMS=("${@:-MILK-1L=2 EGGS-12=1}")

json_items=""
for pair in ${ITEMS[*]}; do
  json_items+="{\"sku\":\"${pair%%=*}\",\"quantity\":${pair##*=}},"
done
BODY="{\"clientOrderId\":\"sim-$(date +%s)-$RANDOM\",\"items\":[${json_items%,}]}"

CURL=(curl -sS --cacert "$ROOT/certs/ca/ca.crt" -H "X-Device-Id: $DEVICE_ID" -H "X-Api-Key: $API_KEY")
echo ">> POST /api/device/orders $BODY"
"${CURL[@]}" -w '\n<< HTTP %{http_code}\n' -H 'Content-Type: application/json' -d "$BODY" "https://$HOST/api/device/orders"
sleep 2
echo ">> GET /api/device/orders/pending"
"${CURL[@]}" -w '\n<< HTTP %{http_code}\n' "https://$HOST/api/device/orders/pending"
