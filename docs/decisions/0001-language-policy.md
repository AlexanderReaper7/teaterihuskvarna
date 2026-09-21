# 0001 — Language policy

2026-09-21

## Decision

Swedish is what a visitor or a member reads: Sanity content, Thymeleaf template copy, mail bodies, form labels, validation messages.

English is everything else: this `docs/` tree, code identifiers, comments, commit messages, README, decision records.

## Two exceptions, both quotations

[projektplan.md](../projektplan.md) stays Swedish word for word. It is a conversion of the produktägare's docx, not something written here, and a translation would fork the source of truth.

The requirement text in [requirements.md](../requirements.md) stays Swedish for the same reason. Translating P1 through U4 would invent wording that no longer matches the document those ids come from, and the produktägare has to be able to recognise his own requirements. The table's headings, columns and surrounding prose are English; only the quoted cells are not.

The rule underneath both: a quotation does not get translated.

## Why one rule rather than case by case

A stated rule is one object that can be argued with. Twelve files each deciding for themselves is twelve separate arguments, and the drift shows up only when someone reads two of them side by side.
