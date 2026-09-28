#!/usr/bin/env python3
"""Checks the docs tree for the nine things that rot silently.

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
6. Every document under docs/, GLOSSARY.md, AGENTS.md and design/ says who
   stands behind it, user, agent or unreviewed, see decisions/0019. Decision
   records say so in front matter with a creation date and a description. Any
   other document may use front matter too, or a "Provenance:" line in its
   first ten lines, which may be an HTML comment so that readers do not see
   it. Meeting notes carry neither. The
   check cannot tell whether a value is true, only that it is there.
7. Every meeting document is named YYYY-MM-DD-<slug>.md, per
   meetings/naming-convention.md.
8. A requirement id followed by its issue, "R005 #8", names the same issue in
   every document, and no issue follows two ids. Whether that issue really is
   the requirement's on GitHub is not checked, because the check runs offline.

9. Everything that can be a link is one, decided by the user on 2026-09-26:
   a bare URL, and a file or directory tracked in this repository, whether
   written as `code` or as plain text, in full or as the end of exactly one
   file's path. A name that ends several paths fails until more of the path
   is written. Requirement ids and issue numbers stay
   plain text, which the user decided the same day: linked, they clutter. Code
   blocks, front matter, a document naming itself, and the files in
   LINK_EXEMPT are left alone. --fix-links rewrites what it finds. Decision
   numbers and commit hashes are not checked: "its 0010"
   can name another project's record, and a seven-letter hex word is not
   always a commit.

The mermaid check needs mmdc on PATH. Without it the check is skipped and says
so loudly; --require-mermaid turns that skip into a failure, which is what CI
passes. A check that silently passes when its tool is missing is worse than no
check, because it reports success it did not earn.

Run: python3 docs/check.py [--require-mermaid] [--fix-links]
     (exit 0 clean, 1 with findings on stderr)
"""
import datetime
import os
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
# Keyed by the frozen source's id.
OVERRIDES = {"V1": "MUST"}
EXPECTED_TOTALS = {"MUST": 21, "SHOULD": 3, "COULD": 1}
# The frozen source's ids, and the working copy's, which follow its row order.
ORIGINAL_ID = re.compile(r"[PRIMVAU]\d")
REQ_ID = re.compile(r"R\d{3}")
# A requirement id, optionally followed directly by its issue: "R005 #8".
REQ_ISSUE = re.compile(r"\b(R\d{3}) #(\d+)\b")

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
    frozen = [r for r in frozen if ORIGINAL_ID.fullmatch(r[0])]
    working = rows((DOCS / "requirements.md").read_text(encoding="utf-8"), 4)
    for r in working:
        r[0] = r[0].split(" #")[0]
    working = [r for r in working if REQ_ID.fullmatch(r[0])]

    if len(frozen) != len(working):
        problems.append(
            f"requirements.md has {len(working)} requirements, "
            f"projektplan-original.md has {len(frozen)}"
        )
        return

    for number, (f, w) in enumerate(zip(frozen, working), 1):
        if w[0] != f"R{number:03}":
            problems.append(f"requirement {number} is {w[0]}, expected R{number:03}, the row of {f[0]} in the frozen source")
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


PROVENANCE = re.compile(r"(?:<!-- )?Provenance: (user|agent|unreviewed)\.")
# The customer's own words, which must not change, not even by one line.
FROZEN = {"docs/projektplan-original.md", "docs/projektplan-original.en.md"}
FIELDS = {"created", "provenance", "description", "superseded_by"}
REQUIRED = {"created", "provenance", "description"}
# Front matter is required here; elsewhere it is optional, and a Provenance
# line does the same job.
IN_FRONT_MATTER = ("docs/decisions/",)
# A value outside YAML's plain scalars would parse here and differently in
# GitHub's renderer, so it is refused rather than quoted.
YAML_LEADING = tuple("[]{}&*!|>'\"%@`,-?:#")


def front_matter(rel, text):
    """The front matter's fields, or None when the file has none. Only flat
    `key: value` lines, the part of YAML these files use."""
    lines = text.splitlines()
    if not lines or lines[0] != "---":
        return None
    if "---" not in lines[1:]:
        problems.append(f"{rel}: front matter has no closing '---'")
        return {}
    fields = {}
    for line in lines[1:lines.index("---", 1)]:
        key, sep, value = line.partition(": ")
        if not sep or not value or key not in FIELDS or key in fields:
            problems.append(f"{rel}: front matter line {line!r} is not one of {sorted(FIELDS)}, once, with a value")
        elif value.startswith(YAML_LEADING) or ": " in value or " #" in value or value != value.strip():
            problems.append(f"{rel}: front matter value {value!r} is not a plain YAML scalar")
        else:
            fields[key] = value
    return fields


def check_front_matter(md, rel, fields):
    for key in sorted(REQUIRED - fields.keys()):
        problems.append(f"{rel}: front matter lacks {key}")
    try:
        datetime.date.fromisoformat(fields.get("created", ""))
    except ValueError:
        if "created" in fields:
            problems.append(f"{rel}: created {fields['created']!r} is not a YYYY-MM-DD date")
    if fields.get("provenance", "user") not in ("user", "agent", "unreviewed"):
        problems.append(f"{rel}: provenance {fields['provenance']!r} is not user, agent or unreviewed")
    if "superseded_by" in fields and not (md.parent / fields["superseded_by"]).is_file():
        problems.append(f"{rel}: superseded_by {fields['superseded_by']!r} points at nothing")


def check_provenance():
    """Returns how many documents are still marked unreviewed."""
    unreviewed = 0
    for md in markdown_files():
        rel = md.relative_to(ROOT).as_posix()
        text = md.read_text(encoding="utf-8")
        fields = front_matter(rel, text)
        lines = [line for line in text.splitlines()[:10] if PROVENANCE.match(line)]
        if rel.startswith("docs/meetings/"):
            if fields is not None or lines:
                problems.append(f"{rel}: meeting notes carry no front matter and no Provenance line")
            continue
        if rel in FROZEN or not (rel.startswith(("docs/", "design/")) or rel in ("GLOSSARY.md", "AGENTS.md")):
            continue
        if fields is not None:
            check_front_matter(md, rel, fields)
            if lines:
                problems.append(f"{rel}: has front matter and a Provenance line; the line goes")
            unreviewed += fields.get("provenance") == "unreviewed"
        elif rel.startswith(IN_FRONT_MATTER):
            problems.append(f"{rel}: needs front matter with {sorted(REQUIRED)}")
        elif len(lines) != 1:
            problems.append(f"{rel}: needs front matter, or exactly one 'Provenance: user|agent|unreviewed.' line in its first ten lines, has {len(lines)}")
        else:
            unreviewed += PROVENANCE.match(lines[0])[1] == "unreviewed"
    return unreviewed


# See meetings/naming-convention.md.
MEETING_NAME = re.compile(r"(\d{4}-\d\d-\d\d)-(S|[^\d\W][\w-]*)\.md")
MEETING_EXEMPT = {"notes.md", "naming-convention.md"}


def check_meeting_names():
    for md in markdown_files():
        rel = md.relative_to(ROOT).as_posix()
        if not rel.startswith("docs/meetings/") or md.name in MEETING_EXEMPT:
            continue
        match = MEETING_NAME.fullmatch(md.name)
        try:
            ok = match is not None and datetime.date.fromisoformat(match[1]) is not None
        except ValueError:
            ok = False
        if not ok:
            problems.append(f"{rel}: a meeting document is YYYY-MM-DD-<slug>.md, the slug not starting with a digit, see meetings/naming-convention.md")


def check_requirement_issues():
    """An id followed by an issue names the same issue everywhere."""
    seen = {}
    for md in markdown_files():
        for req, issue in REQ_ISSUE.findall(md.read_text(encoding="utf-8")):
            first = seen.setdefault(req, (issue, md.relative_to(ROOT)))
            if first[0] != issue:
                problems.append(f"{md.relative_to(ROOT)}: {req} #{issue}, but {first[1]} has {req} #{first[0]}")
    by_issue = {}
    for req, (issue, where) in seen.items():
        if by_issue.setdefault(issue, req) != req:
            problems.append(f"#{issue} follows both {by_issue[issue]} and {req}")


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


# The frozen customer documents must not change, and notes.md is the user's
# own notes from the first meeting.
LINK_EXEMPT = {
    "docs/projektplan-original.md",
    "docs/projektplan-original.en.md",
    "docs/meetings/notes.md",
}
# Already a link, an autolink, or a code span, in the order Markdown reads them.
LINK_TOKEN = re.compile(r"!?\[[^\]]*\]\([^)]*\)|<https?://[^>]+>|`[^`]+`")
BARE_URL = re.compile(r"https?://[^\s<>()\[\]`]+[^\s<>()\[\]`.,;:!?'\"]")
PLAIN_PATH = re.compile(r"(?<![\w/.\-\[])(?:\./)?([\w.\-]+(?:/[\w.\-]+)*/?)")


def tracked_paths():
    listed = subprocess.run(
        ["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, text=True, check=True,
    ).stdout
    files = set(filter(None, listed.split("\0")))
    dirs = {str(Path(f).parents[i]) for f in files for i in range(len(Path(f).parents) - 1)}
    return files, dirs


def path_target(md, written, files, dirs):
    """The relative link for a path written in md, None if it names nothing
    tracked, or the list of candidates if it could name several. Tried from
    md's directory, then the root, then as the end of exactly one tracked
    file's path, so `passkey.js` finds src/main/resources/static/js/passkey.js.
    Directories only match exactly: as a suffix, `test` or `web` would turn
    ordinary words into links."""
    name = written.removeprefix("./")
    is_dir = name.endswith("/")
    name = name.rstrip("/")
    if not name or name in (".", ".."):
        return None
    here = md.parent.relative_to(ROOT)
    for base in (here, Path(".")):
        candidate = Path(*(base / name).parts)
        try:
            key = str(candidate.resolve().relative_to(ROOT)) if ".." in candidate.parts else str(candidate)
        except ValueError:
            continue
        if key in files and not is_dir:
            if ROOT / key == md:
                return None
            target = Path(key)
        elif key in dirs:
            target = Path(key)
        else:
            continue
        rel = Path(os.path.relpath(ROOT / target, md.parent)).as_posix()
        return rel + ("/" if key in dirs else "")
    if is_dir or name.startswith(("/", "../")):
        return None
    matches = sorted(f for f in files if f == name or f.endswith("/" + name))
    if len(matches) > 1:
        return matches
    if not matches or ROOT / matches[0] == md:
        return None
    return Path(os.path.relpath(ROOT / matches[0], md.parent)).as_posix()


def unlinked(md, files, dirs):
    """(line index, start, end, replacement) for everything in md that could
    be a link and is not, and (line index, name, candidates) for each name
    that could be more than one file."""
    found = []
    ambiguous = []
    lines = md.read_text(encoding="utf-8").split("\n")
    fenced = False
    body_start = 0
    if lines and lines[0] == "---":
        body_start = lines.index("---", 1) + 1
    for i, line in enumerate(lines):
        if i < body_start:
            continue
        if line.lstrip().startswith(("```", "~~~")):
            fenced = not fenced
            continue
        if fenced or line.startswith("    ") and not line.lstrip().startswith(("-", "|", "*")) and line.strip():
            continue
        pos = 0
        for token in list(LINK_TOKEN.finditer(line)) + [None]:
            end = token.start() if token else len(line)
            text = line[pos:end]
            for m in BARE_URL.finditer(text):
                found.append((i, pos + m.start(), pos + m.end(), f"[{m.group(0)}]({m.group(0)})"))
            for m in PLAIN_PATH.finditer(text):
                word = m.group(0).rstrip(".")
                if "/" not in word and not re.search(r"\.[A-Za-z]", word):
                    continue
                if any(u.start() <= m.start() < u.end() for u in BARE_URL.finditer(text)):
                    continue
                target = path_target(md, word, files, dirs)
                if isinstance(target, list):
                    ambiguous.append((i, word, target))
                elif target:
                    found.append((i, pos + m.start(), pos + m.start() + len(word), f"[{word}]({target})"))
            if token and token.group(0).startswith("`"):
                target = path_target(md, token.group(0)[1:-1].strip(), files, dirs)
                if isinstance(target, list):
                    ambiguous.append((i, token.group(0), target))
                elif target:
                    found.append((i, token.start(), token.end(), f"[{token.group(0)}]({target})"))
            pos = token.end() if token else pos
    return lines, found, ambiguous


def check_everything_linked(fix):
    files, dirs = tracked_paths()
    fixed = 0
    for md in markdown_files():
        rel = md.relative_to(ROOT).as_posix()
        if rel in LINK_EXEMPT:
            continue
        lines, found, ambiguous = unlinked(md, files, dirs)
        for i, name, candidates in ambiguous:
            problems.append(f"{rel}:{i + 1}: {name} could be any of {', '.join(candidates)}; write enough of the path to pick one")
        if not found:
            continue
        if fix:
            for i, start, end, new in sorted(found, reverse=True):
                lines[i] = lines[i][:start] + new + lines[i][end:]
            md.write_text("\n".join(lines), encoding="utf-8")
            fixed += len(found)
            continue
        for i, start, end, new in found:
            problems.append(f"{rel}:{i + 1}: {lines[i][start:end]} could be a link: {new}")
    if fix:
        print(f"linked {fixed} item(s)")


check_everything_linked("--fix-links" in sys.argv[1:])
check_requirements()
check_links()
check_footnotes()
check_glossary()
unreviewed = check_provenance()
check_meeting_names()
check_requirement_issues()
diagrams = check_mermaid("--require-mermaid" in sys.argv[1:])

if problems:
    print(f"{len(problems)} problem(s):", file=sys.stderr)
    for p in problems:
        print(f"  - {p}", file=sys.stderr)
    sys.exit(1)
print(
    f"docs ok: {sum(EXPECTED_TOTALS.values())} requirements quoted intact, "
    f"all relative links resolve, everything that can be a link is one, footnotes match, glossary headings name both words, "
    f"{diagrams} mermaid diagrams parse, every document states its provenance "
    f"({unreviewed} still unreviewed)"
)
