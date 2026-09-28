# Sanity Studio

Where the association's editors write the public content: events, series, news, the fixed pages and partners. The application reads it through Sanity's API, per [decisions/0021](../docs/decisions/0021-content-from-sanity.md).

## Running it

```sh
npm install
npm run dev       # http://localhost:3333
npm run typecheck
npm run lint
npx sanity schema validate
npm run deploy    # publishes the Studio to *.sanity.studio
```

## Settings

`studio/.env`, git-ignored, or the environment of the commands above:

| Variable | Default | Meaning |
|---|---|---|
| `SANITY_STUDIO_DATASET` | `dev` | The dataset to edit. `production` is the real site. |
| `SANITY_STUDIO_PREVIEW_ORIGIN` | `http://localhost:8000` | The site the Presentation tool shows, such as `https://teaterihuskvarna.se`. |

## Connecting the site

The application needs these in its `.env`, listed in [`.env.example`](../.env.example):

- `SANITY_API_KEY`, a token with read access ("Viewer"). The Presentation preview needs it to check the secret the Studio writes, and a private dataset needs it for everything.
- `SANITY_STUDIO_URL`, the Studio's own origin, such as `https://teaterihuskvarna.sanity.studio`. The site lets only that origin show its pages in a frame.
- `SANITY_WEBHOOK_SECRET`, the secret of a webhook made under the project's API settings in [sanity.io/manage](https://www.sanity.io/manage): URL `https://<site>/api/sanity/webhook`, method POST, trigger on create, update and delete, filter empty, the same secret. Without it, a publish shows on the site within 45 seconds instead of at once.
- `SANITY_STUDIO_URL` must also be added under the project's CORS origins, with credentials, which `sanity deploy` does itself for a `*.sanity.studio` address.

## Fields

The schema's field names are the ones the application reads in [`ContentService.java`](../src/main/java/se/teaterihuskvarna/content/ContentService.java). Renaming a field here without renaming it there hides the field on the site. A new style or mark in [`blockContent.ts`](schemaTypes/blockContent.ts) needs the same in [`PortableText.java`](../src/main/java/se/teaterihuskvarna/content/PortableText.java).
