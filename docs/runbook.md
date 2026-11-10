---
created: 2026-10-18
provenance: agent
description: Which environment file production starts from, and how to back up the database, check that a backup restores and restore after data loss.
---

# Runbook

The system plan asks for a runbook covering deployment, update, rollback, backup and restore ([projektplan.md](projektplan.md), "Ownership and maintenance are release work"). It covers the production environment file, backup and restore so far. PostgreSQL runs in a container, so backups are ours to make ([0007](decisions/0007-postgres-in-a-container.md)).

## The environment file on the production host

On the production host, `.env` starts as a copy of [`.env.production.example`](../.env.production.example), never of [`.env.example`](../.env.example):

```sh
cp .env.production.example .env
docker compose config --quiet
```

[`.env.example`](../.env.example) is the local development stack. Its `COMPOSE_FILE` line loads [`compose.dev.yaml`](../compose.dev.yaml), which turns on the `dev` profile, where `POST /dev/login` logs in as any administrator without a credential and `/dev` lists every address. The production example has no such line, so `docker compose` reads [`compose.yaml`](../compose.yaml) alone. If the line is there anyway, the application refuses to start: the `dev` profile together with a `SITE_URL` stops it with a message naming the lines to remove ([`RealSiteGuard`](../src/main/java/se/teaterihuskvarna/development/RealSiteGuard.java)). It holds every variable [`compose.yaml`](../compose.yaml) reads except the three that only the image build uses, and each one's comment says what it is and where its value comes from.

`docker compose config --quiet` prints nothing when compose can fill in the stack, and fails while `POSTGRES_PASSWORD` is empty. The application refuses to start while `SITE_URL`, `MAIL_FROM`, `BANKGIRO`, `SANITY_PROJECT_ID`, `SANITY_DATASET_NAME` or a `BREVO_` value is empty, and on a database with no administrator while `FIRST_ADMINISTRATOR_EMAIL` or `FIRST_ADMINISTRATOR_NAME` is. Nothing refuses an empty `SITE_HOST` or empty `SMTP_` values, so check those by eye.

Git ignores `.env` and every other `.env.*` file except the two examples, so a copy named `.env.production` cannot be committed by accident.

## Back up and restore

Both scripts work against the running stack, from the repository root, in a POSIX shell. On Windows that is Git Bash.

### Back up

```sh
sh scripts/backup.sh
```

[`scripts/backup.sh`](../scripts/backup.sh) runs `pg_dump` inside the `db` container, so its version always matches the server's, and writes `backups/teaterihuskvarna-2026-10-20T1430.dump`, named by date and time, in PostgreSQL's custom format. It prints `backup.sh: wrote <file>`. A dump that fails halfway leaves no file behind. Git and the image build both ignore `backups/`, since a dump from production holds real personal data.

### Check that a backup restores

```sh
sh scripts/restore-check.sh backups/teaterihuskvarna-2026-10-20T1430.dump
```

[`scripts/restore-check.sh`](../scripts/restore-check.sh) restores the dump into a new database, `restore_check`, and never touches the real one. It compares the row count of every table with the live database, including `flyway_schema_history`, and deletes the check database afterwards:

```
table                                          live   restored  result
account                                           5          5  ok
flyway_schema_history                             2          2  ok
restore-check.sh: every table has the same row count, the dump restores
```

It exits with 0 when every count matches, and with 1 when a count differs, a table is missing or the dump cannot be read. The live database is counted as it is now, so a row added after the dump shows as `DIFFERENT`. Run it right after [`backup.sh`](../scripts/backup.sh).

### Restore after data loss

Stop the application first, so nothing writes while the database is replaced:

```sh
docker compose stop app
docker compose exec -T db sh -c 'exec pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --exit-on-error' < backups/teaterihuskvarna-2026-10-20T1430.dump
docker compose start app
```

`--clean --if-exists` drops each object in the dump before it is created again, so the command also works on a database with some tables left. If the whole volume is gone, `docker compose up -d db` creates an empty database, and the same command without `--clean --if-exists` fills it. Flyway sees its own history in the restored data and applies nothing twice.

## Not covered yet

Each of these waits on a decision:

- Running the backup daily. It needs the production server.
- Sending dumps, encrypted, to another machine in the EU ([0008](decisions/0008-everything-in-containers.md)). It needs a storage provider.
- Deleting old dumps. It depends on the board's retention period, question C25 in [the second meeting](meetings/2026-09-30-meeting-2.md#c25-should-the-end-of-june-removal-of-unpaid-members-delete-their-data-and-how-long-may-backups-keep-it), still deferred.
