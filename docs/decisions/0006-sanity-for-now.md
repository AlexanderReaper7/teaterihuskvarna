# 0006 — Sanity for now, self-hosted Directus as the noted alternative

2026-09-22

## Decision

Sanity stays as the CMS for version 1, as the produktägare's document assumed. Self-hosted Directus is recorded here as the alternative, with the conditions that would make it the better answer, so that switching later is a decision rather than a rediscovery.

## The comparison this came out of

The vendor was inherited rather than chosen, so it was re-examined against one criterion, that the site be easy for the association to edit, and then against a second the board added, minimise cost.

| | Sanity free | Storyblok free | Directus self-hosted |
| --- | --- | --- | --- |
| Cash | 0 | 0 | 0 |
| Editor seats | 20 | 1, second at 15 USD/mo, max 2 | Unlimited under the Open Innovation Grant |
| Editor roles | Administrator or Viewer only | Roles included | Custom access policies under the grant |
| Drafts | World-readable, free datasets are public only | Private | Private |
| Servers to patch | None | None | One container |
| Rich text in Java | Portable Text, no renderer exists | Richtext JSON, no renderer exists | WYSIWYG stores HTML, nothing to write |
| Visual editing in Java | Needs stega and content source maps, no library exists | HTML attributes, workable | HTML attributes, workable |

Directus was the recommendation on the merits. Its [Open Innovation Grant](https://directus.com/docs/licensing/open-innovation-grant) became perpetual on 2026-09-10, with no renewal and no expiry, and the association is under the 5M USD revenue and 50 employee thresholds by several orders of magnitude. It costs nothing, gives unlimited named editors, and its WYSIWYG stores HTML, which removes the only piece of custom rendering code this project would otherwise have to write.

The board chose Sanity anyway, for now. That is recorded as chosen rather than defaulted to.

## What Sanity costs, stated plainly

The free plan has **20 seats but only two roles, Administrator and Viewer**. Editor, Developer and Contributor start at Growth, 15 USD per seat per month. So every board member who can change content can also delete the dataset and rewrite the schema. The mitigation is backups, not permissions.

Free datasets are **public only**. Private datasets start at Growth. This sounds worse than it is here: every document type in the model, events, news, pages, offers and partners, is meant to be read by the public. What actually leaks is drafts, so an unpublished announcement is readable by anyone who guesses the URL. For a theatre association that is a low-harm exposure, and it is bounded by the hard rule that no member register data is ever written to the CMS. Published personal data does live there, meaning names and photographs of the board and of performers, so a draft that is world-readable early is a photograph reaching the internet before the board meant it to. Still low harm, because the photograph was going to be published, but it is not nothing.

Limits are 10,000 documents and 250,000 API requests a month, both far above what this site will use.

The real cost is Java work. No renderer exists on Maven Central for Portable Text, and no library exists for stega encoding or content source maps, which Sanity's Presentation tool needs for visual editing. So version 1 gets neither: rich text is a Markdown field rendered by commonmark-java, and editors get the Studio rather than click-the-page editing. That is the compromise already assumed in [projektplan.md](../projektplan.md), and choosing Sanity keeps it.

## What would make Directus the answer

Any one of these, and this decision should be reopened:

- The board wants more than one or two people editing and wants to know who changed what. Sanity's free plan cannot express that, because everyone is an Administrator.
- Draft content appears that should not be public before it is published.
- Editors ask for click-the-page editing, or find the Markdown field hard to use. Directus's WYSIWYG answers both and Sanity's free plan answers neither without Java work that has no library behind it.
- Sanity changes its free tier. This is already on the risk list.

The switch is bounded. The application reads the CMS over HTTP and keeps the member register out of it, so migrating is a content export plus a change to one service and the rich text rendering, not a rewrite. Directus also needs somewhere to run, which the container hosting in [0007](0007-postgres-in-a-container.md) already provides.

## What it costs to undo

One content export, one API client, and deleting the commonmark-java rendering path rather than writing a Portable Text renderer. Cheaper going to Directus than coming back from it.

## Amendment, 2026-09-22

This record originally leaned on the claim, inherited from the produktägare's document, that Sanity receives no personal data at all. That is wrong: requirement P4 asks for pages about the board and about productions, so names and photographs of identifiable people are published to Sanity by design. The claim has been narrowed here and in [projektplan.md](../projektplan.md) to the member register, which is the part that actually holds. The decision itself does not change, but the public-dataset argument above is slightly weaker than it first read, and Sanity now needs a data processing agreement it was previously exempted from.
