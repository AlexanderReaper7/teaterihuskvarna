# Troubleshooting: Windows setup of the Docker stack

Logged during troubleshooting on 2026-09-25. Saved so the next person on
Windows (or the next APL cohort) doesn't have to debug the same thing from
scratch.

## Problem 1: Build failed on `mvnw`

```
mvnw: line 20: not found
mvnw: line 35: not found
mvnw: line 56: syntax error: unexpected word (expecting "in")
```

**Cause:** `mvnw` had Windows line endings (CRLF, `\r\n`) instead of Unix
line endings (LF, `\n`). `/bin/sh` inside the Linux container (the build
stage in `Dockerfile`) interprets `\r` as part of the command and crashes.
The repo was missing a `.gitattributes` file that enforces correct line
endings on checkout, so Git converted the file to CRLF locally on Windows.

**Quick local fix** (run if the build fails with this error):
```powershell
(Get-Content mvnw -Raw) -replace "`r`n", "`n" | Set-Content mvnw -NoNewline -Encoding utf8
docker compose up -d --build
```

**Permanent fix, at the repo level** (benefits every Windows user, not just
one machine):

1. Added `.gitattributes` at the repo root:
   ```
   * text=auto eol=lf
   mvnw text eol=lf
   *.sh text eol=lf
   ```
2. Renormalized existing files and committed:
   ```powershell
   git add --renormalize .
   git commit -m "Add .gitattributes: force LF line endings for mvnw and shell scripts"
   git push
   ```

## Problem 2: Could not reach the database from an external DB client

```
Connection to localhost:5432 refused. Check that the hostname and port are
correct and that the postmaster is accepting TCP/IP connections.
```

**Cause:** the production stack deliberately does **not** expose the
Postgres port to the host — only Traefik (the `proxy` service) is
published, on port 8000. That's a security choice, not a bug.
`docker compose ps` showed the `db` service with only `5432/tcp` (no
`0.0.0.0:` mapping).

**Fix:** a local, personal `compose.override.yaml` (NEVER committed, listed
in `.gitignore`):
```yaml
services:
  db:
    ports:
      - "5432:5432"
```

**Gotcha:** `.env` already had `COMPOSE_FILE` set explicitly:
```
COMPOSE_FILE=compose.yaml:compose.dev.yaml
```
Docker Compose only auto-loads `compose.override.yaml` when `COMPOSE_FILE`
is **not** set at all. With an explicit list, the file has to be added
manually, at the end:
```
COMPOSE_FILE=compose.yaml:compose.dev.yaml:compose.override.yaml
```
(The separator in this repo is `:`, even though a comment in `.env.example`
warns that Windows otherwise splits on `;` — `:` is what actually works
here.)

**After the fix**, `docker compose up -d` followed by `docker compose ps`
showed:
```
teaterihuskvarna-db-1   ...   0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
```
Connecting from an external DB client (DataGrip/DBeaver) then worked against
`localhost:5432` using the credentials from `.env`.

## things to remember

- **`.gitattributes` was missing from the repo entirely before this** — a
  real risk for anyone on Windows, not specific to one machine. Now added.
- **`COMPOSE_FILE` in `.env` disables auto-inclusion of
  `compose.override.yaml`.** Good to know for anyone who wants to connect an
  external client (database tool, GUI, etc.) locally without touching the
  shared compose files.
- The database is **deliberately** not exposed in production — the right
  call, but worth documenting where/how to add a local override, so people
  don't each reinvent the same fix separately, or worse, accidentally commit
  a production-unsafe change.