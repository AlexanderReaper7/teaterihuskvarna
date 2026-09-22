# 0009 — Java 25 LTS, not the newest release

2026-09-22

## Decision

Java 25 LTS. The container base image is `eclipse-temurin:25-jre-alpine`, per [0008](0008-everything-in-containers.md).

This replaces the original intent, which was to track whatever JDK was newest. That was Java 26 when the project started.

## Why the original policy was abandoned

Tracking the newest JDK has a cost that is easy to state and easy to underestimate: a non-LTS release receives updates only until the next release, so the policy commits somebody to a JDK bump every six months, forever.

That cost came due before a line of application code existed. JDK 27 reached general availability on 2026-09-15, which ended updates for JDK 26 the same day. The project's own plan already carried the objection, worded as a future problem: "its updates end around September 2026". It was September 2026, and the pinned runtime was frozen.

Six months is a short leash for a system whose stated maintenance budget is a few hours a year and whose maintainer after week 12 is an open question. The failure mode is not dramatic. It is a version that quietly stops receiving security updates, on a machine holding the member register, with nobody whose job it is to notice.

Java 25 is supported by Eclipse Temurin until at least 2031-09-30. That outlives any reasonable planning horizon for this project.

## The secondary reason, which is not small

Spring Boot 4.1.1 documents support for Java 17 through 26. Java 27 support is not documented until 4.1.2 ([spring-boot#51825](https://github.com/spring-projects/spring-boot/issues/51825), closed 2026-09-17, milestone 4.1.2), and start.spring.io still refuses to generate a Java 27 project for Boot 4.1 ([start.spring.io#2280](https://github.com/spring-io/start.spring.io/issues/2280), open).

So the newest JDK was not actually available without running ahead of the framework's documented support. Java 25 sits comfortably inside it.

## What it costs

New language features arrive two years late rather than six months late. The next LTS is Java 29 in September 2027.

For this codebase that is close to free. Nothing in a server-rendered site with a member register depends on a preview feature, and the parts of Java that this project leans on, records, sealed types, pattern matching and virtual threads, are all final in 25.

## What it costs to undo

One line in the `pom.xml` and one in the `Dockerfile`. Moving up to a newer JDK later is cheap and stays cheap. That asymmetry is part of the argument: choosing LTS now forecloses nothing, while choosing the newest release commits to a recurring obligation.
