---
created: 2026-10-01
provenance: user
description: Non-null defaults and compiler enforcement for application and test Java sources.
---

# 0027: Non-null defaults and compiler enforcement

## Decision

Java references are non-null by default. Nullable values use JSpecify `@Nullable`, and the compiler must reject violations of those contracts. Adding annotations alone does not meet this requirement. The user requested both non-null defaults and compiler enforcement on 2026-10-01, in a separate branch based on main.

Generated entity IDs have nullable backing fields because Hibernate assigns them after construction. Their getters return non-null IDs and throw `IllegalStateException` before persistence. The user selected this contract on 2026-10-01.

## Agent notes

Every production and test package declares `@NullMarked` in its package information file. JSpecify package defaults do not apply to subpackages, so each package needs its own declaration. `RequireExplicitNullMarking` reports a compiler error when a new top-level type has no explicit default. No application or test package opts out with `@NullUnmarked`.

The [Maven compiler configuration](../../pom.xml) loads NullAway through Error Prone during both `compile` and `testCompile`. `NullAway:ERROR` makes unsafe null arguments, returns and dereferences fail compilation. `JSpecifyMode=true` also checks generic element contracts. `JSpecifyUnrecognizedAnnotationLocation:ERROR` rejects annotations in locations where JSpecify gives them no meaning, such as a local variable's root type. Other Error Prone checks are disabled; [Checkstyle, SpotBugs and PMD](0013-three-static-analysis-gates.md) retain their existing responsibilities.

The compiler forks javac with the module access required by [Error Prone's installation instructions](https://errorprone.info/docs/installation). Compiler plugin 3.16.0, Error Prone 2.50.0, NullAway 0.14.2 and JSpecify 1.0.1 were the latest stable Maven Central releases when queried on 2026-10-01. The checker dependencies are on the annotation processor path, outside the application's runtime dependencies.

`ExternalInitAnnotations=jakarta.persistence.Entity` exempts only the zero-argument JPA constructor from initializing required fields. Hibernate populates those fields after invoking that constructor. Other constructors and entity method bodies remain checked. Generated IDs are explicitly nullable, rather than covered by an initialization suppression. [EntityIdTest](../../src/test/java/se/teaterihuskvarna/architecture/EntityIdTest.java) checks every entity's nullable ID field and getter behavior. The integration tests exercise actual Hibernate persistence.

Checks of validated form values, persisted query results and previously validated links use `Objects.requireNonNull` where NullAway cannot infer the preceding validation. These checks fail if that assumption stops holding. JDBC row maps declare nullable values because SQL columns can be null; session and cookie test helpers check lookups before using them.

The [compiler probes](../../scripts/check-nullability.sh) use the same Maven compiler configuration with isolated sources and output. They accept guarded nullable values and nullable collection elements, then require compiler failures for unsafe returns, arguments, dereferences, generic element contracts, invalid annotation locations and missing package defaults. [Build CI](../../.github/workflows/build.yml) runs the probes after `verify`. Run them locally with `sh scripts/check-nullability.sh`; the fixtures and compiler diagnostics stay under `target/nullability-probe`. They never replace application sources or compiled application classes.

The checker covers repository Java sources compiled by Maven. Hibernate initialization, Spring binding and third-party code execute outside that analysis. JTE's generated template classes use its separate compiler. These boundaries do not establish runtime null safety, and a successful build is not a proof that no null can enter through a framework. [NullAway's JSpecify documentation](https://github.com/uber/NullAway/wiki/JSpecify-Support) also records limitations in generic inference.

Rolling back the compiler configuration and package defaults restores the former build behavior without a database migration. The nullable field annotations and explicit ID getter contract can remain independently.
