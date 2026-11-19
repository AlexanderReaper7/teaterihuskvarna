---
created: 2026-09-21
provenance: unreviewed
description: Which language each part of the system is written in.
---

# 0001: Language policy

Amended on 2026-11-01, as issue #115 asks. A guide for the association's own people is in Swedish.

## Decision

Swedish is what a visitor or a member reads: Sanity content, mail bodies, form labels, validation messages.

Where that Swedish lives is a second question, and the answer is not "in the templates". Editable content lives in Sanity. Fixed copy such as form labels and validation messages lives in [`messages_sv.properties`](../../src/main/resources/messages_sv.properties). A template holds markup and message keys, nothing a reader sees. That keeps the copy somewhere a non-developer can be pointed at, and it means fixing a typo in a validation message does not mean touching a template. See [0004](0004-jte-for-templates.md), which is where this rule came from and which also replaced Thymeleaf with JTE.

English is everything else: this [`docs/`](../) tree, code identifiers, comments, commit messages, README, decision records.

A guide for the association's own people is the exception inside [`docs/`](../): [administrator-guide.md](../administrator-guide.md), and the editor guide #114 asks for. Its readers are board members and editors, not developers, and every step names a Swedish heading or button they see on the screen.

## Two exceptions, both quotations

[projektplan-original.md](../projektplan-original.md) stays Swedish word for word. It is a conversion of the produktägare's docx, not something written here, and a translation would fork the source of truth.

The requirement text in [requirements.md](../requirements.md) stays Swedish for the same reason. Translating R001 through R025 would invent wording that no longer matches the document those ids come from, and the produktägare has to be able to recognise his own requirements. The table's headings, columns and surrounding prose are English; only the quoted cells are not.

The rule underneath both: a quotation does not get translated.

## Why one rule rather than case by case

A stated rule is one object that can be argued with. Twelve files each deciding for themselves is twelve separate arguments, and the drift shows up only when someone reads two of them side by side.

## Agent notes

The application's locale is fixed to `sv_SE`: `spring.web.locale` and `spring.web.locale-resolver: fixed` in [`application.yaml`](../../src/main/resources/application.yaml). The browser's `Accept-Language` therefore chooses nothing, and both a validation message and the `Content-Language` header are Swedish for every request. An agent set this on 2026-11-10, after a review found the mailing form answering "darf nicht leer sein" to a German browser: its five constraints named no key in [`messages_sv.properties`](../../src/main/resources/messages_sv.properties), so the validator used its own texts in the browser's language. They name keys now, and [`ValidationMessagesTest`](../../src/test/java/se/teaterihuskvarna/architecture/ValidationMessagesTest.java) fails the build on a new constraint that names none. It lists four older ones that still do not, which answer a REST client only, and it leaves out the settings classes, whose messages go to the log at startup. The fixed locale alone would not make such a constraint Swedish: Hibernate Validator 9.1.5 has no Swedish texts, so it would answer "must not be blank".
