---
created: 2026-09-22
provenance: unreviewed
description: Which static analysis fails the build, and how Java doc comments are written.
---

# 0013: Three static analysis gates, and Markdown doc comments

## Decision

Checkstyle, SpotBugs and PMD run on every build and all three fail it. Checkstyle runs at `validate`, before anything compiles, so `docker build` runs it too. SpotBugs and PMD need bytecode and run at `verify`, after the tests, which puts them outside the image build. `./mvnw verify` is the command that runs all three; that is what CI runs.

Doc comments are `///` Markdown (JEP 467), never `/** */`. Checkstyle enforces the style. Nothing enforces the contents, and the reason is below.

## Where this came from

[AlexanderReaper7/Lexicon-flight-reservation](https://github.com/AlexanderReaper7/Lexicon-flight-reservation), the same stack a fortnight earlier, carries all of this: the three gates in `backend/pom.xml`, the ruleset in `backend/checkstyle.xml`, and 102 source files written with `///`. Its reasoning is in its `docs/decisions/0010-static-analysis-gates.md` and `0014-pin-pmd-ahead-of-the-plugin.md`. The configuration here is a port, not a rewrite.

Its own argument for doing this early applies harder here. Eight Java files is the smallest this codebase will ever be. The member register alone adds fees, offers, registrations, volunteer bookings and login tokens; retrofitting a ruleset across those later is a day of work instead of the hour it took now.

Versions were resolved from Maven Central on 2026-09-22 rather than copied: `maven-checkstyle-plugin` 3.6.0 with Checkstyle 14.1.0, `spotbugs-maven-plugin` 4.10.4.1, `maven-pmd-plugin` 3.28.0 with PMD pinned to 7.27.0.

## PMD is pinned ahead of the plugin

`maven-pmd-plugin` 3.28.0 bundles PMD 7.17, which cannot read Java 25 class files. The failure is quiet: PMD resolves types from the compiled classes on the auxclasspath, every lookup fails down to `java/lang/Object`, syntactic rules still fire, and the build looks healthy while every type-aware rule checks nothing. `pmd-core` and `pmd-java` are pinned to 7.27.0 in the plugin's own `<dependencies>`, and `pmd.target.jdk` is tied to `java.version` so a Java upgrade cannot leave PMD behind.

The two pins must move together. This is the other project's [0014](https://github.com/AlexanderReaper7/Lexicon-flight-reservation/blob/main/docs/decisions/0014-pin-pmd-ahead-of-the-plugin.md), carried over before it could cost a second debugging session.

## The Javadoc rules are gone, because they cannot see a `///` comment

This is the one place the port does not work, and it is the reason this record exists rather than a commit message.

The reference ruleset keeps `JavadocMethod` with `allowMissingParamTags=false` and calls it the expensive half, kept on purpose. Checkstyle 14.1.0 has no Markdown Javadoc parser. It reads a `///` comment as an ordinary comment and validates nothing inside it.

Measured on 2026-09-22, same constructor, same missing tag, same ruleset:

```java
/** Makes a household. */          ->  error: Expected @param tag for 'name'.
public Household(String name)

/// Makes a household.             ->  0 violations
public Household(String name)
```

`JavadocType` does not require a class comment at all in this Checkstyle version; presence checks moved to `MissingJavadocType` years ago, and the reference project does not use it either. So of the two Javadoc rules, one was inert under `///` and the other was inert regardless.

Both are removed. A rule that reports success while checking nothing is worse than no rule, because the green result gets taken as evidence. The same conclusion PMD's version pin is about.

What replaces them is narrower and honest: a `RegexpSingleline` that rejects `/** */`, so the comment style itself is a gate. `@param` and `@return` discipline is now convention, checked at review, not by the build.

**This finding applies to the reference project too.** Its `JavadocMethod` rule has been inert since its sources moved to `///`, and its 0010 still describes it as the expensive half being kept. Worth telling that repository.

### The option not taken

`javadoc -Xdoclint:all` does parse `///` correctly, and `-Werror` turns its findings into a build failure. Measured on the probe: it reports `no @param for name` and `no @return` from Markdown comments exactly as it does from `/** */`.

It is not used because doclint puts "this method has no comment" and "this comment omits a `@param`" in the same `missing` group, with no way to take the second without the first. Against the current sources that is 45 warnings, mostly `no comment` on entity getters and `no main description` on comments that are only a `@param` line. That is a stricter regime than the reference project has, and adopting it by accident while trying to match that project is the wrong way to arrive at it.

Reopening this means turning on `-Xdoclint:all -Werror` in `maven-compiler-plugin` and documenting every public member. The measurement above is what to weigh it against.

## The one SpotBugs exclusion

`EI_EXPOSE_REP` and `EI_EXPOSE_REP2` on `se.teaterihuskvarna.member`. Both fired for real on `Member.getHousehold` and `Member.setHousehold` before the exclusion existed.

Returning a copy of an associated entity would hand back an object Hibernate does not manage: writes to it would never reach the database, and `==` against the same row loaded elsewhere in the persistence context would be false. The identity is the association.

The detector cannot tell an entity reference from a mutable value, so the exclusion is scoped to the package. What makes that safe is a check that can be run: every field in that package is a `Long`, a `String`, an `Instant` or another entity, and no getter returns a collection. The justification lives in `spotbugs-exclude.xml` next to the exclusion, because an exclusion whose reasoning is in another file is one nobody checks.

Controllers and services stay under the detector. A second entity package gets its own entry only after that check has been run against it.

## Checkstyle said how many, not where

`maven-checkstyle-plugin` has two ways to fail a build and they are not interchangeable. The plugin's own help puts it plainly: `failsOnError` fails "immediately after running Checkstyle, before checking the log for logViolationsToConsole. If you want to use logViolationsToConsole, use failOnViolation instead."

This project had `failsOnError`. So every Checkstyle failure read:

```
Failed during checkstyle execution: There is 1 error reported by Checkstyle 14.1.0
```

and never named the file or the line. Finding the violation meant opening `target/checkstyle-result.xml`. Measured on 2026-09-22 by introducing one `SingleSpaceSeparator` violation; `failOnViolation` reports the same violation as:

```
[ERROR] src/main/java/se/teaterihuskvarna/web/HomeController.java:[17,34] (whitespace)
        SingleSpaceSeparator: Use a single space to separate non-whitespace characters.
```

The configuration is now `failOnViolation` with `logViolationsToConsole`, both of which are the plugin's defaults; `failsOnError` was the setting that turned them off. The VS Code task in `.vscode/tasks.json` parses those lines into the Problems panel, which only works because the location is printed.

The worry with dropping `failsOnError` is Checkstyle's own errors, a file it cannot parse rather than a rule it violates. Probed on 2026-09-22 with a deliberately broken source file: the build still fails, and before any rule runs.

```
Failed during checkstyle configuration: Exception was thrown while processing
Unparseable.java: 4:17: mismatched input '{' expecting ')'
```

That path is `failsOnError`-independent; the plugin throws out of configuration. So nothing was lost.

## Evidence each gate can fail

A gate that has never failed is a gate nobody has tested. All four were made to fail on 2026-09-22.

| Gate | How it failed | Fix |
| --- | --- | --- |
| Checkstyle | 3 real violations: two double spaces before trailing comments in `HomeControllerTest`, one 123-character line in `MemberRegisterSchemaIT` | changed the code |
| Checkstyle, comment style | probe: one class comment rewritten as `/** */` | reverted |
| SpotBugs | 2 real findings, the `EI_EXPOSE_REP` pair above | excluded with the reasoning above |
| PMD | probe: an uncalled `private String unusedProbe(BigDecimal)`. `UnusedPrivateMethod` is suppressed when PMD cannot resolve a parameter type, so this fires only on a working auxclasspath and is therefore a test of the version pin, not just of the gate | reverted |

`var` is banned by `IllegalType`, carried over from the reference ruleset. Four uses were rewritten to their declared types.

## What it costs to undo

Deleting three plugin blocks and three XML files. Nothing in the application depends on them. The retrofit cost would return, larger, and the `///` convention would lose the only mechanical check it has.
