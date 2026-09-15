#!/usr/bin/env bash
#
# Screen transactions against the AML service.
# Usage:
#   ./screen-transaction.sh                     # Run all sample transactions
#   ./screen-transaction.sh samples/clear.json  # Screen a specific file
#
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SAMPLES_DIR="${SCRIPT_DIR}/samples"

screen() {
  local file="$1"
  local name
  name=$(basename "$file" .json)
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "▸ Screening: ${name}"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "Request:"
  cat "$file" | python3 -m json.tool 2>/dev/null || cat "$file"
  echo ""
  echo "Response:"
  curl -s -X POST "${BASE_URL}/api/v1/transactions/screen" \
    -H "Content-Type: application/json" \
    -d @"$file" | python3 -m json.tool 2>/dev/null || echo "(raw response above)"
  echo ""
  echo ""
}

# List rules
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "▸ Listing registered rules"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
curl -s "${BASE_URL}/api/v1/rules" | python3 -m json.tool 2>/dev/null || echo "(failed)"
echo ""
echo ""

# Health check
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "▸ Health check"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
curl -s "${BASE_URL}/actuator/health" | python3 -m json.tool 2>/dev/null || echo "(failed)"
echo ""
echo ""

# Screen transactions
if [ $# -gt 0 ]; then
  # Screen specific file(s)
  for file in "$@"; do
    if [ -f "$file" ]; then
      screen "$file"
    elif [ -f "${SAMPLES_DIR}/$file" ]; then
      screen "${SAMPLES_DIR}/$file"
    else
      echo "File not found: $file"
      exit 1
    fi
  done
else
  # Screen all sample files
  for file in "${SAMPLES_DIR}"/*.json; do
    screen "$file"
  done
fi
