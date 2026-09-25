---
created: 2026-09-21
provenance: unreviewed
description: Which language each part of the system is written in.
---

# 0001: Language policy

## Decision

Swedish is what a visitor or a member reads: Sanity content, mail bodies, form labels, validation messages.

Where that Swedish lives is a second question, and the answer is not "in the templates". Editable content lives in Sanity. Fixed copy such as form labels and validation messages lives in `messages_sv.properties`. A template holds markup and message keys, nothing a reader sees. That keeps the copy somewhere a non-developer can be pointed at, and it means fixing a typo in a validation message does not mean touching a template. See [0004](0004-jte-for-templates.md), which is where this rule came from and which also replaced Thymeleaf with JTE.

English is everything else: this `docs/` tree, code identifiers, comments, commit messages, README, decision records.

## Two exceptions, both quotations

[projektplan-original.md](../projektplan-original.md) stays Swedish word for word. It is a conversion of the produktägare's docx, not something written here, and a translation would fork the source of truth.

The requirement text in [requirements.md](../requirements.md) stays Swedish for the same reason. Translating R001 through R025 would invent wording that no longer matches the document those ids come from, and the produktägare has to be able to recognise his own requirements. The table's headings, columns and surrounding prose are English; only the quoted cells are not.

The rule underneath both: a quotation does not get translated.

## Why one rule rather than case by case

A stated rule is one object that can be argued with. Twelve files each deciding for themselves is twelve separate arguments, and the drift shows up only when someone reads two of them side by side.
