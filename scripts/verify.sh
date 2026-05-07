#!/usr/bin/env bash
#
# verify.sh — Local Verification Suite
#
# Runs the test / typecheck / lint set required by the Definition of Done
# (see CLAUDE.md § Release Discipline and docs/release/checklist.md).
#
# Usage:
#   scripts/verify.sh           # quick: backend test + frontend typecheck/lint/test
#   scripts/verify.sh --full    # quick + backend integration test + backend/frontend build
#   scripts/verify.sh --help    # show help
#
# Exit codes:
#   0   all checks passed
#   1   a check failed
#   2   pre-flight error (missing dir, missing deps, bad arg)

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

FULL_MODE=false

print_help() {
  cat <<'EOF'
verify.sh — Local Verification Suite

Usage:
  scripts/verify.sh           Quick mode (default).
                              Runs backend unit tests, frontend typecheck,
                              frontend lint, and frontend unit tests.

  scripts/verify.sh --full    Full mode.
                              Quick mode plus backend integration tests
                              (requires Docker) and backend / frontend build.

  scripts/verify.sh --help    Show this help.

E2E (Cucumber + Playwright) is not run here; see e2e/README.md.
Run from anywhere; the script switches to the repo root automatically.
EOF
}

for arg in "$@"; do
  case "$arg" in
    --full)
      FULL_MODE=true
      ;;
    -h|--help)
      print_help
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      echo "Run scripts/verify.sh --help for usage." >&2
      exit 2
      ;;
  esac
done

# === helpers ===

section() {
  printf '\n'
  printf '============================================================\n'
  printf '  %s\n' "$1"
  printf '============================================================\n'
}

ensure_dir() {
  if [[ ! -d "$1" ]]; then
    echo "Error: directory '$1' not found (expected under $ROOT_DIR)" >&2
    exit 2
  fi
}

ensure_executable() {
  if [[ ! -x "$1" ]]; then
    echo "Error: '$1' is not executable. Run 'chmod +x $1'." >&2
    exit 2
  fi
}

ensure_frontend_deps() {
  if [[ ! -d "frontend/node_modules" ]]; then
    echo "Error: frontend/node_modules missing." >&2
    echo "Run: (cd frontend && npm install)" >&2
    exit 2
  fi
}

# === pre-flight ===

section "Pre-flight"
ensure_dir "backend"
ensure_dir "frontend"
ensure_executable "backend/gradlew"
ensure_frontend_deps
echo "Repo root:  $ROOT_DIR"
echo "Mode:       $([[ $FULL_MODE == true ]] && echo full || echo quick)"

# === backend ===

section "Backend: unit tests"
( cd backend && ./gradlew test )

if $FULL_MODE; then
  section "Backend: integration tests (requires Docker)"
  ( cd backend && ./gradlew quarkusIntTest )

  section "Backend: build"
  ( cd backend && ./gradlew build -x test -x quarkusIntTest )
fi

# === frontend ===

section "Frontend: typecheck"
( cd frontend && npm run typecheck )

section "Frontend: lint"
( cd frontend && npm run lint )

section "Frontend: unit tests"
( cd frontend && npm test )

if $FULL_MODE; then
  section "Frontend: build"
  ( cd frontend && npm run build )
fi

# === summary ===

section "Verification Summary"
echo "All checks passed."
if ! $FULL_MODE; then
  echo "Tip: re-run with --full to also run backend integration tests + build."
fi
