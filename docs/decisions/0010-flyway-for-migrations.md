# 0010: Flyway for schema migrations

2026-09-22

## Decision

Flyway 13.7.0, `flyway-core` with `flyway-database-postgresql`, migrations written as plain `.sql` files under `src/main/resources/db/migration`. Not Liquibase.

## Why this is decided before the first table exists

The schema is already known to be provisional. [projektplan.md](../projektplan.md) requires the fee rule to be checked against real anonymised bankgiro examples before reconciliation is built, and the retention period in [open-questions.md](../open-questions.md) decides what deleting a former member actually does. Both change the schema after it holds data.

Picking a migration tool after the first table is created means the first table was created by something else, and the first migration is then a lie about where the schema came from.

## The comparison

| | Flyway 13.7.0 | Liquibase 5.0.4 |
| --- | --- | --- |
| Latest release | 2026-09-15 | 2026-08-20 |
| Boot 4.1.1 auto-configuration | first party | first party |
| Migration format | SQL | XML, YAML, JSON or SQL changelogs |
| Database portability | per-database SQL | changelog abstracts over dialects |
| Declared rollback | paid tier | `rollback` blocks, automatic for some change types |

Portability looks like the deciding column and is not. [0007](0007-postgres-in-a-container.md) and [0008](0008-everything-in-containers.md) pin one PostgreSQL image and run it locally, in CI and in production. There is no second engine to be portable to, so Liquibase's main advantage is carried and never used.

What remains is who reads the files. The stated maintenance budget is a few hours a year and the maintainer after week 12 is the first question in [open-questions.md](../open-questions.md). A migration that reads `ALTER TABLE member ADD COLUMN ...` is legible to anyone who can read the database. A changelog is a second notation to learn before the first question can be answered.

## Rollback is the real objection

Flyway Community has no `undo`. Naming what replaces it matters more than noting the gap.

Structure is corrected by a forward migration: the mistake is fixed by writing the next file, not by reversing the last one. Data loss is a different failure, and its recovery path is the restore that [0007](0007-postgres-in-a-container.md) already requires to be proven before launch.

Liquibase's rollback is also weaker than the table suggests. It is automatic only where the inverse of a change is unambiguous, hand-written otherwise, and a hand-written rollback nobody has executed is exactly the never-run guard this project rejects everywhere else.

## What it costs to undo

Liquibase adopts an existing schema through a baseline and `changelog-sync`. The cost is real, bounded, and paid once. It would fall due only if a second database engine arrives, which is the single event that would justify paying it.
