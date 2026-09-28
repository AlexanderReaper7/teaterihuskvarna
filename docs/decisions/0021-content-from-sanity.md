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
- The dev profile and the tests read [`fixture.json`](../../src/main/resources/content/fixture.json) instead of Sanity, with dates relative to today, so the e2e suite gets the same content every run.
- The start page is `/start` under the dev profile, because the development index has `/`.

### Not done

The old WordPress site's content is not migrated. Editors enter it in the Studio.
