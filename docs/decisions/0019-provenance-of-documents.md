---
created: 2026-09-25
provenance: user
description: How a document shows what the user decided and what an agent wrote.
---

# 0019: Every document says who stands behind it

The Decision and Front matter sections are the user's. Everything under Agent notes is an agent's.

## Decision

Documents must make clear what the user decided and what an agent decided or wrote. Agent writing is still useful, but it has to be framed so that a future agent can scrutinise it.

## Front matter

Metadata goes in front matter, starting with the decision records, the design README and the research document. The fields are a creation date, the provenance, a short description of what the document is about, and an optional `superseded_by`:

```yaml
---
created: 2026-09-25
provenance: user
description: What the document is about.
superseded_by: 0020-example.md
---
```

There is no `related` field: "we can compute it by the links, so the content becomes the source of truth." Meeting notes carry neither front matter nor a Provenance line. The design README is `agent`. Decided by the user on 2026-09-25.

## The user's reasons

The user programs only through AI agents, and the documents exist because agents need context. On 2026-09-25 the user wrote:

> the current documentation however is terrible, numerus desicions have been overstated or even fabricated. they also create blockers to innovation.

> prioritize making it clear what an agent decided and has written (in the docs), and what the user decided. the agents writing can still be usefull but it must be framed propely so a future agent can scrutinize it.

## Agent notes

An agent designed this format on 2026-09-25. The user has not reviewed it beyond the conversation it came from.

Every Markdown file under `docs/`, `design/`, `GLOSSARY.md` and `AGENTS.md` states its provenance, with one of three values:

- `user`: the user decided or wrote it. Agent text inside the file sits under a heading that starts with "Agent notes".
- `agent`: an agent decided or wrote it, and the user has not approved it. It is a default, open to a better idea.
- `unreviewed`: an agent wrote it before this record, and some of it may be the user's, but nobody has marked which. Treat it as `agent`. Where the user has since confirmed a part, that part ends with "Decided by the user on" and a date.

Decision records, research and `design/` state it in front matter, and the other files move to front matter one at a time. Until a file moves, it has a line starting `Provenance:` within its first ten lines. The `created` of 0010 to 0014 is the date the record states, 2026-09-22; git first saw them on 2026-09-23. `design/old-site/README.md` states no date, so its `created` is git's. Each `description` is the question the document answers, since the title already gives the answer, and every one was worded by an agent.

The two frozen customer documents, `projektplan-original.md` and `projektplan-original.en.md`, carry no line, because they must not change. They are the customer's words.

`docs/check.py` fails a file without either, fails front matter with an unknown field, a bad date or value, or a `superseded_by` that points at nothing, and counts the `unreviewed` files, so the size of the review backlog stays visible. It cannot tell whether a line is true. An agent can still write `user` over its own reasoning. The protection against that is the rule in `AGENTS.md` and the user reading the diff.

On 2026-09-25 every existing file was marked `unreviewed`, except `meetings/notes.md`, which the user wrote at the first meeting. Its lowercase style and spelling match the user's other writing, and it arrived in the user's commit `e10254a`.
