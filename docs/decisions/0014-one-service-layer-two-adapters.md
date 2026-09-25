---
created: 2026-09-22
provenance: unreviewed
description: How the web pages and the REST API share the application's logic.
---

# 0014: One service layer, two adapters

## Decision

One deployable. Every capability the system has is a method on an application service in a domain package such as `se.teaterihuskvarna.member`. Two adapters sit over that layer as peers:

- `se.teaterihuskvarna.web`: `@Controller` classes that call a service in process, as an ordinary method call, and name a JTE template.
- `se.teaterihuskvarna.api`: `@RestController` classes, one endpoint per capability, each a thin wrapper around the same method.

The invariant: **anything `web` can do, `api` can do.** Same method, same transaction boundary, same validation. The only difference between the two paths is the HTTP layer the REST adapter adds.

Four rules hold this up, all in `AdapterRulesTest`:

| Rule | What it forbids |
| --- | --- |
| `anAdapterCannotReachTheDatabase` | a class in `web` or `api` depending on a Spring Data `Repository` |
| `anAdapterCannotHandleAnEntity` | a class in `web` or `api` depending on an `@Entity` |
| `theAdaptersDoNotKnowAboutEachOther` | either adapter package depending on the other |
| `everyCapabilityAPageUsesIsAlsoAnEndpoint` | a service method a JTE controller calls that no REST controller calls |

## Why not two deployables

The obvious reading of "split into a REST backend and a frontend" is two processes: a JSON API, and a separate app that consumes it. That was rejected, and the reason is worth keeping.

JTE renders on the server. A separate frontend process that renders JTE would have to fetch its data over HTTP from the backend, which buys a network hop, a serialisation round trip and a second failure mode, in exchange for a boundary that a package already gives for free. The decoupling that was actually wanted is "page rendering must not contain business logic", and that is a compile-time property, not a deployment one.

So the split is real but it is a split of responsibilities, not of processes. Ports and adapters: the service layer is the application, `web` and `api` are two ways in.

## Why the parity rule is stated over service methods

The natural phrasing, "every page has a matching endpoint", does not work. `GET /` renders the start page, and there is no sensible REST equivalent of rendering a start page. Pages and endpoints are not in one-to-one correspondence and never will be.

Capabilities are. `MemberService.findByEmail` either has a caller in `api` or it does not. So the rule walks the method calls out of every class in each adapter package, keeps the ones whose target is annotated `@Service`, and asserts that the `api` set contains the `web` set.

One direction only. `api` may expose capabilities that no page uses; that is a REST API being more complete than the site, which is the direction the invariant wants. The reverse, a page doing something no endpoint can, is what fails the build.

What the rule checks is that an endpoint calls the method. It does not check that the endpoint is correct, reachable, or mapped to a sensible URL. It catches the failure that actually happens, which is adding a feature to the site and forgetting the API, not adding a broken endpoint on purpose.

## Why the repository is package private

`MemberRepository` is package private, so `MemberService` is the only class that can name it. A controller that tries to inject it does not fail a test, it fails to compile:

```
HomeController.java:[8,34] se.teaterihuskvarna.member.MemberRepository is not public
in se.teaterihuskvarna.member; cannot be accessed from outside package
```

The ArchUnit rule is the backstop for the day somebody makes a repository public, because Spring Data works fine with a public one and nothing else would object. Both halves were measured on 2026-09-22, below.

## Why adapters do not see entities

`open-in-view` is off ([0012](0012-jpa-over-a-schema-flyway-owns.md)). A `Member` handed to a JTE template or a Jackson serialiser throws `LazyInitializationException` the moment either touches an unloaded association, because the transaction ended when the service method returned.

So services return records, built inside the transaction: `MemberDetails`, not `Member`. The ArchUnit rule turns a runtime failure that needs the right page and the right data to reproduce into a build failure that needs neither.

This is also what makes parity honest. Both adapters get the same record. The JSON an endpoint serialises and the values a template reads come from one object, so they cannot drift.

## Evidence each rule can fail

A rule that has never failed is a rule nobody has tested, the same argument as [0013](0013-three-static-analysis-gates.md). All four were made to fail on 2026-09-22.

| Rule | Probe | Result |
| --- | --- | --- |
| `everyCapabilityAPageUsesIsAlsoAnEndpoint` | `HomeController` calls `MemberService.findByEmail`, no endpoint exists | failed: `Expecting TreeSet: [] to contain: ["MemberService.findByEmail"]` |
| same, other direction | added a `@RestController` calling the same method | green, 4 tests, 0 failures |
| `anAdapterCannotReachTheDatabase` | a `MemberRepository` field in `HomeController` | compile error, repository is package private |
| same, backstop | made `MemberRepository` public, kept the field | failed: `Field <...HomeController.probeRepository> has type <...MemberRepository>` |
| `anAdapterCannotHandleAnEntity` | a controller method returning `Member` | failed, 1 violation |
| `theAdaptersDoNotKnowAboutEachOther` | an `api` class referencing `HomeController` | failed, 1 violation |

All probes reverted; the working tree has no `Probe` classes.

### The pattern that made the rule inert

The rules first named the packages `..web..` and `..api..`, which reads naturally and is wrong. `..web..` also matches `org.springframework.web.bind.annotation`, so `@RestController` on any class in `api` counted as a dependency on `web` and `theAdaptersDoNotKnowAboutEachOther` failed against correct code. The packages are now written out in full, `se.teaterihuskvarna.web` and `se.teaterihuskvarna.api`, with the reason in a comment beside the constants.

This is the mirror image of the Javadoc finding in 0013. There the rule passed while checking nothing; here it failed while checking the wrong thing. Both were only visible because the probe ran.

## What this costs

An extra type per capability. A service method that returns `MemberDetails` needs `MemberDetails` written and mapped from `Member`, where returning the entity would have been one line. That is the price of `open-in-view` being off, and it is paid once per capability rather than once per page.

Two adapters to keep in step. The parity rule makes that a build failure rather than a review question, but the endpoint still has to be written.

## What it costs to undo

Deleting `AdapterRulesTest` and merging `api` into `web`. Nothing in the application depends on the split; the rules are the whole mechanism. Going the other way, to two deployables, means the network hop and the serialisation round trip that the first section rejects, plus a second image, a second health check and a shared DTO module.
