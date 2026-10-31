#!/usr/bin/env python3
"""Writes the Lucide icons the templates use, one JTE template per icon.

A template shows an icon with `@template.icon.logOut()`. After adding or
removing such a call, run

    python3 scripts/lucide-icons.py

It finds every call under src/main/jte, downloads the pinned lucide-static
release, writes src/main/jte/icon/<name>.jte for each icon used and deletes
the ones no template uses any more. JTE precompiles every template, so a call
to an icon that was never written fails the build.

An icon is decoration beside a menu link or an action's text, never instead of
it: it is hidden from screen readers and drawn in the text's colour. POLICY
below is the one list of which icon stands for what, and the script refuses an
icon that is not in it, so one action keeps one icon on every page.
"""

import base64
import hashlib
import io
import re
import sys
import tarfile
import urllib.request
from pathlib import Path

# Pinned to a release at least seven days old, as Dependabot's cooldown does
# for every other dependency. 1.61.0 was published on 2026-10-21.
VERSION = "1.61.0"
# `npm view lucide-static@1.61.0 dist.integrity`
INTEGRITY = "sha512-yLAiQB3MWUJEstsEDAwenSA3m7vr1tdLvgnC16Q/XMxRRma0DWwMs8M2bWlvgShrIs1m2mWMJ+0AiahqQO4GrA=="
URL = f"https://registry.npmjs.org/lucide-static/-/lucide-static-{VERSION}.tgz"

# Lucide's name, and what it stands for on the member and administrator pages.
# A link that only leads back to another page gets no icon.
POLICY = {
    # The menus, and a link elsewhere that goes to the same place.
    "layout-dashboard": "Översikt",
    "id-card": "Kontaktuppgifter",
    "contact": "Medlemsregister",
    "house": "Hushåll, and managing a household",
    "ticket": "Erbjudanden",
    "file-text": "Handlingar",
    "hand-helping": "Volontärpass",
    "mail": "Utskick",
    # Actions.
    "log-out": "log out, on this device or the others",
    "log-in": "log in again",
    "refresh-cw": "stay logged in",
    "trash-2": "remove or delete, and a link to a page that asks first",
    "plus": "add or create, and a link to a form that does",
    "pencil": "change, and a link to a form that does",
    "save": "save",
    "upload": "upload",
    "download": "download, CSV files included",
    "external-link": "open Sanity or Brevo",
    "eye": "publish, or preview",
    "eye-off": "unpublish",
    "check": "register for an offer",
    "x": "cancel a registration, or dismiss for now",
    "calendar-plus": "book a shift",
    "calendar-x": "cancel a booking",
    "badge-check": "mark a fee paid",
    "undo-2": "undo a payment",
    "send": "send an invitation or a test mailing",
    "key-round": "add a passkey",
    "bell-off": "never ask again",
    "search": "search",
    "door-open": "leave a household",
}

ROOT = Path(__file__).resolve().parent.parent
TEMPLATES = ROOT / "src/main/jte"
ICONS = TEMPLATES / "icon"
CALL = re.compile(r"@template\.icon\.([A-Za-z][A-Za-z0-9]*)\(\)")


def lucide_name(call: str) -> str:
    """logOut is log-out, trash2 is trash-2."""
    return re.sub(r"(?<=[a-z])(?=[A-Z0-9])", "-", call).lower()


def used() -> dict[str, str]:
    calls = {}
    for path in TEMPLATES.rglob("*.jte"):
        if path.parent != ICONS:
            for call in CALL.findall(path.read_text(encoding="utf-8")):
                calls[call] = lucide_name(call)
    return calls


def download() -> tarfile.TarFile:
    with urllib.request.urlopen(URL) as response:
        data = response.read()
    algorithm, expected = INTEGRITY.split("-", 1)
    actual = base64.b64encode(hashlib.new(algorithm, data).digest()).decode()
    if actual != expected:
        sys.exit(f"{URL} does not match its pinned {algorithm}: got {actual}")
    return tarfile.open(fileobj=io.BytesIO(data), mode="r:gz")


def template(svg: str, name: str) -> str:
    svg = re.sub(r"<!--.*?-->", "", svg, flags=re.S)
    svg = re.sub(r"\s*(/?>)\s*", r"\1", " ".join(svg.split()))
    svg = re.sub(r'\s+xmlns="[^"]*"', "", svg)
    svg, classes = re.subn(r'class="[^"]*"', 'class="icon" aria-hidden="true" focusable="false"', svg, count=1)
    if classes != 1 or 'stroke="currentColor"' not in svg:
        sys.exit(f"lucide-static {VERSION} {name}.svg has changed form; check template() against it")
    return (f'<%-- Lucide {name}, lucide-static {VERSION}, ISC licence in LICENSE. '
            f"Written by scripts/lucide-icons.py; change that, not this file. --%>{svg}\n")


def main() -> None:
    calls = used()
    unknown = sorted(f"{call} ({name})" for call, name in calls.items() if name not in POLICY)
    if unknown:
        sys.exit("Not in POLICY in scripts/lucide-icons.py: " + ", ".join(unknown))
    archive = download()
    ICONS.mkdir(exist_ok=True)
    for call, name in sorted(calls.items()):
        try:
            member = archive.extractfile(f"package/icons/{name}.svg")
        except KeyError:
            member = None
        if member is None:
            sys.exit(f"lucide-static {VERSION} has no icon {name} (called as @template.icon.{call}())")
        (ICONS / f"{call}.jte").write_text(template(member.read().decode(), name), encoding="utf-8")
    licence = archive.extractfile("package/LICENSE")
    (ICONS / "LICENSE").write_bytes(licence.read())
    removed = [path.name for path in ICONS.glob("*.jte") if path.stem not in calls]
    for name in removed:
        (ICONS / name).unlink()
    print(f"lucide-static {VERSION}: {len(calls)} icons written"
          + (f", removed {', '.join(sorted(removed))}" if removed else ""))


if __name__ == "__main__":
    main()
