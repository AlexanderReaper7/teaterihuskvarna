#!/bin/sh
# Proves that a dump can be restored: restores it into a new, empty database
# and compares the row count of every table with the live database. Exits 0 only
# when every count matches. Never touches the live database: docs/decisions/0007.
#
#   sh scripts/restore-check.sh backups/teaterihuskvarna-2026-10-20T1430.dump
#
# The counts are compared against the live database as it is now, so a row added
# since the dump was taken shows as a difference. Run it right after backup.sh,
# or expect that difference.
set -eu

cd "$(dirname "$0")/.."

# Git Bash on Windows would otherwise rewrite arguments that start with a slash.
export MSYS_NO_PATHCONV=1

if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
  echo "usage: sh scripts/restore-check.sh <dump file>" >&2
  exit 2
fi
dump="$1"
check=restore_check

# psql in the db container, as the container's own user, against the database
# named by the first argument. The SQL comes from standard input.
psql_in() {
  docker compose exec -T db sh -c 'exec psql -U "$POSTGRES_USER" -d "$1" -v ON_ERROR_STOP=1 -At' sh "$@"
}

live="$(docker compose exec -T db sh -c 'printf %s "$POSTGRES_DB"')"
if [ "$live" = "$check" ]; then
  echo "restore-check.sh: the live database is called $check, refusing to go on" >&2
  exit 2
fi

tmp="$(mktemp -d)"
cleanup() {
  psql_in postgres <<SQL > /dev/null 2>&1 || true
DROP DATABASE IF EXISTS $check WITH (FORCE);
SQL
  rm -rf "$tmp"
}
trap cleanup EXIT

# One line per table in public: name|rows. count(*) per table through
# query_to_xml, since a plain query cannot count tables it has to look up.
counts() {
  psql_in "$1" <<'SQL'
SELECT table_name || '|' || (xpath('/row/c/text()',
         query_to_xml(format('SELECT count(*) AS c FROM %I.%I', table_schema, table_name), false, true, '')))[1]::text
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY table_name;
SQL
}

psql_in postgres <<SQL > /dev/null
SET client_min_messages = warning;
DROP DATABASE IF EXISTS $check WITH (FORCE);
CREATE DATABASE $check;
SQL

docker compose exec -T db sh -c 'exec pg_restore -U "$POSTGRES_USER" -d "$1" --no-owner --exit-on-error' sh "$check" < "$dump"

counts "$live" > "$tmp/live"
counts "$check" > "$tmp/restored"

if [ ! -s "$tmp/live" ] || [ ! -s "$tmp/restored" ]; then
  echo "restore-check.sh: found no tables to compare" >&2
  exit 1
fi

awk -F'|' '
  FNR == NR { live[$1] = $2; seen[$1] = 1; next }
  { restored[$1] = $2; seen[$1] = 1 }
  END {
    for (t in seen) {
      l = (t in live) ? live[t] : "-"
      r = (t in restored) ? restored[t] : "-"
      print t "|" l "|" r "|" ((l == r) ? "ok" : "DIFFERENT")
    }
  }
' "$tmp/live" "$tmp/restored" | sort > "$tmp/table"

printf '%-40s %10s %10s  %s\n' table live restored result
awk -F'|' '{ printf "%-40s %10s %10s  %s\n", $1, $2, $3, $4 }' "$tmp/table"

different="$(grep -c '|DIFFERENT$' "$tmp/table" || true)"
if [ "$different" -ne 0 ]; then
  echo "restore-check.sh: $different table(s) differ, the dump does not match the live database" >&2
  exit 1
fi
echo "restore-check.sh: every table has the same row count, the dump restores"