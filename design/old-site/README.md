---
created: 2026-09-23
provenance: agent
description: What the old site's design looks like, measured off the live site.
---

# The old site's design, surveyed

Read off the live [teaterihuskvarna.se](https://teaterihuskvarna.se/) on 2026-09-22, because the produktägare said they liked its graphics. Nothing here is a decision. It records what the old site actually does, so that the choice of what to carry into the new one is argued against measured values instead of a memory of them.

The WordPress site closes at launch. [projektplan.md](../../docs/projektplan.md) puts migration of its content inside version 1, and the new site replaces it on the same domain ([first meeting](../../docs/meetings/2026-09-24-meeting-1.md#who-does-what)). That ends with the old design gone, and after that this directory is the only copy of it.

## Which graphics were praised is still unknown

"Liked the graphics" is the whole of what was said, and nobody asked which part. So this survey covers the entire design rather than guessing. The screenshots are here to be put in front of the board with a narrower question attached. At [the first meeting](../../docs/meetings/2026-09-24-meeting-1.md#how-the-site-looks) Klas answered that members should recognise the new site from the old one's visual style, which still names no single part.

## The design is seven colours, one typeface and no curved edges

Deep teal carries the identity. Header, footer and the front-page hero are all solid `#006d68`, body content sits on white, and panels are either pale mint or near-black. Nothing is rounded, nothing has a shadow, and no gradient appears anywhere. The restraint is the reason it still looks current after three years of neglect, and it is the part most worth keeping.

All values below are in [tokens.css](tokens.css) as custom properties with the role each one plays.

### Palette

Bricks Builder stores these under generated names like `--bricks-color-cwmskc` in a `<style>` block in every page's `<head>`. Seven are referenced by at least one rule.

| Value | Role | Where it appears |
| --- | --- | --- |
| `#006d68` | primary | Header, footer, hero background, primary button, current page number |
| `#168a80` | primary hover | Links in body copy, and the primary button on hover |
| `#ee6810` | secondary | Link hover, social-icon hover, card hover fill. Never a resting background |
| `#ebfaf5` | light | Text on teal, fill of light cards, and the default border colour site-wide |
| `#3e3939` | muted | Fill of the Luddepriser and Produktioner list cards, with light text |
| `#000000` | dark | Declared as the dark role. Body text does not use it |
| `#ffffff` | white | Page background, and button text on hover |

An eighth, `#55726d`, is declared in the palette and referenced by no rule. It was considered and dropped.

Three more colours render that nobody chose. They are Bricks defaults left unoverridden, and they are why the design looks less consistent than it is. Body copy is `#363636` rather than any palette value. The main navigation is `#f5f5f5` rather than the mint that every other element on teal uses. The production and news sidebars are filled `#f5f5f5` while every other panel is mint or muted.

### Type

Montserrat is the only webfont, and it is used only for the front-page hero at weight 800. Everything else, including every page title, falls through to whatever sans-serif the visitor's operating system supplies. The site therefore renders in Segoe UI on Windows, in the system font on macOS and in Roboto on Android, with Montserrat appearing on one element of one page.

| Element | Size | Weight | Notes |
| --- | --- | --- | --- |
| Hero, line 1 | 48px | 800 | Montserrat. "FÖRENINGEN" |
| Hero, line 2 | 83px | 800 | Montserrat. "TEATER" |
| Hero, line 3 | 46px | 800 | Montserrat. "I HUS" plus the typed word |
| Page title (h1) | 70px | 900 | Uppercase, system font. 50px below 478px |
| Card title | 20px | bold | |
| Card meta | 12px | | Year on production cards |
| Body | 15px | 400 | Line height 1.7, colour `#363636` |
| Body link | 15px | 700 | Every `<a>` in body copy is bold |
| Social icon | 40px | | Footer only |

### Space and form

Two breakpoints are declared and one does the work. At 767px the layout collapses to a single column and the navigation becomes a hamburger. At 478px a single heading drops to 50px. Above 767px nothing changes at any width, so the design is the same on a laptop and on a 4K monitor, with the extra width going to the margins.

Sections are padded 100px at the top and 150px at the bottom, dropping to 50px on both at mobile width. The footer is heavier still at 150px and 200px. Cards are padded 40px in the main column and 20px in sidebars. Grid gaps are 20px, or 40px between the main column and the sidebar. Content grids are two columns at ratios of 1:2, 2:1 or 1:3, collapsing to one column at the breakpoint.

No rule the design owns sets `border-radius`, `box-shadow` or a gradient. The page does ship WordPress's unused block-editor gradient presets, and nothing on the site uses one. Depth is done with overlap instead. On the front page the newsletter card is pulled up over the photograph behind it by a negative top margin of 20% of its own width, and it is the only three-dimensional effect on the site.

### Icons

Three, all from Font Awesome 6 Free. Instagram and Facebook in the footer, and a long right arrow inside the primary button. Lists use a literal `»` character rather than an icon. There is no icon system to inherit, which is a reason to pick a set on the new site's own terms rather than to carry Font Awesome forward for the sake of three glyphs.

### The one piece of real personality

The front-page hero types itself. "FÖRENINGEN TEATER I HUS" is static, and then a fourth word is typed in after it, cycling VAGN, KUR, DJUR, ARREST and finally KVARNA. Husvagn, huskur, husdjur, husarrest, Huskvarna. It runs once at 55ms per character after a one-second delay and then stops on the association's own name.

Everything else on the site is a competent, quiet layout. This is the part someone actually thought of, and it is the single most likely answer to what "the graphics" meant. Ask about it first.

## The accent colour cannot carry text, and R006 is a MUST

Requirement R006 commits version 1 to WCAG 2.1 AA. Measured against it, the teal core passes and the orange accent does not. AA wants 4.5:1 for body text and 3:1 for large text and interface components.

| Pair | Ratio | Verdict |
| --- | --- | --- |
| `#ebfaf5` on `#006d68`, text on teal | 5.76 | Passes |
| `#f5f5f5` on `#006d68`, main navigation | 5.69 | Passes |
| `#363636` on white, body text | 12.08 | Passes |
| `#ebfaf5` on `#3e3939`, dark card text | 10.56 | Passes |
| `#168a80` on white, body link | 4.22 | Fails body text, passes large text |
| `#ee6810` on white, link hover | 3.17 | Fails body text, passes large text |
| `#ee6810` on `#ebfaf5`, orange on a mint card | 2.95 | Fails everything |
| `#ee6810` on `#006d68`, icon hover in the footer | 1.96 | Fails everything |

The pattern is consistent. Orange is used exactly where it cannot carry contrast, and the two worst cases are both hover states, so a keyboard or pointer user is the one who gets the unreadable version.

Darkening the orange along its own hue fixes the two on light backgrounds. `#b64f0c` reaches 5.11 on white and 4.75 on mint while reading as the same colour. Orange on teal has no such fix. Even a pale tint at 85% lightness only reaches 4.42 on `#006d68`, and by then it is beige. Footer icon hover should use white or mint and change something other than hue.

That is a recommendation, not a change. The palette is the association's, and picking the replacement value is theirs or the produktägare's to approve.

## The screenshots

Rendered at 2560px wide, full page, so the footer and the bottom of every list are in frame. The old site has one working breakpoint at 767px, so these plus the mobile capture are every arrangement it has.

Every email address, phone number and postal address in them is covered by a black box. The pages published board members' and volunteers' private contact details, and the new site is checked for exactly those ([projektplan.md](../../docs/projektplan.md)), so they stay out of this repository too. Names and photographs are left in. They are what the layout is built around, and whether the association may publish them is the photo consent question in [open-questions.md](../../docs/open-questions.md).

| File | What it shows |
| --- | --- |
| [home.png](screenshots/home.png) | The typing hero, the overlapped newsletter card, mint news cards against muted sidebar cards |
| [home-mobile.png](screenshots/home-mobile.png) | The same page at 390px. Hamburger navigation, single column, the hero card overlap surviving the collapse |
| [news-index.png](screenshots/news-index.png) | The full news list. The longest page on the site at 8945px |
| [news-article.png](screenshots/news-article.png) | An article, and how body copy is set |
| [about.png](screenshots/about.png) | Two-column prose with a document sidebar, and the clearest view of the footer |
| [board.png](screenshots/board.png) | Person cards with photographs, which requirement R004 asks for again |
| [production.png](screenshots/production.png) | A production page. Shows the 70px uppercase title and the grey Bricks-default sidebar |
| [ludde-award.png](screenshots/ludde-award.png) | A Ludde award page |
| [join.png](screenshots/join.png) | The membership page the new R005 form replaces |
| [partners.png](screenshots/partners.png) | Partners, which is prose and links with no partner logos to carry over |

## What to keep and what to drop

Keep the palette, the square edges, the generous section padding and the overlap trick. Keep the typing hero, which is cheap to reimplement in JTE and is the site's only memorable moment.

Drop three things. The system-font body stack is the largest single visual improvement available, because it is the reason the site looks different on every visitor's machine, and Montserrat is already self-hosted here at no extra cost. The three Bricks leftovers (`#363636` body text, `#f5f5f5` navigation, `#f5f5f5` sidebars) should resolve to palette values. The orange needs the contrast fix above before it goes near text.

One thing cannot be carried at all. The logo exists only as a 1534x1444 PNG in its white-on-dark version, and no vector and no dark-on-light variant exist anywhere on the old site. The footer illustration of Jätten Vist is worse. Its largest file is 180x338, already below what a 2x display needs at the size the old site draws it. Both need to come from the association's own files, not from the web server.

## Questions for the board

C20, C21 and C23 were answered at [the first meeting](../../docs/meetings/2026-09-24-meeting-1.md#how-the-site-looks). C23 went to the team as T5 in [open-questions.md](../../docs/open-questions.md). C19, and C24 for the original files, are in [the next meeting](../../docs/meetings/2026-10-01-meeting-2.md).

## Files here, and where they came from

Everything here was captured from the live site on 2026-09-22 and is not meant to be refreshed. The screenshots are headless Firefox renders of `/`, `/news/`, `/news/medlemsbrev-januari-2025/`, `/om-foreningen/`, `/styrelsen/`, `/bli-medlem/`, `/partners/`, `/produktion/ronja-rovardotter/` and `/luddepris/barngrupperna/`, with lazy-loaded images forced in and the cookie banner removed.

| Path | Source | Licence |
| --- | --- | --- |
| `assets/brand/logo-negative.png` | `wp-content/uploads/2023/06/FTH_ALF_neg_SV.png` | The association's mark |
| `assets/brand/jatten-vist.png` | `wp-content/uploads/2023/06/jatten_vist.png` | Unknown artist. The association may use it ([first meeting](../../docs/meetings/2026-09-24-meeting-1.md#how-the-site-looks)) |
| `assets/brand/favicon.png` | `wp-content/uploads/2023/06/favicon-32x32-1.png` | The association's mark |
| `assets/fonts/montserrat-*.woff2` | Google Fonts, variable 100 to 900, latin and latin-ext | SIL OFL 1.1, text in `OFL.txt` |
| `assets/fonts/montserrat.css` | Rewritten from the Google Fonts stylesheet to point at the local files | |
| `assets/icons/*.svg` | Font Awesome Free 6.7.2, from the upstream repository | CC BY 4.0, attribution required |
| `screenshots/*.png` | Rendered from the live pages | The association's content |
| `tokens.css` | Read out of the pages' inline CSS by hand | |

Three things are deliberately absent. The Bricks theme's own CSS is not copied, because Bricks is commercially licensed. A colour value and a font size are facts about the design and are recorded in `tokens.css`; the theme's expression of them is not. The article photography under `wp-content/uploads` is not mirrored either, because most of it is stock imagery the association licensed for the old site and it is content rather than identity. The icons come from Font Awesome upstream rather than from the copy the theme bundles, for the same licensing reason.

The fonts are self-hosted here on purpose. The old site requests Montserrat from `fonts.googleapis.com` on every page load, which sends every visitor's IP address to Google before they have consented to anything.
