#!/usr/bin/env bash
set -euo pipefail

MIMOPE_CLIENTS=10 MIMOPE_DURATION_MS=60000 node scripts/load-test.mjs
MIMOPE_CLIENTS=25 MIMOPE_DURATION_MS=120000 node scripts/load-test.mjs
MIMOPE_CLIENTS=50 MIMOPE_DURATION_MS=300000 node scripts/load-test.mjs
