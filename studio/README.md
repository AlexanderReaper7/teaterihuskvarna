# Sanity Studio

Where the association's editors write the public content: events, series, news, the fixed pages and partners. The application reads it through Sanity's API, per [decisions/0021](../docs/decisions/0021-content-from-sanity.md).

## Running it

Development runs Studio in the project's Compose stack. From the repository root, run `docker compose up -d --build`, then open [http://localhost:3333/](http://localhost:3333/). The container runs as the Node user, reads the `dev` dataset by default and previews the application on its configured port. `STUDIO_HTTP_PORT` changes the editor's host port. Rebuild with `docker compose up -d --build studio` after editing Studio code.

The local Studio still reads and writes Sanity's hosted database. Log in with a Sanity account that has access to the project. The development application reads the same Sanity project and dataset by default. `SANITY_API_KEY` supplies access to private content and draft preview. Published edits appear within 45 seconds, or sooner with the publish webhook. Automated tests use fixture content.

With [`compose.share.yaml`](../compose.share.yaml), Studio runs under `/studio/` on `SHARE_URL`, behind the same password as the shared application. Its preview uses the shared site, and the administrator link opens the shared editor. The direct local address also uses `/studio/` in this mode. Add the shared origin, without the path, to Sanity's CORS origins with credentials.

Running Studio outside Compose is also supported:

```sh
npm install
npm run dev       # http://localhost:3333
npm run typecheck
npm run lint
npx sanity schema validate
npm run deploy    # publishes the Studio to *.sanity.studio
```

## Deploying for editors

Editors use Sanity hosting, per [0021](../docs/decisions/0021-content-from-sanity.md). Deploy from this directory with `SANITY_STUDIO_DATASET=production` and `SANITY_STUDIO_PREVIEW_ORIGIN` set to the public site's HTTPS origin. For example, in a POSIX shell:

```sh
SANITY_STUDIO_DATASET=production SANITY_STUDIO_PREVIEW_ORIGIN=https://teaterihuskvarna.se npm run deploy
```

The hostname is chosen during deployment. Set the application's `SANITY_STUDIO_URL` to the resulting `*.sanity.studio` origin and configure the connections below. Use the association-controlled Sanity account. Changing Studio code requires another deployment; editors publishing content do not redeploy Studio.

## Settings

`studio/.env`, git-ignored, or the environment of the commands above:

| Variable | Default | Meaning |
|---|---|---|
| `SANITY_STUDIO_DATASET` | `dev` | The dataset to edit. `production` is the real site. |
| `SANITY_STUDIO_PREVIEW_ORIGIN` | `http://localhost:8000` | The site the Presentation tool shows, such as `https://teaterihuskvarna.se`. |

## Connecting the site

The application needs these in its `.env`, listed in [`.env.example`](../.env.example):

- `SANITY_API_KEY`, a token with read access ("Viewer"). The Presentation preview needs it to check the secret the Studio writes, and a private dataset needs it for everything.
- `SANITY_STUDIO_URL`, the Studio's address, such as `https://teaterihuskvarna.sanity.studio` or the shared site's `/studio/` URL. The site lets only that address's origin show its pages in a frame.
- `SANITY_WEBHOOK_SECRET`, the secret of a webhook made under the project's API settings in [sanity.io/manage](https://www.sanity.io/manage): URL `https://<site>/api/sanity/webhook`, method POST, trigger on create, update and delete, filter empty, the same secret. Without it, a publish shows on the site within 45 seconds instead of at once.
- The origin of `SANITY_STUDIO_URL`, without its path, must also be added under the project's CORS origins with credentials. `sanity deploy` does this itself for a `*.sanity.studio` address.

## Fields

The schema's field names are the ones the application reads in [`ContentService.java`](../src/main/java/se/teaterihuskvarna/content/ContentService.java). Renaming a field here without renaming it there hides the field on the site. A new style or mark in [`blockContent.ts`](schemaTypes/blockContent.ts) needs the same in [`PortableText.java`](../src/main/java/se/teaterihuskvarna/content/PortableText.java).

## Lists, addresses and language

[`structure.ts`](structure.ts) orders the content list for editors: upcoming events soonest first, then past events, news newest first, the rest by name. "Generera" makes an address with [`slug.ts`](slug.ts), which writes å and ä as a and ö as o, where Sanity's default follows German and writes ö as oe. The interface is Swedish through `@sanity/locale-sv-se`, and only the project's administrators see the Vision query tool.

## Importing the old site

[`import/wordpress.ts`](import/wordpress.ts) turns a snapshot of the old WordPress site into an NDJSON file for `sanity dataset import`. The snapshot is not in the repository, because it holds members' names, addresses and numbers. The one read on 2026-10-24 is at `~/Projects/scratch/teaterihuskvarna-wp` on the owner's machine, with the alt texts the user approved in `alt-text/approved.json`.

```sh
npx sanity exec import/wordpress.ts -- <snapshot> <snapshot>/alt-text/approved.json wordpress.ndjson
npx sanity dataset export dev dev-before-import.tar.gz
npx sanity dataset import wordpress.ndjson --dataset dev --replace
```

Both datasets answer queries without a token, so whatever is imported is public. The script therefore writes "(kontaktuppgift borttagen)" in place of a private e-mail address or a mobile number, unless given `--keep-private-contacts`. An image without an approved alt text is left out. The script prints what it left out and why: links to files the old site hosted, posts that were private in WordPress and become drafts, and the pages the new site has no address for.

The ids come from WordPress, as `wp-<id>`, and the three fixed pages and Jönköpings kommun keep the ids they already have, so a second import replaces the first one's documents instead of adding copies. The same snapshot gives a byte-identical file.

## Dependency overrides

`overrides` in [`package.json`](package.json) raise four packages that Sanity's CLI pulls in at versions with known vulnerabilities. Upstream still pinned all four on 2026-10-26, so neither `npm update` nor Dependabot could raise them:

| Package | Pulled in by | Pinned upstream | Override |
|---|---|---|---|
| `js-yaml` | `@vercel/frameworks` 3.29.0 | 3.13.1 | `^3.15.2` |
| `smol-toml` | `@vercel/frameworks` 3.29.0 | 1.5.2 | `^1.9.1` |
| `uuid` | `typeid-js` 1.2.0 | `^10.0.0` | `^11.1.1` |
| `undici` | `@module-federation/dts-plugin` 2.9.2 | 7.29.1 | `^7.30.1` |

Each override stays inside the major version the parent package uses: js-yaml 3 still has `safeLoad`, which `@vercel/frameworks` calls. Dependabot does not remove an override, so when one of the parents raises its own version, delete that entry and run `npm install` and `npm audit`.
