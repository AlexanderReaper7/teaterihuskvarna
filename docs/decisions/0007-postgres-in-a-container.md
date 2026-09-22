# 0007 — PostgreSQL runs in a container, not as a managed service

2026-09-22

## Decision

PostgreSQL runs as a container on the same host as the application, from the same pinned image used locally. The plan's earlier assumption of "a cloud provider with an EU region and a managed PostgreSQL service" is withdrawn.

## Why

Cost, and it is the largest cost lever in the project. Managed PostgreSQL starts around 25 USD a month on the major providers and climbs with storage and backup retention. A single VPS in an EU region runs the application, the database and the mail catcher for a few euros.

Microsoft's nonprofit Azure grant of 2 000 USD a year would cover managed PostgreSQL comfortably. It is also not granted, requires the association to validate as a nonprofit, does not roll over, and expires 90 days after issuance if not activated. Designing around a credit nobody has applied for yet means the hosting bill arrives as a surprise if the application fails. Designing for a cheap host means the credit, if it lands, is headroom rather than a dependency.

Using the same image in both places also removes a class of bug the original scaffolding plan already flagged, where CI and a laptop disagree about the database because one of them pulled `latest`.

## What this costs, and the obligation it creates

Backups, patching and failover stop being the provider's job. Failover is acceptable to lose: this is an association website, and an hour of downtime costs nothing that matters. Patching is the routine already in place, a pinned image and a Dependabot bump to approve.

Backups are different, and this decision is not complete without them. The database holds the entire member register. A managed service would have made point-in-time recovery somebody else's problem; a container makes it ours, and the failure mode is losing the register with no way back.

So the following are requirements of this decision, not follow-up work:

- A scheduled `pg_dump` to storage on a different machine, in an EU region.
- The dump is encrypted at rest, because it contains names, addresses, phone numbers and e-mail addresses.
- **A restore has actually been run from a backup, into an empty database, and the result checked.** A backup nobody has restored is not a backup, it is a file. This is the same rule the rest of the project applies to tests: a guard that has never been seen to work is not known to work.
- Backup retention is bounded and written down, because a backup holding personal data is subject to the same retention rule as the database. How long member data is kept after a lapsed membership is still an open question for the board, and the answer applies to backups too.

## What it costs to undo

Moving to a managed service later is a `pg_dump` and a `pg_restore` against a new connection string, plus deleting the backup cron. The application does not know the difference. Nothing here is one-way.
