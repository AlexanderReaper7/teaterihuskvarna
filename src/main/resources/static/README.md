# Static files

Spring Boot serves this directory from the root path. `SecurityConfiguration` opens only `/css/**`, `/fonts/**` and `/img/**`, so this README stays unreachable.

Everything except [`css/site.css`](css/site.css) is copied unchanged from the survey of the old site in [design/old-site](../../../../design/old-site/README.md), which records where each file first came from.

| Path | Source | Licence |
| --- | --- | --- |
| [`css/site.css`](css/site.css) | Written for this site from the values in [tokens.css](../../../../design/old-site/tokens.css) | The project's own |
| `fonts/montserrat-*.woff2` | [`design/old-site/assets/fonts/`](../../../../design/old-site/assets/fonts/), from Google Fonts, variable 100 to 900, latin and latin-ext | SIL OFL 1.1, text in [fonts/OFL.txt](fonts/OFL.txt) |
| [`img/logo-negative.png`](img/logo-negative.png) | [`design/old-site/assets/brand/logo-negative.png`](../../../../design/old-site/assets/brand/logo-negative.png), from the old site's `wp-content/uploads/2023/06/FTH_ALF_neg_SV.png` | The association's mark |
| [`img/favicon.png`](img/favicon.png) | [`design/old-site/assets/brand/favicon.png`](../../../../design/old-site/assets/brand/favicon.png), from the old site's `wp-content/uploads/2023/06/favicon-32x32-1.png` | The association's mark |

The logo is a 1534x1444 raster, the only version the old site had. Replace it with a file from the association once they supply a vector original, as asked in the survey's questions for the board.
