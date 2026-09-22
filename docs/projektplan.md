# Teater i Huskvarna, system specification

2026-09-22

What the new teaterihuskvarna.se has to be, and how anyone can tell whether it is. The produktägare's own document is [projektplan-original.md](projektplan-original.md), translated in [projektplan-original.en.md](projektplan-original.en.md); both are frozen and this file does not replace them. Where the two disagree, the difference is stated here rather than quietly resolved.

This is a specification, not a work plan. It says what has to exist and what counts as finished. It does not divide anything into tasks or assign anyone to them. The requirement list lives in [requirements.md](requirements.md), unanswered board questions in [open-questions.md](open-questions.md), and the reasoning behind individual technical choices in [decisions/](decisions/).

## Purpose

The association's site runs on WordPress and its newest article is from spring 2025. That is the visible symptom. The real problem is that member administration runs through board members' private Gmail and Hotmail accounts: annual meeting sign-ups, discounted ticket requests, and revue volunteers who tick off performances and mail a private individual. Membership fees, 50 kr single and 100 kr family, arrive by bankgiro with a name in the message field and get reconciled by hand.

So the system replaces a filing method, not a website. Three things have to become true:

- Publishing an event stops requiring a developer.
- Reaching every member stops requiring someone's personal address book.
- Knowing who is a member stops living in one person's inbox.

The last one is the reason this is worth building. A board member who resigns currently takes part of the membership register with them.

## Desired results

These are the outcomes the finished system is judged against. Each one is checkable by watching someone do it, not by reading code.

| Result | How it is checked |
| --- | --- |
| An editor with no technical background publishes an event in under 10 minutes | Timed, with a real board member who has not seen the system before, on their own laptop |
| A mailing to all paying members goes out in under 5 minutes | Timed, from admin login to send confirmation |
| No private mail address or phone number remains on the public site | `grep` the rendered site for the board's personal addresses, expect zero hits |
| The membership register survives any one person leaving | Two named board members hold admin on every account, checked by logging in as the second one |
| A new developer goes from clone to running site in under an hour | Timed on a machine that has never built the project |
| Published content appears on the site within a minute | Publish in Sanity, reload, measure |
| The member register survives the server being lost | Restore a `pg_dump` into an empty database and compare row counts against the original |

The first and third are the ones that fail quietly. An editor who finds publishing awkward goes back to mailing the webmaster, and the site is stale again within a year.

## Scope

Version 1 is in production at the end of week 12, with the public site, editing, member login and mailings working. Everything else is later.

| In version 1 | Not in version 1 |
| --- | --- |
| Public site with news, events, association info, partners, Ludde awards | Payment by Swish or card on the site |
| All public content editable in Sanity | A mobile app |
| Passwordless member login by mail link | Ticket sales, which stay with Nortic and Tickster |
| Member pages with offers and sign-up | Member chat or forum |
| Volunteer booking for cloakroom and serving | Automatic reconciliation against the bank |
| Admin view for the member register and manual fee marking | SMS |
| Mail to selected groups of members | More than one language |
| Migration of the content worth keeping from the current site | |

Payment deserves a note, because it looks like an omission. Fees stay on bankgiro and an administrator ticks them off by hand (requirement A2). Adding Swish means handling money, which means a payment provider agreement, reconciliation rules, and refunds. For an association collecting 50 kr from a few hundred people, the manual tick is not a compromise, it is the correct amount of machinery.

## Architecture

Two systems. Sanity holds public content. The Spring Boot application holds everything about people. **No member register data is ever written to Sanity**, which is what keeps the register confined to one database and one mail provider.

That rule is narrower than the original document's, which says Sanity receives no personal data at all. That version breaks in the first week of content entry, because requirement P4 asks for a page about the board and a page about productions, and both mean names and photographs of identifiable people. Published personal data goes into Sanity by design. The register does not. Keeping the two claims apart is what stops the second one from being quietly abandoned along with the first.

Sanity was inherited from the original document rather than chosen, so it was re-examined against both the editing requirement and cost. It stays, with self-hosted Directus recorded as the alternative and the conditions that would make it the better answer. See [0006](decisions/0006-sanity-for-now.md). Nothing else in this specification depends on the vendor, because the application reads the CMS over HTTP and keeps the register out of it either way.

```mermaid
flowchart LR
    editor[Editor]
    visitor["Visitors and members<br/>(browser)"]
    admin[Administrator]
    github["GitHub<br/>code, CI/CD, Dependabot<br/>container registry"]
    sanity["Sanity<br/>news, events, pages"]
    brevo["Brevo<br/>mail to members"]

    subgraph host["One host, EU region, all containers"]
        proxy["Reverse proxy<br/>TLS"]
        app["Spring Boot app<br/>JTE + Spring Security"]
        db[("PostgreSQL<br/>members, sign-ups")]
    end

    editor -- "Sanity Studio" --> sanity
    visitor --> proxy
    admin -- "admin view" --> proxy
    proxy --> app
    github -- "pushes image,<br/>then deploys over SSH" --> host
    app <-- "fetches content via API;<br/>webhook on publish" --> sanity
    app <--> db
    app -- "login links, mailings" --> brevo

    classDef internal fill:#dae8fc,stroke:#6c8ebf
    classDef external fill:#ffe6cc,stroke:#d79b00
    classDef tooling fill:#e8e8e8,stroke:#999999
    class editor,visitor,admin,proxy,app,db internal
    class sanity,brevo external
    class github tooling
```

The application renders every page server-side. There is no separate JavaScript front end, and no page requires JavaScript to read or to submit a form. That is a hard constraint, not a preference: it is what makes WCAG 2.1 AA achievable by a small team, and it removes an entire second build from the maintenance bill.

The site reads Sanity through its API and caches the result. A webhook on publish clears the cache, which is how R2 gets met. If the webhook fails, the cache expires on its own within a minute, so a broken webhook degrades to a one-minute delay rather than a stale site.

### Components

| Component | Responsibility | Built or configured |
| --- | --- | --- |
| Sanity Studio | Editing public content | Content models configured in TypeScript |
| Spring Boot app | Renders the site, member area, admin view | Built |
| JTE templates | HTML for every page | Built. See [decisions/0004](decisions/0004-jte-for-templates.md) |
| Spring Security one-time token | Passwordless login | Configured; the mail sending around it is built |
| PostgreSQL | Members, sign-ups, volunteer shifts | Schema built, migrations under Flyway |
| Brevo | Sends login links over SMTP, holds mailings as drafts until an administrator sends them | Integration built |
| GitHub Actions | Tests, build, deployment | Configured |
| Reverse proxy | Terminates TLS, renews certificates automatically | Configured. See [decisions/0008](decisions/0008-everything-in-containers.md) |
| Container host, EU region | Runs the proxy, the application and PostgreSQL | Configured. See [decisions/0007](decisions/0007-postgres-in-a-container.md) and [0008](decisions/0008-everything-in-containers.md) |
| Dependabot | Dependency update proposals | Configured |

### Member login

```mermaid
sequenceDiagram
    participant member as Member
    participant app as Spring Boot app
    participant brevo as Brevo

    member->>app: Submits mail address
    app->>app: Checks membership, creates one-time token
    app->>brevo: Sends mail with login link
    brevo->>member: Mail with link
    member->>app: Follows the link
    app->>app: Validates and consumes the token
    app->>member: Signed in to the member pages
```

Three properties this flow has to keep, all of them testable:

- A token works once and then never again.
- A token expires shortly after it is issued. Ten minutes unless the board wants otherwise.
- An unknown address gets exactly the same response as a known one, with the same timing. Otherwise the login form becomes a way to ask whether a given person is a member.

The third is the one that gets broken by accident, usually by an error message added later for helpfulness.

### Mailings

```mermaid
flowchart TD
    publish[Editor publishes<br/>an event in Sanity]
    choose[Admin selects<br/>Send as mailing]
    audience["Selects audience:<br/>all, paying, volunteers"]
    draft[App builds the HTML and<br/>creates a draft campaign in Brevo]
    review[Admin reviews in Brevo:<br/>preview, test send, edit]
    send[Admin sends<br/>from Brevo]
    log[Brevo records delivery,<br/>opens and unsubscribes]

    publish --> choose --> audience --> draft --> review --> send --> log
```

The application builds the mailing and stops. It does not send it. `createEmailCampaign` leaves the campaign in draft status, and an administrator opens Brevo to check it and press send. There is no mailing editor in the admin view, and no send button.

That is a deliberate handover, reasoned through in [0005](decisions/0005-brevo-campaign-drafts.md). It means U2's preview and test send, U3's unsubscribe link and U4's log are Brevo's rather than ours, which is the point: an unsubscribe that Brevo records is honoured by every later campaign without anyone here having written that code correctly.

The text still comes from the Sanity document, so the wording is written once (U1). Audience selection stays in the application, because it is the only component that knows who has paid.

## Data

The member register lives only in PostgreSQL. The schema sketch below is the specification for what gets stored; anything not on it needs a reason before being added.

```mermaid
erDiagram
    HOUSEHOLD |o--o{ MEMBER : contains
    MEMBER ||--o{ MEMBERSHIP_FEE : holds
    MEMBER ||--o{ OFFER_SIGNUP : makes
    MEMBER ||--o{ VOLUNTEER_BOOKING : takes
    MEMBER ||--o{ MAILING_RECIPIENT : receives
    VOLUNTEER_SHIFT ||--o{ VOLUNTEER_BOOKING : has
    MAILING ||--o{ MAILING_RECIPIENT : went_to

    MEMBER {
        uuid id
        text name
        citext email
        text phone
        text address
        uuid household_id "null for an individual member"
        text role
        timestamptz created_at
    }
    HOUSEHOLD {
        uuid id
        text name
    }
    MEMBERSHIP_FEE {
        uuid id
        uuid member_id "the account the fee is bound to"
        text kind "individual or household"
        int year
        int amount_ore
        text status
        date marked_paid_on
        text marked_paid_by
    }
    VOLUNTEER_SHIFT {
        uuid id
        text performance
        text task
        timestamptz starts_at
        int seats
    }
```

Offers, sign-ups and mailings are sketched by their relationships rather than their columns, because their fields follow from the requirements (M3, A3, U1, U4) and do not carry decisions worth arguing about here.

Rules about this data:

- **No personnummer.** Name, mail, phone, address and household are enough. This is enforced by a test that fails if a column named `personnummer`, `national_id` or `ssn` appears in the schema, not by a sentence in a document.
- **A fee is always bound to one member account, and a `kind` field says how far it reaches.** `member_id` is not null. `kind` is `individual`, covering only that member, or `household`, covering everyone in that member's household. `CHECK (kind IN ('individual', 'household'))`, and `UNIQUE (member_id, year)`. A plain text column with a check rather than a Postgres enum, because adding a value to an enum is a migration and adding one to a check is a one-line one.
- **This is the model because of how the money arrives.** Fees come in by bankgiro with a name in the message field and get reconciled by hand. A name is one person, so the row an administrator ticks off is the row bound to that person. Pointing the fee at a household instead would make every reconciliation start with a lookup, which is work added to the one task in the system that happens a few hundred times a year.
- **Whether a member has paid is a two-branch question.** A member is paid up for a year if they hold a paid fee for it, or if someone in their household holds a paid `household` fee for it. Belonging to a household is therefore exactly what family coverage means, and `member.household_id` is nullable, because an individual member belongs to no household. This is the rule to write a test against, since it is the one an administrator will phone about.
- Money is stored in öre as an integer. Fees are decided by the annual meeting and will not stay 50 and 100 kr forever.
- `marked_paid_by` records which administrator ticked a fee off. Manual reconciliation without an audit trail is how disputes become unresolvable.
- No member register field is ever added to a Sanity document type. Not a name on a volunteer shift, not an e-mail on a sign-up. This is checkable rather than aspirational: the content model is five TypeScript files, so the property is read off the schema, and it is the schema a reviewer should look at when a new field is proposed.

## Accessibility and the public site

WCAG 2.1 AA (P6) is a requirement, and it is the one most likely to be declared done without being done. So it gets a mechanical check in the build: `@axe-core/cli` 4.13.0 or `pa11y-ci` 4.1.1 run against the rendered pages in CI, failing the build on violations.

An automated checker catches perhaps a third of WCAG failures. It cannot tell whether alt text is accurate, whether the focus order makes sense, or whether a colour combination is readable to the person actually reading it. So the automated check is a floor, and the rest is a manual pass: keyboard-only navigation of every page, and one run through with a screen reader. That manual pass has no mechanical substitute and the plan does not pretend otherwise.

The site works on a phone. Given the association's audience this is not a secondary consideration, and the layout is designed narrow-first rather than shrunk down afterwards.

## Language

Swedish is what a visitor or member reads. English is everything else, including this document, the code, and the commit messages. The full rule and its two exceptions are in [decisions/0001](decisions/0001-language-policy.md).

Swedish visitor-facing text lives in `messages_sv.properties` and in Sanity, not inside templates. A template holds markup and references a message key. This keeps the copy in one place a non-developer can be pointed at, and it means correcting a typo in a validation message does not require touching a template file.

## Security

- HTTPS everywhere, with HTTP redirected.
- One-time login tokens, short-lived, stored in the database rather than in memory. In-memory tokens break the moment there is more than one instance, and they vanish on every deploy.
- Rate limiting on the login form and on the mail-sending endpoints.
- Two roles, member and administrator (I2). The member register is visible only to administrators.
- The default for a new route under `/medlem/**` or `/admin/**` is denied. Access is granted per route, never assumed.
- Daily database backups, with a restore that has actually been performed once. An untested backup is a guess.

## Personal data

The association becomes data controller for the member register and has to be able to show how the data is protected.

- Data processing agreements with Brevo, the hosting provider and **Sanity**. The original document exempts Sanity on the grounds that it receives no personal data, which is not true once the board and the productions are published. Sanity publishes a DPA with Standard Contractual Clauses and a subprocessor list at [sanity.io/legal/dpa](https://www.sanity.io/legal/dpa).
- Database and hosting in an EU region. Sanity satisfies this without being asked: every dataset is stored in Belgium, GCP `europe-west1`, on all plans, with the CDN in front used for delivery only. Region selection is not offered, so this is the vendor's default rather than a setting to get right.
- Two kinds of personal data, kept apart. The **member register** lives only in PostgreSQL and never reaches Sanity. **Published personal data**, meaning names and photographs of board members, performers and audiences, lives in Sanity because publishing it is the point. The second kind needs consent and a way to honour a removal request, which is a process the board runs rather than code the system enforces.
- Development runs against invented test data. Production data never reaches a development machine.
- Members who have not renewed are anonymised or deleted after a period the board sets. That period is an open question and the retention job cannot be written until it is answered.
- Member mailings go out on the basis of membership. Every mailing carries an unsubscribe link.
- A short record of processing activities is written as part of the documentation.

## Operations

GitHub holds the code, runs the tests on every push and pull request, and deploys. Dependabot proposes dependency updates weekly. Together those mean routine maintenance is approving a green pull request rather than remembering to check for updates.

Hosting is one host in an EU region, running the reverse proxy, the application and PostgreSQL as containers from the same pinned images used locally. PostgreSQL is not a managed service, which is the project's largest cost decision, reasoned through in [0007](decisions/0007-postgres-in-a-container.md). Everything else being containerised too is [0008](decisions/0008-everything-in-containers.md).

What runs in production is the image CI tested. Images are tagged with the commit sha rather than only `latest`, so rolling back is pulling the previous tag and "which version is running" has an answer. Actions pushes to GHCR on a green build of `main` and then deploys over SSH, so a deployment is a deliberate act with a timestamp rather than an agent acting unattended.

The original plan assumes Azure on the strength of Microsoft's nonprofit credit of 2 000 USD a year. That credit is not yet applied for, requires the association to validate as a nonprofit, and does not roll over, so it is treated as headroom rather than as the budget. Nothing in this specification depends on which provider wins.

Running the database rather than renting it moves backups from the provider to us, and the failure mode is losing the member register with no way back. So a scheduled `pg_dump` to encrypted storage on another machine in an EU region is part of the system, and **a restore has to have been run from one of those dumps into an empty database before launch**. A backup nobody has restored is a file, not a backup.

Java 25 LTS and Spring Boot 4.1.1. The project originally tracked the newest JDK, which was Java 26. That policy was abandoned when JDK 27 went generally available on 2026-09-15 and ended updates for JDK 26 the same day, before any application code existed. A six month update cycle is the wrong commitment for a system whose maintenance budget is a few hours a year and whose maintainer after week 12 is still an open question. Temurin supports Java 25 until at least 2031-09-30. See [0009](decisions/0009-java-25-lts.md).

## Quality bar

The association gets one build. After week 12 the budget is a few hours a year, so anything that needs regular attention will not get it. That pushes every choice towards fewer moving parts and towards checks that run without being remembered.

A change is finished when all of these hold:

- Tests exist for it and pass in CI.
- The tests can fail for the reason they claim to. A check nobody has watched fail is not evidence. Where a property cannot be checked mechanically, the specification says so instead of implying coverage.
- It works in the deployed test environment, including on a phone.
- The documentation that describes it is updated in the same change.
- No new personal data field appeared without being on the list above.

The documents have their own check, since prose rots without a compiler to notice. `docs/check.py` verifies that [requirements.md](requirements.md) still quotes the produktägare's Swedish word for word, that every relative link between documents resolves, and that every mermaid diagram renders. GitHub Actions runs it on push and pull request with `--require-mermaid`, which turns a missing renderer into a failure rather than a skip. All four of those failure modes have been triggered by hand and watched to exit 1.

Four documents ship with the system: how to deploy, update and restore it; how to publish content, as short screen recordings; what the architecture actually became, as opposed to what this file predicted; and a README that gets a new developer running in under an hour.

## What has to be true at week 12

Twelve weeks, ending with version 1 in production. No milestone breakdown here, because a specification that also schedules itself goes stale twice as fast. The end state:

- Every Must requirement in [requirements.md](requirements.md) is done, 20 of them.
- The site is live on the association's domain, or the cutover from WordPress is a decision the board has consciously deferred.
- Real editors, not the developers, have published real content.
- Real administrators have marked a fee paid, exported the register, and sent a mailing.
- All accounts are owned by the association, or every exception is written down in [decisions/](decisions/) with what it costs to undo.
- The accessibility check runs in CI and the manual keyboard and screen-reader pass is done.
- A backup has been restored successfully at least once.

If something has to be cut, Should and Could requirements go first, in that order. That rule comes from the produktägare's own document and is the reason the priority column exists.

## Risks

The largest risk is that nobody owns the system after week 12. Everything else is smaller than that.

| Risk | Likelihood | Impact | Response |
| --- | --- | --- | --- |
| No maintainer after handover | Medium | High | Named maintainer before launch; the automation above keeps routine updates to approving a pull request |
| Scope overruns and version 1 misses week 12 | High | High | Strict priority; Should and Could cut first |
| Accessibility declared done on the automated check alone | Medium | High | The manual pass is a separate named item in the week 12 list |
| Sanity changes its free tier | Low | Medium | Content exports; self-hosted Directus is the costed alternative in [0006](decisions/0006-sanity-for-now.md) |
| More than 300 members hits Brevo's daily limit | Unknown | Low | Member count is an open question; split the send or upgrade |
| Azure credit is not granted | Unknown | Low | Apply early; hosting is costed without it, per [0007](decisions/0007-postgres-in-a-container.md) |
| Personal data leaks from a development environment | Low | High | Test data only; production access restricted |
| A photograph of an identifiable person is published without consent | Medium | Medium | Consent practice is a board question in [open-questions.md](open-questions.md), to be settled before the first upload |
| Backups are never tested and the register is lost | Medium | High | A tested restore is a desired result, not a runbook line |
| The site launches without HTTPS because nobody owned TLS | Low | High | The proxy is a named component with automatic certificate renewal, per [0008](decisions/0008-everything-in-containers.md) |
| A board member deletes content in Sanity | Low | Medium | The free plan has no Editor role, so everyone is an Administrator; scheduled content exports |
| Editors find it awkward and stop using it | Medium | High | Timed test with real editors is a desired result, not an afterthought |

The produktägare's version of this table lists two risks this one drops, both about a team that no longer applies: mentoring that does not materialise, and four junior developers taking on too much.

## Decisions this specification assumes

Recorded, with reasoning, in [decisions/](decisions/):

- [0001](decisions/0001-language-policy.md), Swedish for readers, English for everything else.
- [0002](decisions/0002-accounts-under-a-personal-login.md), why the repository sits on a personal account and what a transfer costs.
- [0003](decisions/0003-no-branch-protection-yet.md), why main carries no protection rules yet.
- [0004](decisions/0004-jte-for-templates.md), JTE rather than Thymeleaf.
- [0005](decisions/0005-brevo-campaign-drafts.md), the application drafts mailings and Brevo sends them. Login links stay transactional over SMTP.
- [0006](decisions/0006-sanity-for-now.md), Sanity stays as the CMS, with self-hosted Directus recorded as the alternative.
- [0007](decisions/0007-postgres-in-a-container.md), PostgreSQL runs in a container rather than as a managed service, and what that obliges.
- [0008](decisions/0008-everything-in-containers.md), the whole application is containerised, which makes TLS termination, the registry and rollback explicit components.
- [0009](decisions/0009-java-25-lts.md), Java 25 LTS rather than the newest JDK, and why tracking the newest one was abandoned.

Rich text from Sanity is a Markdown field rendered by commonmark-java rather than Portable Text, and editors get the Studio rather than click-the-page editing. Both follow from there being no Java library for Portable Text, stega encoding or content source maps, and both are part of [0006](decisions/0006-sanity-for-now.md).

Assumed here but not yet recorded:

- One repository holding both the application and the studio, so a schema change and the template reading it land in the same commit.
