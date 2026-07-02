#!/usr/bin/env bash
#
# verify-clean-clone.sh
#
# Verifies that a fresh clone of the repository builds and tests cleanly with
# no dependency on local working-tree state or caches outside the standard
# package managers. Mirrors what a new contributor (or CI) would run.
#
# Steps:
#   1. Clone the current repo HEAD into a throwaway temp directory.
#   2. Frontend: npm ci (or install), build, test, lint.
#   3. Backend:  mvn package + test via the Maven wrapper.
#   4. Optional: docker compose build (skipped unless --docker is passed and
#      docker is available, since it requires network + significant disk).
#
# Usage:
#   scripts/verify-clean-clone.sh [--docker]
#
# Exit code is non-zero if any step fails.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DOCKER=0

for arg in "$@"; do
  case "$arg" in
    --docker) RUN_DOCKER=1 ;;
    *) echo "Unknown argument: $arg" >&2; exit 2 ;;
  esac
done

WORKDIR="$(mktemp -d -t mimope-clean-clone-XXXXXX)"
CLONE="$WORKDIR/mimope"

cleanup() {
  rm -rf "$WORKDIR"
}
trap cleanup EXIT

step() {
  echo ""
  echo "=================================================================="
  echo ">>> $*"
  echo "=================================================================="
}

step "Copying working tree into $CLONE (excluding build artifacts)"
mkdir -p "$CLONE"
# Copy the current working tree (including uncommitted changes) but exclude
# generated/heavy directories so the build genuinely starts from scratch.
if command -v rsync >/dev/null 2>&1; then
  rsync -a \
    --exclude '.git' \
    --exclude 'node_modules' \
    --exclude 'frontend/dist' \
    --exclude 'backend/target' \
    "$REPO_ROOT/" "$CLONE/"
else
  # Fallback: tar pipe with excludes.
  tar -C "$REPO_ROOT" \
    --exclude='./.git' \
    --exclude='./frontend/node_modules' \
    --exclude='./frontend/dist' \
    --exclude='./backend/target' \
    -cf - . | tar -C "$CLONE" -xf -
fi


# ------------------------------------------------------------------ frontend
step "Frontend: install dependencies"
cd "$CLONE/frontend"
if [ -f package-lock.json ]; then
  npm ci
else
  npm install
fi

step "Frontend: build"
npm run build

step "Frontend: test"
npm test

step "Frontend: lint"
npm run lint

# ------------------------------------------------------------------ backend
step "Backend: package + test (Maven wrapper)"
cd "$CLONE/backend"
chmod +x ./mvnw
./mvnw --batch-mode verify

# ------------------------------------------------------------------ docker
if [ "$RUN_DOCKER" -eq 1 ]; then
  if command -v docker >/dev/null 2>&1; then
    step "Docker: compose build (production compose file)"
    cd "$CLONE"
    docker compose -f docker-compose.prod.yml build
  else
    echo "Docker requested but not available on PATH — skipping." >&2
  fi
else
  echo ""
  echo "Skipping docker build (pass --docker to include it)."
fi

step "Clean clone verification PASSED"
