# 0008 — The whole application runs in containers

2026-09-22

## Decision

Every running piece is a container on one host in an EU region: a reverse proxy, the Spring Boot application, and PostgreSQL. The same compose file describes local and production, differing only in an environment file and which image tags are pinned.

This extends [0007](0007-postgres-in-a-container.md), which containerised the database. The application follows it for the same reason: what runs in production is then the artefact that was tested, not a jar copied onto a host whose Java version nobody checked.

## What this forces into the open

A managed platform would have terminated TLS, held the registry and handled restarts. Containerising everything makes each of those an explicit component, and leaving any of them implicit is how a system arrives at week 12 without HTTPS.

**TLS is now ours.** A reverse proxy container terminates it and obtains certificates from Let's Encrypt automatically. Caddy does this in a handful of lines of configuration and renews without a cron job. This is a component the plan did not previously name, and it is the one that would otherwise have been discovered late.

**The registry is GHCR.** Container storage and bandwidth there are free for private images as well as public, so the repository does not have to be made public to hold images. GitHub's own wording is "currently free", which is a hedge worth remembering rather than acting on.

**Images are tagged with the commit sha, not only `latest`.** Two things depend on this: rolling back is pulling the previous tag, and the question "which version is running" has an answer. A `latest`-only setup can answer neither.

## Building the image

A layered `Dockerfile` with a pinned JRE base image, not `spring-boot:build-image`.

Buildpacks are the tempting option because they need no Dockerfile at all. They lose here on the project's own maintenance model. Routine maintenance in this project is approving a green Dependabot pull request, and Dependabot reads a `FROM` line but cannot see the JDK buried inside a buildpack. Choosing buildpacks means the base operating system and the Java runtime update on somebody else's schedule, invisibly, which is the opposite of what the rest of the setup is arranged to do.

The secondary gains are real but not the argument: a layered Dockerfile lands near 100 MB against roughly 300 MB for the Ubuntu-based buildpack, and layer caching means a code change pushes kilobytes rather than the whole image.

The cost is about twenty lines of `Dockerfile` to own.

## Deploying

GitHub Actions builds and pushes the image on a green build of `main`, then connects over SSH and runs `docker compose pull && docker compose up -d`. Deployment is therefore something CI does deliberately, at a moment recorded in the Actions log.

The alternative is an agent on the host polling the registry, such as Watchtower. Rejected: it deploys whatever is newest whenever it notices, with nobody watching, and pointing that mechanism at a database container is a way to lose data to an unattended major version upgrade.

## Backups against a containerised database

From [0007](0007-postgres-in-a-container.md), unchanged in substance but concrete now:

- PostgreSQL's data lives in a named volume, not in the container's writable layer. A container that is replaced on every deployment must not be where the member register lives.
- A scheduled job runs `pg_dump` and ships the result, encrypted, to storage on another machine in an EU region.
- The restore has to have been run into an empty database and checked before launch.

## What it costs to undo

Moving to a managed platform later means keeping the image and deleting the compose file, the proxy and the backup job. The image is the portable part, so this is the direction that stays cheap.
