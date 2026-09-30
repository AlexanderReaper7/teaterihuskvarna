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
