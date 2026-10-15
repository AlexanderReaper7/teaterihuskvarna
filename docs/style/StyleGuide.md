---
created: 2026-10-15
provenance: agent
description: What the site looks like: colours, typography, shapes and components.
---

# Style guide

Provenance: agent. Henry Andersson asked for a one-page style guide (colours, typography, shapes, components) in his feedback on the mobile header. The user asked an agent to write the first version. Nothing here is a decision by the user or the product owner. The values were read from one screenshot of the mobile header, not from the CSS, so check each against [`src/main/resources/static`](../src/main/resources/static/) before relying on it. Rows marked **TBD** are not known yet.

No requirement in [requirements.md](requirements.md) asks for this page. R006 #9 in that file ("Fungerar på mobil, tillgänglighet enligt WCAG 2.1 AA", MUST) is the nearest one, and it sets the contrast rules below. The same rule is in [projektplan.md](projektplan.md), section "Accessibility needs automated and manual checks". See [Sources](#sources) for where every other claim comes from.

## Colours

| Role | Value | Source |
| --- | --- | --- |
| Header and menu background | `#006d68` | Screenshot |
| Menu button border | `#025955` | Screenshot |
| Text on the header | white | Screenshot |
| Page background | `#fcfcfb` | Screenshot |
| Content background | `#ffffff` | Screenshot |
| Accent, links, error colour | TBD | |

| Light text and person icon | `#ebfaf5` | Screenshot |
| Menu button icon, closed (three lines) | `#082322` | Screenshot |
| Menu button icon, open (cross) | `#0b0b0b` | Screenshot |
| Divider between menu rows | `#2f8984` | Screenshot |

Icons count as non-text contrast and need 3:1 against their background (WCAG 2.1 AA, 1.4.11). By my estimate from the screenshot values, the three lines (`#082322`, about 2.6:1) fail and the cross (`#0b0b0b`, about 3.2:1) only just passes. The person icon (`#ebfaf5`, about 5.8:1) passes. Verify with a contrast tool before changing anything. The likely fix is the same light colour as the person icon, `#ebfaf5`.

White on `#006d68` must meet 4.5:1 for normal text (WCAG 2.1 AA). Measure it when the CSS is read.

## Typography

| Use | Style |
| --- | --- |
| Site name ("TEATER") | Bold, uppercase, centred in the header |
| Menu items | Regular, white, one per row |
| Font family, sizes, line height | TBD |

Bold is used as it is now. Henry finds the current use of bold well implemented, so do not add more weights without a reason.

## Shapes

- Large rounded corners on the header and the open menu.
- The menu button is a square with rounded corners and a thin border in a darker teal.
- Menu items are separated by one thin, lighter line.

## Underlines in the menu

Rule from Henry's feedback: the underlines (Swedish "understreck") on the desktop menu also follow into the hamburger menu, so the site looks the same on desktop and mobile.

- The open menu in the screenshot shows no underline under Kalender, Nyheter or Produktioner, so this is not implemented yet.
- Whether he means an underline on every item, or only on the active or hovered one, is not stated. **TBD**, ask Henry.
- The underline must also show in the open hamburger menu on a touch screen, where there is no hover.

## Components

| Component | States | Notes |
| --- | --- | --- |
| Header | One | Button on the left, site name in the centre, person icon on the right |
| Menu button | Closed (`aria-expanded="false"`, three lines `#082322`), open (`aria-expanded="true"`, cross `#0b0b0b`) | Dark icon on dark teal; contrast too low, see Colours |
| Menu | Closed (hidden), open (rows: Kalender, Nyheter, Produktioner, and more) | Full width under the header |
| Person icon | One, colour `#ebfaf5` | Leads to login and the member page `/medlem` |
| Footer | One | Left as it is (Henry's feedback) |
| Forms, buttons, cards, error messages | TBD | |

## Open

- Where the CSS defines these values, so the guide can name the variables instead of hex codes.
- Whether the product owner wants the guide as a task of its own. It is not a requirement.

## Logo and icons

Where these are loaded from is **not known**. The repository's static files were not available when this was written, so every path below is TBD and has to be filled in from [`src/main/resources/static`](../src/main/resources/static/) and the templates in [`src/main/jte`](../src/main/jte/).

| Item | What the screenshot shows | Colour | File and how it is loaded |
| --- | --- | --- | --- |
| Logo | The word "TEATER", bold, uppercase, centred. No image logo is visible. | `#ebfaf5` | TBD: text in a template or an SVG file |
| Menu button, closed | Three lines | `#082322` now, should be `#ebfaf5` | TBD |
| Menu button, open | A cross | `#0b0b0b` now, should be `#ebfaf5` | TBD |
| Person icon | An outlined person | `#ebfaf5` | TBD |

### Logo text must be sharp

Feedback from the user: the text that comes with the logo must be in focus, as on the [old site](https://teaterihuskvarna.se/news/foreningen-byter-hemsida/), and not blurry. Taken as: sharp edges at every screen size and pixel density.

What the old site does, read from its page source on 2026-10-15:

- The logo is one image, [`FTH_ALF_neg_SV-300x282.png`](https://teaterihuskvarna.se/wp-content/uploads/2023/06/FTH_ALF_neg_SV-300x282.png), a 300 by 282 pixel PNG, linked to the start page, with the alt text "teaterihuskvarna.se". The file name suggests a negative (light on dark) Swedish version of the association's logo.
- The text is part of that image, not live text.

What follows for the new site (an agent's reasoning, not verified in a browser):

- A raster image shown larger than its pixel size, or on a high-density phone screen, looks blurry. A 300 pixel wide PNG is too small for a 2x or 3x screen.
- Prefer an SVG logo, where the text is vector shapes and stays sharp at any size. If only the PNG exists, ask the association for the original vector file.
- If the text is set in the page instead of the logo, use real text in the header's font and colour `#ebfaf5`, never text inside a scaled image.
- Do not use `transform: scale`, sizes that land between pixels, or `filter: blur` on the logo.
- Give the logo an accessible name, and keep the link to the start page.

Open: whether Henry means the lettering inside the logo image or the word "TEATER" next to it. **TBD**, ask him.

Rules for the SVG files, so the colour is found in one place:

- Draw each icon with `stroke="currentColor"` or `fill="currentColor"`, never a hex code inside the file. The colour then comes from the CSS `color` of the element, and the guide needs one value per role instead of one per file.
- An SVG loaded through `<img src>` ignores `currentColor`, because it is a separate document. To colour such a file, inline it in the template or use a CSS mask. Which of the three is used is TBD.
- The icon colour is the light colour from [Colours](#colours), `#ebfaf5`, on `#006d68`. That colour is the one the person icon already has, and it gives about 5.8:1 against the header.
- Each icon button keeps an accessible name, since the icon alone says nothing to a screen reader.

## Preview

Mockups of what this guide describes, drawn by an agent with the guide's colours, light icons and underlined menu items. They are not screenshots of the running site. The desktop menu items are examples, since the real desktop menu was not seen, and the font is a stand-in.

![Mobile header, closed and open](img/style-guide-mobile.png)

![Desktop header](img/style-guide-desktop.png)

## Sources

| Claim | Source |
| --- | --- |
| Colours, shapes, the closed and open menu | The screenshot of the mobile header the user attached on 2026-10-15. It is not stored in the repository. The values were sampled from its pixels. |
| Keep underlines in the hamburger menu, keep the footer, bold is good, a one-page style guide would help | Email from Henry Andersson, 2026-10-15. It is not stored in the repository. |
| Contrast 4.5:1 for text | [WCAG 2.1, success criterion 1.4.3, Contrast (Minimum)](https://www.w3.org/TR/WCAG21/#contrast-minimum) |
| Contrast 3:1 for icons | [WCAG 2.1, success criterion 1.4.11, Non-text Contrast](https://www.w3.org/TR/WCAG21/#non-text-contrast) |
| Accessibility is required at level AA | R006 #9 in [requirements.md](requirements.md), and [projektplan.md](projektplan.md) |
| Logo and icon colours, and the observation that no image logo is visible | The same screenshot, sampled by an agent |
| The old site's logo image, its size and link | Page source of [Föreningen byter hemsida – Teater i Huskvarna](https://teaterihuskvarna.se/news/foreningen-byter-hemsida/), fetched 2026-10-15 |
| Contrast figures (about 2.6:1, 3.2:1, 5.8:1) | An agent's calculation from the sampled colours, not measured with a tool |
| Terms such as member page and login | [GLOSSARY.md](../GLOSSARY.md) |