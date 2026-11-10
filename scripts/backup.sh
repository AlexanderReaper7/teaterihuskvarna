#!/bin/sh
# Writes a dump of the running database to backups/, named by date and time:
# docs/decisions/0007. The dump is made by pg_dump inside the db container, so
# its version always matches the server's, and in PostgreSQL's custom format,
# which pg_restore reads.
#
# The dump holds no sessions. spring_session.session_id is the session
# cookie's value in plain text, so a dump with those rows would log its reader
# in as everyone who was logged in when it was made. The rows of the two
# Spring Session tables are left out and the tables themselves are kept, so a
# restore gives a working schema where everyone logs in again:
# docs/runbook.md. restore-check.sh names the same two tables and expects them
# empty in a restored dump.
#
#   sh scripts/backup.sh
#
# The user and database name come from the container's own environment, which
# compose has already filled in from .env with the same defaults as
# compose.yaml. Nothing is parsed here and no password is needed: inside the
# container, pg_dump connects over the local socket, which the image trusts.
set -eu

cd "$(dirname "$0")/.."

# Git Bash on Windows would otherwise rewrite arguments that start with a slash.
export MSYS_NO_PATHCONV=1

mkdir -p backups
name="$(docker compose exec -T db sh -c 'printf %s "$POSTGRES_DB"')"
file="backups/${name}-$(date +%Y-%m-%dT%H%M).dump"

# A dump that fails halfway must not stay behind under a name that looks like a
# backup, so it is written beside its final name and renamed when complete.
trap 'rm -f "$file.partial"' EXIT
docker compose exec -T db sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc \
  --exclude-table-data=public.spring_session \
  --exclude-table-data=public.spring_session_attributes' > "$file.partial"

if [ ! -s "$file.partial" ]; then
  echo "backup.sh: the dump is empty, nothing was saved" >&2
  exit 1
fi
mv "$file.partial" "$file"
echo "backup.sh: wrote $file"