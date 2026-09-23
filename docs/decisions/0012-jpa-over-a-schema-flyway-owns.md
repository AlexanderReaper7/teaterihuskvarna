# 0012: JPA over a schema Flyway owns

2026-09-22

## Decision

Spring Data JPA, with `spring.jpa.hibernate.ddl-auto: validate`. Flyway owns every table, per [0010](0010-flyway-for-migrations.md); Hibernate never creates or alters one. `open-in-view` is off.

String columns are `VARCHAR(n)`. Not `TEXT`, and never `CHAR(n)`.

Ids use `GENERATED ALWAYS AS IDENTITY`, not sequences.

## Where this came from

[AlexanderReaper7/Lexicon-flight-reservation](https://github.com/AlexanderReaper7/Lexicon-flight-reservation) is the same stack a fortnight earlier: Spring Boot 4.1.1, PostgreSQL, Flyway, JPA at `validate`. Its `docs/decisions/0011-writing-the-schema-by-hand.md` records three rules that each cost a debugging session there. Two are adopted here unchanged, one is not, and the disagreement is the part worth writing down.

Adopted: **the Flyway module**, `spring-boot-flyway` rather than only `flyway-core`. This project hit that independently, and the integration test is what caught it. Adopted: **`VARCHAR`, not `CHAR`**, because PostgreSQL reports `CHAR(n)` back as `bpchar` and blank-pads values, so a string compares equal in the database and unequal in Java. `TEXT` is avoided for a weaker reason, that every Hibernate-mapped column in the other project is `VARCHAR(n)` and `TEXT` under `validate` is untested ground in this codebase.

## Where this diverges: IDENTITY, not sequences

The other project uses explicit sequences with `INCREMENT BY 50`, because Hibernate cannot JDBC-batch inserts under `IDENTITY` and its schedule generator writes about 25,000 flights in one transaction.

There is no such write path here. The largest insert this system will ever do is a membership application, one row at a time, for an association with a few hundred members. The performance argument is real there and absent here.

What remains is the cost that record lists under consequences: `INCREMENT BY 50` and Hibernate's `allocationSize` are declared in two files, must stay equal, and nothing checks that they do. Taking on an unchecked coupling to win batching that nothing in this system needs is the wrong trade.

## What `validate` does and does not catch

Measured on 2026-09-22 against this codebase, because the reason for choosing `validate` is worth checking rather than assuming.

It catches a mapped column that does not exist. Adding a `nickname` field to `Member` with no matching column failed the build:

```
SchemaManagementException: Schema validation: missing column [nickname] in table [member]
```

It does not catch a length mismatch. Changing `@Column(length = 254)` to `255` against a `VARCHAR(254)` column built and tested green.

So `validate` is a guard against a forgotten migration, which is the failure it was chosen for. It is not a guard against a column declared at the wrong width. Lengths in the entities and lengths in the migrations are kept equal by hand, and that is the known gap in this arrangement.

## What it costs to undo

Dropping to `JdbcClient` means deleting the entities and writing the queries by hand; the schema is unaffected either way, because Flyway already owns it. Going the other direction, letting Hibernate generate the schema, is the arrangement this project and the other one both moved away from.
