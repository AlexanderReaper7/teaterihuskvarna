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

- Preview-secret exchange allows 20 requests per minute per client IP across the Studio and REST entry points. Exhausting that allowance returns HTTP 429. Decided by the user on 2026-09-30.

- Preview pages load Sanity's visual-editing script, built into the application image, and mark every field they show with a `data-sanity` attribute, Portable Text down to each block, so the Presentation tool opens a field from a click on the page. A preview page reloads when a draft is saved. Decided by the user on 2026-11-04.

## Agent notes

Written by an agent on 2026-09-28. Everything below is a default the user has not reviewed.

### How it works

- [`ContentService`](../../src/main/java/se/teaterihuskvarna/content/ContentService.java) fetches every document of a type in one GROQ query and filters and sorts in Java. The site has a few hundred documents at most, and a fixture needs no query language.
- [`ContentCache`](../../src/main/java/se/teaterihuskvarna/content/ContentCache.java) keeps each type for 45 seconds. Sanity's webhook, `POST /api/sanity/webhook`, signed with `SANITY_WEBHOOK_SECRET`, expires every copy at once. That meets R008's minute with the webhook, and without it. When Sanity fails, the last copy stays on the page.
- The application queries `api.sanity.io`, not the CDN, because the CDN's own delay would add to R008's minute.
- [`PortableText`](../../src/main/java/se/teaterihuskvarna/content/PortableText.java) renders only the styles and marks [`blockContent.ts`](../../studio/schemaTypes/blockContent.ts) offers, escapes all text, and drops a link that is not http, https, mailto, tel or a path on the site.
- Preview: the Presentation tool writes a secret to the dataset and opens `/forhandsgranska/start` in a frame. The application checks the secret with Sanity, the same query `@sanity/preview-url-secret` makes, and sets a cookie holding an expiry time signed with a key made at startup. The cookie is `SameSite=None; Secure; Partitioned`, so it reaches the site only inside the Studio's frame. A page with that cookie reads the drafts perspective, which needs `SANITY_API_KEY`, and shows a banner. A REST client gets the same pass from `POST /api/content/preview` and sends it in the `Preview-Pass` header, since every capability has an endpoint ([0014](0014-one-service-layer-two-adapters.md)).
- Each preview pass expires one hour after exchange. Restarting the application invalidates every pass because the signing key exists only in memory. The one-hour lifetime is existing behavior, documented by the agent on 2026-09-30.
- On 2026-09-30, the agent implemented the preview allowance in [`Previews`](../../src/main/java/se/teaterihuskvarna/content/Previews.java). A rolling minute counts attempts before querying Sanity, including invalid secrets and query failures. Blank secrets trigger no query and consume no allowance. The service keeps at most 20 timestamps per active IP in memory, expires old timestamps and removes idle IPs on the next exchange. Restarting the application resets the counts. Both adapters supply the client address through `getRemoteAddr()`, using the existing proxy configuration. Issued passes remain usable when an IP exhausts its allowance.
- Centralized staff accounts and provider-access management are deferred to [issue #63](https://github.com/AlexanderReaper7/teaterihuskvarna/issues/63), which the user requested at COULD priority on 2026-09-30. Preview keeps the per-IP rate limit while that work remains deferred.
- Content pages send `Content-Security-Policy: frame-ancestors 'self' <SANITY_STUDIO_URL>`, every other page `X-Frame-Options: DENY`.
- Click-to-edit, added on 2026-11-04 for #198. [`layout.jte`](../../src/main/jte/layout.jte) loads `/js/visual-editing.js` on a preview page only. The [`Dockerfile`](../../Dockerfile) builds it with esbuild from [`visual-editing/`](../../visual-editing/), whose [`main.js`](../../visual-editing/main.js) connects the page to Presentation. Presentation's address bar follows the frame, and its own navigation moves the frame only within the site's origin. A saved draft reloads the page 1 s after the last save, because the server renders every page and the script has no other way to show a change.
- [`EditAttributes`](../../src/main/java/se/teaterihuskvarna/content/EditAttributes.java) writes each `data-sanity` value in the format of `createDataAttribute` from `@sanity/visual-editing-csm`, so the overlays need no stega encoding. A template marks each field, [`PortableText`](../../src/main/java/se/teaterihuskvarna/content/PortableText.java) each block by its `_key`, and a gallery each image by its `_key`. [`PreviewEditingIT`](../../src/test/java/se/teaterihuskvarna/content/PreviewEditingIT.java) fails when a template names a field that no schema in [`studio/schemaTypes/`](../../studio/schemaTypes/) declares. [`fixture.json`](../../src/main/resources/content/fixture.json) has no pictures, so that the browser tests fetch nothing from Sanity's CDN, and the test gives each document of the preview its picture field and each event a gallery. It then fails when a picture from Sanity has no attribute.
- The Studio address in the attribute loses any trailing slash. The overlays build the edit link with `createEditUrl` from `@sanity/client`, which throws on one, and that error unmounted the overlays and dropped Presentation's connection on the first hover.
- A click on a marked element opens the field in the editor instead of following a link, and a click with Ctrl or Shift goes to the page. Holding Alt hides the overlays. Every other link, the banner's "Avsluta förhandsgranskningen" included, works as on the site. Paragraphs and gallery images get a drag handle, which the package offers for items of one array.
- An address typed in Presentation moves the frame through the history adapter in [`main.js`](../../visual-editing/main.js), which follows only an address on the site itself, and the whole address rather than its path. A path such as `//other.example/` names another host to `location.assign()`, and the first version followed it there. Codex's review of #203 found it on 2026-11-04.
- Presentation sends its refresh only while a draft or a published version exists after a change. Discarding a draft therefore leaves the old page in the frame until the editor presses "Uppdatera förhandsgranskning" (refresh preview).
- The overlays start following the pointer over an element at the first mouse move after it scrolls into view. A wheel scroll that brings a marked link under a pointer that stays still, followed by a click without a move, therefore follows the link. For the ticket button that takes the frame off the site.
- In a Studio narrower than 900 px, Sanity UI's third breakpoint, Presentation shows the preview and the editor one at a time, under the tabs "Presentation" and "Struktur" (structure). A click on the page selects the document and field but stays on the preview, and "Struktur" then opens the document at its top rather than at the field.
- The script is 872 KB, 288 KB with gzip, and only a preview page loads it. [`licenses.mjs`](../../visual-editing/licenses.mjs) writes the licences of the 43 packages in it to `/js/visual-editing.licenses.txt`, since the minified file keeps only comments marked `@license`.
- Development reads the same Sanity `dev` dataset as Studio. Integration and browser tests explicitly read [`fixture.json`](../../src/main/resources/content/fixture.json), with dates relative to today, so tests remain independent of Sanity and get the same content every run.
- On 2026-09-30, the agent imported 16 published documents into project `gk5ur3tb`, dataset `dev`: 2 series, 4 events, 3 news items, 5 pages and 2 partners. The import preserved fixture IDs and content, converted series into references and resolved relative dates against 2026-09-30 in Europe/Stockholm. These dates stay fixed in Sanity; editors change them in Studio. The import created documents without overwriting existing ones, and a query verified every imported field against the generated documents.
- The start page is `/` in every profile. Developer tools use `/dev` under the dev profile, moved at the user's request on 2026-09-30 so development presents the public site at its normal address.

### Studio hosting

[`compose.dev.yaml`](../../compose.dev.yaml) starts the development Studio using [`studio/Dockerfile`](../../studio/Dockerfile). Its default address is [localhost:3333](http://localhost:3333/), its dataset is `dev`, and its preview points to the application on `PROXY_HTTP_PORT`. The application uses this Studio origin for administrator links and frame permissions unless `SANITY_STUDIO_URL` overrides it. The end-to-end stack disables Studio and keeps fixture content.

[`compose.share.yaml`](../../compose.share.yaml) serves Studio at `/studio/` on `SHARE_URL`, with the same password as the site and Mailpit. It configures the editor's base path, preview origin and administration link. Frame permissions use only the origin of that link. On 2026-09-30, the agent added the shared origin to project `gk5ur3tb`'s CORS settings with credentials, and allowed that hostname in Studio's development server.

Editors use the Studio deployed to Sanity hosting, with the `production` dataset and the public site's preview origin. [`studio/README.md`](../../studio/README.md) describes deployment and the application settings. Both Studios use Sanity's hosted content database. Hosting the development editor locally does not make content editing work offline.

### Not done

The old WordPress site's content is imported into the `dev` dataset by [`studio/import/wordpress.ts`](../../studio/import/wordpress.ts). The `production` dataset does not hold it yet, which is part of #196.

## Verification

Written by an agent on 2026-11-04, for #195 and #59. The runs used the shared development stack on strix, application build 5932464, Sanity project `gk5ur3tb`, dataset `dev`. Sanity reached the stack through a Tailscale Funnel and the share password, with a webhook made in sanity.io/manage as [`studio/README.md`](../../studio/README.md) describes. [`scripts/measure-publish.py`](../../scripts/measure-publish.py) repeats the timing: it publishes a test news item dated 2000-01-01, polls the item's page every 0.1 s, and deletes the item at the end.

### R008, publish to visible

Without the webhook, a publish shows when the copy expires and the next request fetches again. The slowest case is therefore 45 s, plus one query to Sanity, plus the time Sanity takes to accept the publish. A query from strix to `api.sanity.io` took 0.19 to 0.22 s.

| Case | Runs | From Sanity accepting the publish to the changed page |
|---|---|---|
| No webhook, copy refreshed 1 to 2 s before the publish | 6 | 43.6 to 44.5 s |
| No webhook, copy about 14 s old | 1 | 31.1 s |
| Webhook, copy refreshed 1.1 to 1.6 s before the publish | 6 | 0.7 to 1.4 s |
| Webhook, published in Studio's editor | 1 | 3.6 s |

Sanity took 0.6 to 1.2 s to accept each publish, so counted from the publish itself the slowest run took 45.3 s, inside R008's minute. Every webhook delivery in these runs returned 204. Deliveries after several quiet minutes took 1.2 to 2.6 s instead of about 0.5 s, which is most of the Studio run's 3.6 s.

A fetch already running when the webhook arrives stores what it read, which can predate the publish, for another 45 s. The webhook usually clears the copy at once, but the guarantee is the `max-age` bound.

These numbers replace the runs of 2026-10-12, which were made on localhost while the project had no webhook, and so measured the expiry.

### R009, preview

Done in Firefox, in the Studio at `/studio/`, signed in as a project administrator:

- After the test item's ingress was changed in the editor, the Presentation tool showed the draft ingress under the preview banner. The same page outside the preview, read from the application and through the Funnel, showed the published ingress.
- The banner's link "Avsluta förhandsgranskningen" (end preview) deleted the cookie. The same page in the same frame then showed the published ingress, without the banner.
- Ending the preview deletes the cookie and does not revoke the pass. A copied cookie value keeps working until its hour runs out.

### R009, click-to-edit

Written by an agent on 2026-11-04, for #198. Done in Firefox on the same stack with the application built from branch `198-visual-editing`, in the Studio at `/studio/`, on `/nyheter` and `/nyheter/varens-kaffe-med-drommar`:

- Presentation connected, and its list "Dokument på den här sidan" (documents on this page) showed the news items on the page.
- Hovering a news title drew an outline and the document's title over it. A click opened "Vårens Kaffe med drömmar" in the editor with the Rubrik (title) field focused, and the frame stayed on its page.
- A click on the second paragraph of the body opened Brödtext (body text) at block `wp-529-2`.
- After " TEST" was typed at the end of the title, the frame showed the new title 3.7 s after the typing started, through a reload. Discarding the draft left the frame unchanged until "Uppdatera förhandsgranskning" was pressed. A query to the dataset afterwards found only the published document, last changed on 2026-11-02.
- Clicks on the banner's end link and on a menu link reached the page uncancelled. A click on a news title was cancelled and opened the editor.
- In Presentation's phone-width frame, 375 px, and in the same frame narrowed to 320 px, the layout check from [`layout.spec.ts`](../../e2e/tests/layout.spec.ts) found nothing on 15 pages: the nine top-level pages, three news items and three events. A click on a title, a paragraph, an event's place and its ticket button opened that field. The menu opened and its link moved the frame, and the end link was not cancelled.
- With the Studio itself in a 390 by 844 px frame, so that its layout saw a phone's viewport, the same checks found nothing on `/nyheter` and one news item. A click on a title and on a paragraph selected the field, and "Struktur" opened the document at its top.
- The preview banner showed its white text on the message boxes' pale panel colour, 1.08:1, with a teal edge and a gap below. Its `role="status"` matched the message box rule, which comes later in [`site.css`](../../src/main/resources/static/css/site.css) with the same specificity. The banner's rule now names the role too. The runs above with the user's profile missed it, because Dark Reader repaints the page.
- The automated window, behind other windows, painted 2 frames a second. That delayed the overlays' IntersectionObserver, so a click right after a scroll went to the page. The phone-width runs were made headless, at 61 frames a second.
- The first build wrote the Studio address with the trailing slash that [`compose.share.yaml`](../../compose.share.yaml) gives `SANITY_STUDIO_URL`. The first hover threw "baseUrl must not end with a slash", the overlays unmounted, and Presentation showed its connection error. After the fix above, the overlays and the connection held through every hover and click in the run.

Not tested: dragging a paragraph or a gallery image, holding Alt, the Studio on Sanity hosting, a touch screen, and a live event with a main image or gallery, which the dev dataset has none of. `PreviewEditingIT` checks their attributes.
