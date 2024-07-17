#!/usr/bin/env bash
# Sends a GitHub-style sample webhook to a HookDock receive URL.
#
#   ./scripts/send-sample-webhook.sh http://localhost:8080/in/<token>
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "usage: $0 <receive-url>" >&2
  exit 1
fi

delivery_id=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)

curl -sS -X POST "$1" \
  -H "Content-Type: application/json" \
  -H "User-Agent: GitHub-Hookshot/sample" \
  -H "X-GitHub-Event: push" \
  -H "X-GitHub-Delivery: ${delivery_id}" \
  --data-binary @- <<JSON
{
  "ref": "refs/heads/main",
  "before": "0000000000000000000000000000000000000000",
  "after": "1111111111111111111111111111111111111111",
  "repository": { "full_name": "example/demo", "private": false },
  "pusher": { "name": "example-user" },
  "commits": [
    { "id": "1111111", "message": "Sample commit", "added": [], "removed": [], "modified": ["README.md"] }
  ]
}
JSON
echo
