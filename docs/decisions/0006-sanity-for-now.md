# 0006: Sanity for now, self-hosted Directus as the noted alternative

2026-09-22

## Decision

Sanity stays as the CMS for version 1, as the produktägare's document assumed. Self-hosted Directus is recorded here as the alternative, with the conditions that would make it the better answer, so that switching later is a decision rather than a rediscovery.

## The comparison this came out of

The vendor was inherited rather than chosen, so it was re-examined against one criterion, that the site be easy for the association to edit, and then against a second the board added, minimise cost.

| | Sanity nonprofit, with Free as fallback | Storyblok free | Directus self-hosted |
| --- | --- | --- | --- |
| Cash | 0 within quota if the application is accepted; 0 on Free | 0 | 0 |
| Editor seats | 25 on nonprofit; 20 on Free | 1, second at 15 USD/mo, max 2 | Unlimited under the Open Innovation Grant |
| Editor roles | Growth roles on nonprofit; Administrator or Viewer on Free | Roles included | Custom access policies under the grant |
| Content access | Private datasets on nonprofit; published documents public on Free; drafts require authentication on both | Private | Private |
| Servers to patch | None | None | One container |
| Rich text in Java | Portable Text, no renderer exists | Richtext JSON, no renderer exists | WYSIWYG stores HTML, nothing to write |
| Visual editing in Java | Needs stega and content source maps, no library exists | HTML attributes, workable | HTML attributes, workable |

Directus was the recommendation in the original comparison. Its [Open Innovation Grant](https://directus.com/docs/licensing/open-innovation-grant) became perpetual on 2026-09-10, with no renewal and no expiry, and the association is under the 5M USD revenue and 50 employee thresholds by several orders of magnitude. It costs nothing, gives unlimited named editors, and its WYSIWYG stores HTML, which removes the only piece of custom rendering code this project would otherwise have to write.

The board chose Sanity anyway, for now. That is recorded as chosen rather than defaulted to.

## Apply for the nonprofit plan before configuring production

[Sanity's nonprofit plan](https://www.sanity.io/docs/platform-management/non-profit-plan) mirrors Growth within quota at no charge for an eligible organisation and includes 25 users. That removes the two largest Free-plan costs in the original comparison: it allows private datasets and provides Editor, Developer and Contributor roles. The association should apply after creating the Sanity project because the application requires a project ID.

Until Sanity approves that application, Free remains the fallback. Free has 20 seats but only Administrator and Viewer roles. Anyone who edits can therefore delete the dataset or change its configuration. Backups limit damage but do not replace permissions.

Free datasets expose published documents to unauthenticated queries. Drafts do not leak: Sanity's [authentication documentation](https://www.sanity.io/docs/content-lake/http-auth) and [draft documentation](https://www.sanity.io/docs/content-lake/drafts) state that drafts require authentication. The previous version of this record got that fact wrong. Member-only offer details must stay outside published Sanity documents while the project uses a public dataset.

The Free plan limit of 10,000 documents is far above the expected content volume.

The real cost is Java work. No renderer exists on Maven Central for Portable Text, and no library exists for stega encoding or content source maps, which Sanity's Presentation tool needs for visual editing. So version 1 gets neither: rich text is a Markdown field rendered by commonmark-java, and editors get the Studio rather than click-the-page editing. That is the compromise already assumed in [projektplan.md](../projektplan.md), and choosing Sanity keeps it.

## What would make Directus the answer

Any one of these, and this decision should be reopened:

- Sanity rejects the nonprofit application and the board will not accept Administrator rights for every editor.
- Published content must be available to members but hidden from the public, and the nonprofit plan is unavailable.
- Editors ask for click-the-page editing, or find the Markdown field hard to use. Directus's WYSIWYG answers both and Sanity's free plan answers neither without Java work that has no library behind it.
- Sanity changes its free tier. This is already on the risk list.

The switch is bounded. The application reads the CMS over HTTP and keeps the member register out of it, so migrating is a content export plus a change to one service and the rich text rendering, not a rewrite. Directus also needs somewhere to run, which the container hosting in [0007](0007-postgres-in-a-container.md) already provides.

## What it costs to undo

One content export, one API client, and deleting the commonmark-java rendering path rather than writing a Portable Text renderer. Cheaper going to Directus than coming back from it.

## Amendment, 2026-09-22

This record originally leaned on the claim, inherited from the produktägare's document, that Sanity receives no personal data at all. That is wrong: requirement P4 asks for pages about the board and about productions, so names and photographs of identifiable people are published to Sanity by design. The claim has been narrowed here and in [projektplan.md](../projektplan.md) to the member register, which is the part that actually holds. The decision itself does not change, but the public-dataset argument above is slightly weaker than it first read, and Sanity now needs a data processing agreement it was previously exempted from.

The same review found two newer facts. Sanity now documents a nonprofit plan that mirrors Growth within quota at no charge, and its public-dataset documentation says unauthenticated requests cannot read drafts. Both change the cost comparison but not the CMS choice. The project now applies for the nonprofit plan and uses Free as the fallback.
