#!/bin/sh
# Runs the end-to-end suite: builds the application, starts it with its own
# database and Mailpit under a compose project named after this checkout, runs
# Playwright in its docker image inside the application's network namespace,
# and removes the stack and its image again. Several checkouts can run it at
# once. Why this way: docs/decisions/0017.
#
#   e2e/run.sh                      every test
#   e2e/run.sh tests/login.spec.ts  one file; arguments go to `playwright test`
#   E2E_KEEP=1 e2e/run.sh           leave the stack running afterwards, published
#                                   on http://localhost:55556
#
# The report is in e2e/playwright-report, and a failed test's trace in
# e2e/test-results.
set -eu

cd "$(dirname "$0")/.."

# Shell variables win over .env when compose fills in ${...}, so the e2e stack
# never takes the dev stack's database settings.
export POSTGRES_DB=teaterihuskvarna POSTGRES_USER=teaterihuskvarna POSTGRES_PASSWORD=e2e

# One project per checkout, from its directory name, lower-cased to what compose
# accepts in a project name.
project="teaterihuskvarna-e2e-$(basename "$PWD" | tr 'A-Z' 'a-z' | tr -c 'a-z0-9_\n-' '-')"
files="-f compose.yaml -f compose.dev.yaml -f e2e/compose.e2e.yaml"
if [ -n "${E2E_KEEP:-}" ]; then
  files="$files -f e2e/compose.e2e-keep.yaml"
fi

compose() {
  # $files is unquoted on purpose: it is a list of flags without spaces.
  # shellcheck disable=SC2086
  docker compose -p "$project" --project-directory . $files "$@"
}

if [ -z "${E2E_KEEP:-}" ]; then
  trap 'compose down -v --rmi local --remove-orphans >/dev/null 2>&1' EXIT
fi

# A fresh database each run, which also resets the rate limit's counts.
compose down -v --remove-orphans >/dev/null 2>&1 || true
compose up -d --build --wait app db mail

attempt=0
until compose exec -T app wget -q -O /dev/null http://localhost:55556/ 2>/dev/null; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 90 ]; then
    echo "The application did not answer on port 55556 within 3 minutes." >&2
    compose logs app | tail -50 >&2
    exit 1
  fi
  sleep 2
done

# npm's cache lives in the volume scripts/maven.sh uses for Maven's, so a run
# does not download every package again. It is handed to the user the suite
# runs as first.
cache=teaterihuskvarna-cache
# The image's tag must match @playwright/test in e2e/package.json, or the
# browsers it carries are not the ones the library expects.
playwright=mcr.microsoft.com/playwright:v1.63.0-noble
docker run --rm -v "$cache":/cache "$playwright" chown "$(id -u):$(id -g)" /cache

docker run --rm --network "container:$(compose ps -q app)" --ipc=host --init \
  --user "$(id -u):$(id -g)" -e HOME=/cache -e CI="${CI:-}" \
  -v "$cache":/cache -v "$PWD":/w -w /w/e2e \
  "$playwright" \
  sh -c 'npm ci --no-audit --no-fund --loglevel=error && npx playwright test "$@"' sh "$@"
