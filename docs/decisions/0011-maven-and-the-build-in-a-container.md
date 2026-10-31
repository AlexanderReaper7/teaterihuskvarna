---
created: 2026-09-22
provenance: unreviewed
description: Which build tool builds the project, and where the build runs.
---

# 0011: Maven, and the build runs in a container

## Decision

Maven 4, version `4.0.0-rc-7`, through the checked-in [`mvnw`](../../mvnw) wrapper. Tests use Testcontainers, at the version Spring Boot manages, against the same pinned PostgreSQL image the application runs against.

No JDK is required on the machine doing the building. `docker compose build` produces the image and the toolchain belongs to the Dockerfile, not to a laptop.

## Why Maven, and why this is a record rather than a habit

[0004](0004-jte-for-templates.md) wrote `mvn compile` in passing while arguing about templates. That is a build tool arriving by accident, in a sentence about something else. This record exists to either confirm it deliberately or replace it.

Confirmed. A [`pom.xml`](../../pom.xml) is data; a `build.gradle.kts` is a program. Routine maintenance here is approving a green Dependabot pull request, and a build file that cannot contain logic is a feature under that model rather than a limitation. Gradle's incremental build speed is real and buys little against one application module.

## Maven 4, with its release-candidate status stated plainly

`4.0.0-rc-7` is the newest build Apache publishes; there is no Maven 4 GA on 2026-10-31. Running a release candidate is a deliberate choice, made here rather than inherited, and it is worth being exact about what is being accepted.

What it buys: Maven 4 reads the same [`pom.xml`](../../pom.xml), and the cutover that would otherwise land mid-project lands now, while the project is one empty module. A build migration costs almost nothing today and costs a week once there are students, CI history and a deployed image depending on it.

What it risks: a release candidate can change behaviour before GA, and plugin compatibility is proven by building rather than by reading. The proof obligation is therefore on this project, not on Apache, and it is discharged by the build passing in CI on every commit.

The fallback is cheap and does not need planning for: Maven 3.9.16 reads the same [`pom.xml`](../../pom.xml), so reverting is an edit to one wrapper property.

## Why the build happens in a container

The machine this was written on runs Temurin 26.0.2.1. The project is pinned to Java 25 LTS by [0009](0009-java-25-lts.md). Those already disagree, on day zero, with one developer. Four students on four machines will disagree in four more ways.

[0008](0008-everything-in-containers.md) made the deployed artefact the one that was tested. Building in a container extends that backwards: the compiler is pinned too, so "works on my machine" stops being a claim anyone can make about the build.

- Build stage: `eclipse-temurin:25-jdk-alpine` plus the checked-in wrapper. Docker Hub's `maven` image stopped at `4.0.0-rc-5` on 2026-09-22, when Apache had published `4.0.0-rc-6`, so taking the wrapper's word for the version instead removes a dependency on that image being republished, and keeps the Maven version in a file this repository owns.
- Runtime stage: `eclipse-temurin:25.0.4_7-jre-alpine`, the pinned form of the base image [0008](0008-everything-in-containers.md) names.

Both are pinned rather than floating, for the reason [0008](0008-everything-in-containers.md) gives: Dependabot reads a `FROM` line, and a tag that moves on its own is a change nobody reviewed.

## Testcontainers needs a daemon, which the image build does not have

This is the wrinkle worth writing down, because discovering it inside a red CI run costs an afternoon.

Testcontainers starts containers. A `docker build` stage cannot, since it has no access to a daemon. Running the integration tests during the image build therefore requires mounting a docker socket into the build, which is a privilege escalation in exchange for convenience.

So the two run in different places:

- `docker build` compiles, runs unit tests and packages the jar. No daemon needed.
- Integration tests run through a compose service, or on the GitHub Actions runner, where a daemon exists and the socket is already available.

CI runs both before an image reaches GHCR, so the split changes where tests run, not whether a green build means they passed.

## Testcontainers follows Spring Boot

Testcontainers 2 renamed its modules, `postgresql` to `testcontainers-postgresql`, so the 1.x artifact ids stop at 1.21.4 and Dependabot sees no newer release of them. A pin on the old ids therefore goes stale without a pull request. The pom names no version and takes the one Spring Boot manages, so Testcontainers moves with Boot.

## What it costs to undo

Moving to Gradle is a rewrite of one file and the wrapper next to it, with no effect on the application. Dropping from Maven 4 to 3.9.16 is one line in [`.mvn/wrapper/maven-wrapper.properties`](../../.mvn/wrapper/maven-wrapper.properties). Moving the build out of a container means installing a JDK 25 on every machine that builds, which is the cost this record is paying to avoid.
