# Naming meeting documents

A meeting document is named `YYYY-MM-DD-<slug>.md`, the meeting's date and then a slug. The slug may contain digits but never starts with one, so the date always ends at the third hyphen.

- A proper meeting, for now a meeting with Klas, is numbered in sequence from 1, and the number is in the slug: `2026-09-24-meeting-1.md`, `2026-10-01-meeting-2.md`. The heading starts `# Meeting N:`.
- A sync is the date and `S`: `2026-09-28-S.md`. Syncs will probably never have notes here.
- `notes.md` keeps its name.

Decided by the user on 2026-09-25. `docs/check.py` fails a file under `docs/meetings/` that breaks the name pattern, apart from `notes.md` and this file.
