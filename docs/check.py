#!/usr/bin/env python3
"""Checks the docs tree for the five things that rot silently.

1. requirements.md quotes the produktagare's Swedish word for word. A quotation
   that drifts from projektplan-original.md is no longer a quotation.
2. Relative links between documents resolve. Dropping a citation and dropping
   prose look identical in a diff, so the citations get checked mechanically.
3. Every mermaid block parses. A broken diagram renders as a grey code block on
   GitHub rather than as an error, so nobody notices in review.
4. Every footnote reference has exactly one definition, and every definition is
   referenced. GitHub renders a reference without a definition as literal
   "[^x]" text and drops an unreferenced definition, so a source can vanish from
   a page without anything looking broken.
5. Every glossary entry's heading reads "English | Swedish". The Swedish word is
   the one the site's copy uses, and an entry added without one looks finished.

The mermaid check needs mmdc on PATH. Without it the check is skipped and says
so loudly; --require-mermaid turns that skip into a failure, which is what CI
passes. A check that silently passes when its tool is missing is worse than no
check, because it reports success it did not earn.

Run: python3 docs/check.py [--require-mermaid]
     (exit 0 clean, 1 with findings on stderr)
"""
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

DOCS = Path(__file__).resolve().parent
ROOT = DOCS.parent
# The produktagare writes M/B/K; the working copy spells them out. Same meaning.
PRIORITY = {"M": "MUST", "B": "SHOULD", "K": "COULD"}
# Where the produktagare's document contradicts itself, its version 1 scope
# table wins over the row's own letter. requirements.md says why for each.
OVERRIDES = {"V1": "MUST"}
EXPECTED_TOTALS = {"MUST": 21, "SHOULD": 3, "COULD": 1}
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
        frozen_priority = PRIORITY.get(f[3])
        expected = OVERRIDES.get(f[0], frozen_priority)
        if frozen_priority is None:
            problems.append(f"{f[0]}: unknown priority {f[3]!r} in the frozen source")
        elif w[3] != expected:
            problems.append(f"{w[0]}: priority is {w[3]!r}, expected {expected!r} (frozen source says {f[3]!r})")

    totals = {name: sum(1 for w in working if w[3] == name) for name in EXPECTED_TOTALS}
    if totals != EXPECTED_TOTALS:
        problems.append(f"priority totals are {totals}, expected {EXPECTED_TOTALS}")

    stated = (DOCS / "requirements.md").read_text(encoding="utf-8")
    line = ", ".join(f"{n} {p}" for p, n in totals.items())
    if f"Totals: {line}." not in stated:
        problems.append(f'requirements.md does not state "Totals: {line}."')


def markdown_files():
    """Tracked files and new ones git would add, so nothing under .gitignore:
    node_modules/ and target/ hold other projects' Markdown, with links into
    trees that are not here."""
    listed = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z", "--", "*.md"],
        cwd=ROOT, capture_output=True, text=True, check=True,
    ).stdout
    for name in sorted(set(filter(None, listed.split("\0")))):
        md = ROOT / name
        if md.exists():
            yield md


STACK_FRAME = re.compile(r"\s*(at\s|\S+ \(https?://)")


def mmdc_complaint(done):
    """The first lines of mmdc's output, before the puppeteer stack trace."""
    said = []
    for line in (done.stderr or done.stdout).strip().splitlines():
        if STACK_FRAME.match(line):
            break
        said.append(line[:300])
    return "\n".join("      " + l for l in said[:4]) or f"      exit {done.returncode}"


def check_mermaid(require):
    """Render every mermaid block. Returns how many were actually checked."""
    blocks = []
    for md in markdown_files():
        text = md.read_text(encoding="utf-8")
        for m in re.finditer(r"```mermaid\n(.*?)```", text, re.S):
            line = text.count("\n", 0, m.start()) + 1
            blocks.append((md.relative_to(ROOT), line, m.group(1)))
    if not blocks:
        return 0

    if shutil.which("mmdc") is None:
        note = f"{len(blocks)} mermaid diagram(s) not checked: mmdc is not on PATH"
        if require:
            problems.append(note + " (--require-mermaid was passed)")
        else:
            print(f"skipped: {note}", file=sys.stderr)
        return 0

    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        # Chromium's sandbox is unavailable on most CI runners.
        config = tmp / "puppeteer.json"
        config.write_text('{"args": ["--no-sandbox"]}', encoding="utf-8")
        for n, (rel, line, src) in enumerate(blocks, 1):
            source = tmp / f"{n}.mmd"
            source.write_text(src, encoding="utf-8")
            done = subprocess.run(
                ["mmdc", "-i", str(source), "-o", str(tmp / f"{n}.svg"),
                 "-p", str(config), "-q"],
                capture_output=True, text=True,
            )
            if done.returncode != 0:
                # mmdc numbers the error's line from the start of the block, so
                # add the fence's own line to get somewhere the editor can jump.
                problems.append(
                    f"{rel}:{line}: mermaid diagram does not render\n"
                    f"{mmdc_complaint(done)}"
                )
    return len(blocks)


FENCE = re.compile(r"^```.*?^```", re.S | re.M)
INLINE_CODE = re.compile(r"`[^`\n]*`")
FOOTNOTE_DEF = re.compile(r"^\[\^([^\]\s]+)\]:", re.M)
FOOTNOTE_REF = re.compile(r"\[\^([^\]\s]+)\](?!:)")


def check_footnotes():
    for md in markdown_files():
        text = FENCE.sub("", md.read_text(encoding="utf-8"))
        text = INLINE_CODE.sub("", text)
        rel = md.relative_to(ROOT)
        defined = FOOTNOTE_DEF.findall(text)
        referenced = set(FOOTNOTE_REF.findall(text))
        for name in sorted({d for d in defined if defined.count(d) > 1}):
            problems.append(f"{rel}: footnote [^{name}] is defined {defined.count(name)} times")
        for name in sorted(referenced - set(defined)):
            problems.append(f"{rel}: footnote [^{name}] is referenced but never defined")
        for name in sorted(set(defined) - referenced):
            problems.append(f"{rel}: footnote [^{name}] is defined but never referenced")


GLOSSARY_ENTRY = re.compile(r"### (?P<english>[^|]*?) \| (?P<swedish>[^|]+)")


def check_glossary():
    for number, line in enumerate((ROOT / "GLOSSARY.md").read_text(encoding="utf-8").splitlines(), 1):
        if line.startswith("### ") and not (
            (entry := GLOSSARY_ENTRY.fullmatch(line.rstrip())) and entry["english"].strip()
        ):
            problems.append(f"GLOSSARY.md:{number}: heading {line!r} is not 'English | Swedish'")


def check_links():
    for md in markdown_files():
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
check_footnotes()
check_glossary()
diagrams = check_mermaid("--require-mermaid" in sys.argv[1:])

if problems:
    print(f"{len(problems)} problem(s):", file=sys.stderr)
    for p in problems:
        print(f"  - {p}", file=sys.stderr)
    sys.exit(1)
print(
    f"docs ok: {sum(EXPECTED_TOTALS.values())} requirements quoted intact, "
    f"all relative links resolve, footnotes match, glossary headings name both words, "
    f"{diagrams} mermaid diagrams parse"
)
