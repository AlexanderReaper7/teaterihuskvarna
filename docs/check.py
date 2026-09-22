#!/usr/bin/env python3
"""Checks the docs tree for the two things that rot silently.

1. requirements.md quotes the produktagare's Swedish word for word. A quotation
   that drifts from projektplan-original.md is no longer a quotation.
2. Relative links between documents resolve. Dropping a citation and dropping
   prose look identical in a diff, so the citations get checked mechanically.

Run: python3 docs/check.py   (exit 0 clean, 1 with findings on stderr)
"""
import re
import sys
from pathlib import Path

DOCS = Path(__file__).resolve().parent
ROOT = DOCS.parent
# The produktagare writes M/B/K; the working copy spells them out. Same meaning.
PRIORITY = {"M": "Must", "B": "Should", "K": "Could"}
EXPECTED_TOTALS = {"Must": 20, "Should": 4, "Could": 1}
REQ_ID = re.compile(r"[PRIMVAU]\d")

problems = []


def rows(text, ncols):
    out = []
    for line in text.splitlines():
        line = line.strip()
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip("|").split("|")]
        if len(cells) != ncols or set("".join(cells)) <= set("-: "):
            continue
        out.append(cells)
    return out


def check_requirements():
    frozen = rows((DOCS / "projektplan-original.md").read_text(encoding="utf-8"), 4)
    frozen = [r for r in frozen if REQ_ID.fullmatch(r[0])]
    working = rows((DOCS / "requirements.md").read_text(encoding="utf-8"), 5)
    working = [r for r in working if REQ_ID.fullmatch(r[0])]

    if len(frozen) != len(working):
        problems.append(
            f"requirements.md has {len(working)} requirements, "
            f"projektplan-original.md has {len(frozen)}"
        )
        return

    for f, w in zip(frozen, working):
        if f[0] != w[0]:
            problems.append(f"requirement order diverged: {f[0]} vs {w[0]}")
            continue
        if f[2] != w[2]:
            problems.append(
                f"{w[0]}: quoted Swedish drifted from the frozen source\n"
                f"    original: {f[2]}\n"
                f"    working : {w[2]}"
            )
        expected = PRIORITY.get(f[3])
        if expected is None:
            problems.append(f"{f[0]}: unknown priority {f[3]!r} in the frozen source")
        elif w[3] != expected:
            problems.append(f"{w[0]}: priority is {w[3]!r}, frozen source says {f[3]!r} ({expected})")

    totals = {name: sum(1 for w in working if w[3] == name) for name in EXPECTED_TOTALS}
    if totals != EXPECTED_TOTALS:
        problems.append(f"priority totals are {totals}, expected {EXPECTED_TOTALS}")

    stated = (DOCS / "requirements.md").read_text(encoding="utf-8")
    line = ", ".join(f"{n} {p}" for p, n in totals.items())
    if f"Totals: {line}." not in stated:
        problems.append(f'requirements.md does not state "Totals: {line}."')


def check_links():
    for md in sorted(ROOT.rglob("*.md")):
        if ".git" in md.parts:
            continue
        text = md.read_text(encoding="utf-8")
        for label, target in re.findall(r"\[([^\]]*)\]\(([^)]+)\)", text):
            if target.startswith(("http://", "https://", "#", "mailto:")):
                continue
            path = (md.parent / target.split("#", 1)[0]).resolve()
            if not path.exists():
                rel = md.relative_to(ROOT)
                problems.append(f"{rel}: link [{label}]({target}) points at nothing")


check_requirements()
check_links()

if problems:
    print(f"{len(problems)} problem(s):", file=sys.stderr)
    for p in problems:
        print(f"  - {p}", file=sys.stderr)
    sys.exit(1)
print("docs ok: 25 requirements quoted intact, all relative links resolve")
