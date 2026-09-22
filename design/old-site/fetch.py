#!/usr/bin/env python3
"""Re-downloads the reference assets in assets/ and the screenshots in screenshots/.

Everything this script writes is already committed. The script exists so the
provenance of each file is a runnable command rather than a sentence in a README,
and so the set can be refreshed while teaterihuskvarna.se is still up. The old
site goes away at launch (requirement M20), and after that this directory is the
only remaining record of what the produktagare said they liked.

Run: uv run python design/old-site/fetch.py [--screenshots]

--screenshots also re-renders screenshots/, which needs firefox on PATH. It is
off by default because rendering takes about a minute per page and the result
is not byte-stable, so a refresh would otherwise churn the diff for no reason.
"""
import argparse
import base64
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
SITE = "https://teaterihuskvarna.se"
UPLOADS = f"{SITE}/wp-content/uploads"

# A browser UA, because fonts.googleapis.com serves woff2 only to clients it
# believes can read it and falls back to ttf otherwise.
UA = "Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0"

# The three files that appear on all 88 pages of the old site. Everything else
# under wp-content/uploads is article content (mostly stock photography), not
# identity, so it is deliberately not mirrored here.
BRAND = {
    "logo-negative.png": f"{UPLOADS}/2023/06/FTH_ALF_neg_SV.png",
    "jatten-vist.png": f"{UPLOADS}/2023/06/jatten_vist.png",
    "favicon.png": f"{UPLOADS}/2023/06/favicon-32x32-1.png",
}

# The only three icons the old site draws, all from Font Awesome 6 Free. Taken
# from the upstream package rather than from the site's copy: Bricks is
# commercially licensed and its bundled build must not be redistributed here.
FA_VERSION = "6.7.2"
ICONS = {
    "facebook.svg": "brands/facebook.svg",
    "instagram-square.svg": "brands/square-instagram.svg",
    "arrow-right-long.svg": "solid/arrow-right-long.svg",
}

# Montserrat is the only webfont the old site loads. Latin covers Swedish
# (a-ring, a-diaeresis, o-diaeresis all sit below U+0100); latin-ext is kept for
# names that need it, and the two are separate files so the browser only
# downloads latin-ext when a glyph calls for it.
FONT_CSS = (
    "https://fonts.googleapis.com/css2"
    "?family=Montserrat:ital,wght@0,100..900;1,100..900&display=swap"
)
FONT_SUBSETS = ("latin", "latin-ext")

OFL = "https://raw.githubusercontent.com/google/fonts/main/ofl/montserrat/OFL.txt"

# Pages chosen to cover one instance of every layout the old site has. The
# produktagare praised the graphics without naming a page, so the set is the
# whole design rather than the front page.
SHOTS = {
    "home": "/",
    "news-index": "/news/",
    "news-article": "/news/medlemsbrev-januari-2025/",
    "about": "/om-foreningen/",
    "board": "/styrelsen/",
    "join": "/bli-medlem/",
    "partners": "/partners/",
    "production": "/produktion/ronja-rovardotter/",
    "ludde-award": "/luddepris/barngrupperna/",
}
# Width only. Firefox reads a bare --window-size width as "render the whole page
# at this width", which is what a design reference needs: the footer and the
# bottom of every list stay in frame. 2560 is the reviewing machine's actual CSS
# viewport. The old site has one breakpoint at 767px, so no width above that
# changes the layout, and the mobile shot is the only other arrangement there is.
DESKTOP_WIDTH = 2560
MOBILE_WIDTH = 390


def get(url, binary=True):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        data = r.read()
    return data if binary else data.decode("utf-8")


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data if isinstance(data, bytes) else data.encode("utf-8"))
    print(f"  {path.relative_to(HERE)}  {len(data)} bytes")


def fetch_brand():
    print("brand assets")
    for name, url in BRAND.items():
        write(HERE / "assets/brand" / name, get(url))


def fetch_icons():
    print(f"icons (Font Awesome Free {FA_VERSION})")
    base = f"https://raw.githubusercontent.com/FortAwesome/Font-Awesome/{FA_VERSION}/svgs"
    for name, path in ICONS.items():
        write(HERE / "assets/icons" / name, get(f"{base}/{path}"))


def fetch_fonts():
    print("fonts (Montserrat)")
    css = get(FONT_CSS, binary=False)
    out = HERE / "assets/fonts"
    faces = []
    # Google's stylesheet emits one @font-face per subset, each preceded by a
    # comment naming that subset. Splitting on the comment is what lets us keep
    # latin and latin-ext and drop cyrillic, greek and vietnamese.
    for subset, block in re.findall(r"/\* (\S+) \*/\s*(@font-face \{.*?\})", css, re.S):
        if subset not in FONT_SUBSETS:
            continue
        url = re.search(r"url\((\S+?)\)", block).group(1)
        style = re.search(r"font-style:\s*(\w+)", block).group(1)
        name = f"montserrat-{subset}-{style}.woff2"
        write(out / name, get(url))
        unicode_range = re.search(r"unicode-range:\s*([^;]+);", block).group(1)
        faces.append(
            "@font-face {\n"
            "  font-family: 'Montserrat';\n"
            f"  font-style: {style};\n"
            "  font-weight: 100 900;\n"
            "  font-display: swap;\n"
            f"  src: url('{name}') format('woff2');\n"
            f"  unicode-range: {unicode_range};\n"
            "}"
        )
    header = (
        "/* Generated by fetch.py. Self-hosting rule: no request to\n"
        " * fonts.googleapis.com, which is what the old site does and what sends\n"
        " * every visitor's IP address to Google on every page load. */\n\n"
    )
    write(out / "montserrat.css", header + "\n\n".join(faces) + "\n")
    write(out / "OFL.txt", get(OFL))


# Replayed pages load their icon font from teaterihuskvarna.se, which sends no
# Access-Control-Allow-Origin header, so a file:// page gets blocked and draws
# tofu boxes where the social icons belong. Re-declaring the same families from
# a data: URI sidesteps the check, because a data: URI has no origin to fail.
FA_FONTS = {
    "fa-brands-400": [("Font Awesome 6 Brands", 400)],
    "fa-regular-400": [("Font Awesome 6 Free", 400)],
    "fa-solid-900": [("Font Awesome 6 Free", 900), ("Font Awesome 6 Solid", 900)],
}
FA_DIR = f"{SITE}/wp-content/themes/bricks/assets/fonts/fontawesome"


def inlined_icon_font_css():
    faces = []
    for stem, families in FA_FONTS.items():
        b64 = base64.b64encode(get(f"{FA_DIR}/{stem}.woff2")).decode()
        for family, weight in families:
            faces.append(
                "@font-face{font-display:block;"
                f"font-family:'{family}';font-style:normal;font-weight:{weight};"
                f"src:url(data:font/woff2;base64,{b64}) format('woff2')" + "}"
            )
    return "<style>" + "".join(faces) + "</style>"


def screenshot(url, path, width, icon_css):
    """Renders url with firefox at the given width and writes a full-page PNG.

    The page is saved and replayed from a temporary file with three changes.
    The cookie panel ships hidden and is revealed by cookies.js, so dropping
    that one script tag leaves it hidden and the page undimmed. Every image
    carries a blank inline-SVG src plus the real URL in data-src, and the
    observer that swaps them never fires below the fold of a full-page render,
    so the swap is done here. The placeholder has to go first. An img carrying
    two src attributes keeps the first one and stays blank. The footer
    illustration uses the browser's own loading="lazy" instead, which a headless
    render never triggers because it never scrolls, so that attribute goes too.
    The icon font is inlined for the reason above.
    """
    html = get(url, binary=False)
    html = re.sub(r"<script[^>]*plugins/cookies[^>]*>\s*</script>", "", html)
    html = re.sub(r'\ssrc="data:image/svg\+xml,[^"]*"', "", html)
    html = re.sub(r"\sdata-(src|srcset)=", r" \1=", html)
    html = html.replace("bricks-lazy-hidden", "")
    html = html.replace('loading="lazy"', "")
    html = html.replace("</head>", icon_css + "</head>", 1)
    with tempfile.TemporaryDirectory() as tmp:
        page = Path(tmp) / "page.html"
        page.write_text(html, encoding="utf-8")
        profile = Path(tmp) / "profile"
        profile.mkdir()
        subprocess.run(
            ["firefox", "--headless", "-no-remote", "--profile", str(profile),
             "--screenshot", str(path), f"--window-size={width}",
             page.as_uri()],
            check=True, timeout=300,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )


def fetch_screenshots():
    if not shutil.which("firefox"):
        sys.exit("--screenshots needs firefox on PATH")
    print("screenshots")
    out = HERE / "screenshots"
    out.mkdir(exist_ok=True)
    icon_css = inlined_icon_font_css()
    for name, path in SHOTS.items():
        target = out / f"{name}.png"
        screenshot(SITE + path, target, DESKTOP_WIDTH, icon_css)
        print(f"  {target.relative_to(HERE)}")
    target = out / "home-mobile.png"
    screenshot(SITE + "/", target, MOBILE_WIDTH, icon_css)
    print(f"  {target.relative_to(HERE)}")


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--screenshots", action="store_true",
                    help="also re-render screenshots/ (needs firefox)")
    args = ap.parse_args()
    fetch_brand()
    fetch_icons()
    fetch_fonts()
    if args.screenshots:
        fetch_screenshots()


if __name__ == "__main__":
    main()
