---
created: 2026-09-28
provenance: user
description: Where member offers and member documents live, and why not in Sanity.
---

# 0022: Offers and member documents live in the application

The Decision section is the user's. Everything under Agent notes is an agent's.

## Decision

- Offers (R014) and member documents (R015) are stored and edited in the application, in PostgreSQL, and not in Sanity. Decided by the user on 2026-09-28.

- R007 lists Erbjudande among the Sanity types. Offers stay in the application all the same, and R007's quote stays as the produktägare wrote it, with the exception noted beside the table in [requirements](../requirements.md). Decided by the user on 2026-09-28, after the GPT-6 Sol review pointed out the conflict.

This answers T6 in [open-questions](../open-questions.md): at [the first meeting](../meetings/2026-09-24-meeting-1.md#what-the-site-does) Klas said offer details are for members only, and a Free Sanity dataset publishes every published document.

## Agent notes

Written by an agent on 2026-09-28. Everything below is a default the user has not reviewed.

- The split [projektplan](../projektplan.md) described, text in Sanity and capacity in PostgreSQL, is gone. An offer is one row in `offer`, and a registration is a row in `offer_registration` that the same transaction checks the capacity against, in [`OfferService`](../../src/main/java/se/teaterihuskvarna/offer/OfferService.java).
- Registration closes at `registration_closes_at`, or at `starts_at` when that is empty, or never when both are empty. That rule is the agent's choice.
- A document is a PDF of at most 10 MB, stored in `member_document` as bytes. The association has a handful of protocols and annual reports a year, so a file store would be one more thing to back up for no gain.
- The document service checks the 10 MB limit to the byte. A request over 11 MB, the multipart limit for the file and the whole request alike, is stopped by its `Content-Length` before anything reads the body, and the page says the file is too large; the API answers 413 ([`OversizeUploadFilter`](../../src/main/java/se/teaterihuskvarna/login/OversizeUploadFilter.java)). Until 2026-09-29 the form put its CSRF token in the action address to get past the same problem, which wrote the token to access logs; the user chose to remove it after the GPT-6 Sol review. Tomcat reads and discards up to 50 MB of an unread body so the answer arrives, `server.tomcat.max-swallow-size` in [`src/main/resources/application.yaml`](../../src/main/resources/application.yaml).
- The description is plain text, since an administrator types it in a textarea. A rich text editor in the application would be a second editor beside the Studio.
