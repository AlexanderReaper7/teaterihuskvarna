---
created: 2026-09-22
provenance: unreviewed
description: Which template engine renders the HTML.
---

# 0004: JTE rather than Thymeleaf for templates

## Decision

The application renders HTML with [JTE](https://jte.gg/), through `gg.jte:jte-spring-boot-starter-4` 3.2.5. Not Thymeleaf, which the produktägare's document names under "Teknikval" and which the system sketch shows inside the application box.

Swedish visitor-facing text does not live in template files. It lives in [`messages_sv.properties`](../../src/main/resources/messages_sv.properties) and in Sanity. Templates hold markup and message keys.

## Why not Thymeleaf, given the plan says Thymeleaf

The document's reason is that Spring Boot and Thymeleaf are "studenternas utbildningsspråk", the students' course material. There are no students. That reason does not survive the change, so the choice was reopened on its merits rather than inherited.

Both engines are healthy and both support Boot 4.1.1, so this was never about one being abandoned:

| | JTE | Thymeleaf |
| --- | --- | --- |
| Boot 4 artifact | `gg.jte:jte-spring-boot-starter-4` 3.2.4, third party | `spring-boot-starter-thymeleaf` 4.1.1, first party |
| Stars, open issues | 1141, 54 | 2986, 40 |
| Last push | 2026-09-01 | 2026-06-22 |
| Recent releases | 3.2.1 Apr 2025, 3.2.2 Jan 2026, 3.2.3 Feb 2026, 3.2.4 Apr 2026 | 3.1.2 Jul 2023, 3.1.3 Dec 2024, 3.1.4 Apr 2026, 3.1.5 Apr 2026 |
| Security integration | none packaged | `thymeleaf-extras-springsecurity6` 3.1.5 |

The deciding property is when a mistake is found. A JTE template compiles to a Java class, so renaming a field or passing the wrong type fails `mvn compile`. The same mistake in Thymeleaf resolves at render time and produces a 500 on one page, discovered by whoever loads it first. On a site where most pages are read a few times a month, that may well be a visitor.

This is the same reasoning as the schema test that fails when a `personnummer` column appears. A check that runs on every build beats a rule that holds until someone forgets.

## What it costs

Three things, all accepted knowingly.

Thymeleaf is the first-party starter and has far more examples to copy from. JTE's answer to a problem is more often "work it out" than "find the Stack Overflow question".

There is no packaged Spring Security integration. Auth-aware markup means passing what the template needs into it rather than calling `sec:authorize`. Arguably better, since it makes the dependency visible in the parameter list, but it is more typing.

Thymeleaf templates are valid standalone HTML and open in a browser showing placeholder content. JTE templates do not. For a project where the same person writes the markup and the controller, this is worth little, but it is a real loss for anyone doing pure design work.

## The argument that was considered and rejected

The first version of this decision leaned the other way, towards an engine a non-developer could read, with Pebble as the candidate on the strength of its Twig and Jinja syntax. That collapsed under one question: what is the user story for a non-developer editing a template?

Every candidate turned out to belong somewhere else. Adding news or events is Sanity. Footer text, opening hours and contact details are a Sanity settings document. Reordering sections on the start page is Sanity page composition. Rewording a form label or a validation message is [`messages_sv.properties`](../../src/main/resources/messages_sv.properties). Colours and the logo are CSS and an asset. Adding a whole new page type needs a schema change and a renderer, which is development work under any engine.

Each of those is content sitting in the wrong place. Friendlier template syntax does not fix any of them, and fixing them properly removes the reason to care about template syntax.

Syntax is also the cheapest step in that chain. Editing a template needs a checkout, an editor, a build and a deploy that can take the site down. `{{ titel }}` being prettier than `${e.titel()}` does not help someone who cannot reach the file, and it does not stop them breaking the build once they can. Sanity gives that person a login, a preview, and no way to break anything.

So the requirement was restated: **a non-developer can change everything they would plausibly want to change, and none of it lives in a template.** That is a coverage requirement on the Sanity content model, not a property of the view layer, and it is why the language rule above moves Swedish copy out of templates.

## What it costs to undo

Templates are the only thing affected, and they are rewritten rather than ported. There is no data migration and no API change. The cost scales with how many templates exist, which is the reason to decide now rather than at template twenty.

## Agent notes

Written by an agent on 2026-11-10.

The version went from 3.2.4 to 3.2.5 on 2026-11-10. The table above is what was read on 2026-09-22 and still says 3.2.4. 3.2.5 was the newest release on Maven Central that day, published 2026-11-01, and its notes list three security fixes in the output escaping:

- `<script>` and `on…` attributes are recognised in any letter case ([GHSA-2hfm-x4p2-r9hw](https://github.com/casid/jte/security/advisories/GHSA-2hfm-x4p2-r9hw)). No template here spells either with a capital. The only capitals in a tag or attribute name are `viewBox` on the icons.
- A `javascript:` address is dropped also when a tab or a line break sits inside the word ([GHSA-mw9h-prf3-cr4h](https://github.com/casid/jte/security/advisories/GHSA-mw9h-prf3-cr4h)).
- A dynamic `srcdoc` on an `<iframe>` is written as empty, and `javascript:` and `data:` addresses are dropped from `<a href>`, `<area href>`, `<base href>`, `<iframe src>`, `<form action>`, `<button formaction>` and `<input formaction>` ([GHSA-hvg6-pc44-97gv](https://github.com/casid/jte/security/advisories/GHSA-hvg6-pc44-97gv)).

What this means for a template: JTE drops such a value without an error, at render time, so the compile-time argument above does not cover it. The mailing preview used `srcdoc` and showed an empty frame under 3.2.5 until it was rebuilt ([0026](0026-outbox-and-brevo-contacts.md), "The mailing preview"). Every other dynamic address in the templates on 2026-11-10 is a path the application builds, a configured address, or a Sanity address that [`ContentService`](../../src/main/java/se/teaterihuskvarna/content/ContentService.java) keeps only when it starts with `http://` or `https://`, so the guards drop nothing there.
