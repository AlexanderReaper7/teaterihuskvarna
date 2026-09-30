---
created: 2026-09-28
provenance: user
description: How the public pages get their content from Sanity, and how editors preview it.
---

# 0021: Portable Text rendered in Java, series as documents, preview in Sanity's Presentation tool

The Decision section is the user's. Everything under Agent notes is an agent's.

## Decision

- Rich text is Sanity's Portable Text, as in the Studio from pull request #49, and the application renders it to HTML with its own Java code. This replaces the Markdown field that [0006](0006-sanity-for-now.md) planned. Decided by the user on 2026-09-28.
- The Studio from pull request #49 is taken over and may be rewritten. Decided by the user on 2026-09-28.
- A series (R002) is a document type of its own, `serie`, that an event refers to, rather than a fixed list in the schema. Decided by the user on 2026-09-28.
- R009, preview before publishing, is Sanity's Presentation tool showing the site with drafts. Decided by the user on 2026-09-28.

- Development runs Studio in Compose; association editors use Sanity hosting. The same Studio code serves both. Compose keeps the developer setup together, while Sanity hosting gives editors access independent of the application server and avoids maintaining editor hosting. Decided by the user on 2026-09-30.

- Development reads the real Sanity `dev` dataset. Only automated tests use fixture content, so Studio edits reach the running application while tests remain independent of Sanity. Decided by the user on 2026-09-30.

- Import the existing invented demo content into the `dev` dataset for presentation. Authorized by the user on 2026-09-30.

- The shared development stack exposes Studio through its proxy at `/studio/`, protected by the existing share password. Administration links open that shared editor. Decided by the user on 2026-09-30.

## Agent notes

Written by an agent on 2026-09-28. Everything below is a default the user has not reviewed.

### How it works

- [`ContentService`](../../src/main/java/se/teaterihuskvarna/content/ContentService.java) fetches every document of a type in one GROQ query and filters and sorts in Java. The site has a few hundred documents at most, and a fixture needs no query language.
- [`ContentCache`](../../src/main/java/se/teaterihuskvarna/content/ContentCache.java) keeps each type for 45 seconds. Sanity's webhook, `POST /api/sanity/webhook`, signed with `SANITY_WEBHOOK_SECRET`, expires every copy at once. That meets R008's minute with the webhook, and without it. When Sanity fails, the last copy stays on the page.
- The application queries `api.sanity.io`, not the CDN, because the CDN's own delay would add to R008's minute.
- [`PortableText`](../../src/main/java/se/teaterihuskvarna/content/PortableText.java) renders only the styles and marks [`blockContent.ts`](../../studio/schemaTypes/blockContent.ts) offers, escapes all text, and drops a link that is not http, https, mailto, tel or a path on the site.
- Preview: the Presentation tool writes a secret to the dataset and opens `/forhandsgranska/start` in a frame. The application checks the secret with Sanity, the same query `@sanity/preview-url-secret` makes, and sets a cookie holding an expiry time signed with a key made at startup. The cookie is `SameSite=None; Secure; Partitioned`, so it reaches the site only inside the Studio's frame. A page with that cookie reads the drafts perspective, which needs `SANITY_API_KEY`, and shows a banner. A REST client gets the same pass from `POST /api/content/preview` and sends it in the `Preview-Pass` header, since every capability has an endpoint ([0014](0014-one-service-layer-two-adapters.md)).
- Content pages send `Content-Security-Policy: frame-ancestors 'self' <SANITY_STUDIO_URL>`, every other page `X-Frame-Options: DENY`.
- The preview shows drafts, not click-to-edit overlays. Overlays need stega encoding, which has no Java library, as [0006](0006-sanity-for-now.md) found. The Presentation tool's locations still link each document to the pages that show it.
- Development reads the same Sanity `dev` dataset as Studio. Integration and browser tests explicitly read [`fixture.json`](../../src/main/resources/content/fixture.json), with dates relative to today, so tests remain independent of Sanity and get the same content every run.
- On 2026-09-30, the agent imported 16 published documents into project `gk5ur3tb`, dataset `dev`: 2 series, 4 events, 3 news items, 5 pages and 2 partners. The import preserved fixture IDs and content, converted series into references and resolved relative dates against 2026-09-30 in Europe/Stockholm. These dates stay fixed in Sanity; editors change them in Studio. The import created documents without overwriting existing ones, and a query verified every imported field against the generated documents.
- The start page is `/` in every profile. Developer tools use `/dev` under the dev profile, moved at the user's request on 2026-09-30 so development presents the public site at its normal address.

### Studio hosting

[`compose.dev.yaml`](../../compose.dev.yaml) starts the development Studio using [`studio/Dockerfile`](../../studio/Dockerfile). Its default address is [localhost:3333](http://localhost:3333/), its dataset is `dev`, and its preview points to the application on `PROXY_HTTP_PORT`. The application uses this Studio origin for administrator links and frame permissions unless `SANITY_STUDIO_URL` overrides it. The end-to-end stack disables Studio and keeps fixture content.

[`compose.share.yaml`](../../compose.share.yaml) serves Studio at `/studio/` on `SHARE_URL`, with the same password as the site and Mailpit. It configures the editor's base path, preview origin and administration link. Frame permissions use only the origin of that link. On 2026-09-30, the agent added the shared origin to project `gk5ur3tb`'s CORS settings with credentials, and allowed that hostname in Studio's development server.

Editors use the Studio deployed to Sanity hosting, with the `production` dataset and the public site's preview origin. [`studio/README.md`](../../studio/README.md) describes deployment and the application settings. Both Studios use Sanity's hosted content database. Hosting the development editor locally does not make content editing work offline.

### Not done

The old WordPress site's content is not migrated. Editors enter it in the Studio.
