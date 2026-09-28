# Glossary

Provenance: unreviewed. An agent wrote this, and the user has not marked which parts are the user's decisions. Treat every claim as an agent's, see [decisions/0019](docs/decisions/0019-provenance-of-documents.md).

> When a name or meaning changes, this file must also change in the same commit.

Canonical terms are English because code and project documentation are English.

Each heading gives the English term, then the Swedish word the site's copy uses for it, or should use once the site shows it.

## People and roles

### Association | Föreningen

Föreningen Teater i Huskvarna, the organisation that owns the site, member register and production accounts.

### Visitor | Besökare

A person using the public site without member access.

### Member | Medlem

A person recorded in the member register. A member may lack a paid fee for the current year, and may lack an account. A member added to a household, such as a child, has no account until they accept an invitation.

### Account | Konto

A member's login. It holds the email address that login links and mailings go to, and it belongs to exactly one member. A member without an account cannot log in and receives no mailings. An administrator account is not an account in this sense.

### Login link | Inloggningslänk

A link mailed to the address of an account or an administrator account, which logs its holder in once, within a limited time, and only in the browser that asked for it. Asking again sends a new link and leaves the older ones working.

### Login code | Kod

Six digits in the same mail as a login link, for when the mail is read on another device than the one that asked. It is typed on the page the asking browser shows after the request, works only there, and stops after five wrong tries while the link keeps working. The association never asks anyone for it.

### Passkey | Lösenordsnyckel

A key a person's device or password manager holds, which logs them in to the site without a login link. It belongs to one account or one administrator account, and works only on that kind's login page. A person may have several, one per device, and removes them on their own page. A login link always works as well.

The Swedish plural is lösenordsnycklar. Why this word: [0016](docs/decisions/0016-passkeys-beside-links.md), "The Swedish word".

### Logged-in device | Inloggad enhet

A browser where a person is logged in, from one login until it ends. A login ends when the person logs out, when they end it from the list on their own page, or a fixed time after it started: 30 days for an account and 8 hours for an administrator account. The person may start that time again from the page before it runs out. Two browsers on one computer are two devices.

### Membership | Medlemskap

The association's recorded relationship with a member. Membership and fee status are separate facts.

### Paying member | Betalande medlem

A member covered by a paid individual fee or household fee for the stated year. The year matters because payment does not carry forward.

### Editor | Redaktör

A person authorised to create and publish public content. Editor permission does not grant access to the member register.

### Administrator | Administratör

A person with an administrator account, authorised to manage members, fees, registrations, volunteer bookings and mailing audiences. An administrator account is separate from an account: being an administrator neither requires nor grants membership, and losing membership does not remove administrator access. A board member who administers and is a member has both. Administrators create and remove other administrator accounts, but not below two. Administrator permission is separate from editor permission.

## Membership

### Member register | Medlemsregister

The association's authoritative collection of members, contact details, household membership and annual fee records.

### Household | Hushåll

A grouping used to let one household fee cover several members. A household is not itself a member.

### Household member addition | Tillägg i hushållet

A member with an account, or an administrator, adding a person to that member's household. It creates a member without an account.

### Invitation | Inbjudan

An offer of an account to a member who has none, sent to an email address by an administrator or by a member with an account in the same household. It becomes an account when the recipient confirms the address.

### Membership application | Ansökan om medlemskap

A visitor's request to enter the member register, for themselves alone. It becomes a member with an account, without a paid fee, when the applicant confirms their email address, and it is deleted if they have not confirmed within 24 hours. Household members are added afterwards, not through the application. Provisional until the customer decides, see [open-questions.md](docs/open-questions.md).

### Membership fee | Medlemsavgift

An annual payment recorded against one member. Its kind determines whether it covers that member or the member's household. ([system plan](docs/projektplan.md#the-member-model-records-only-data-the-confirmed-workflows-need))

### Individual fee | Avgift för enskild medlem

A membership fee that covers only the member against whom the payment is recorded.

### Household fee | Avgift för familj

A membership fee that covers the payer and whoever is in the household it was paid for now, so moving a member changes which household fee covers them. The fee stays with that household when the payer is deleted. Decided by the user on 2026-09-28 ([decisions/0025](docs/decisions/0025-member-register-in-the-application.md)).

### Fee status | Avgiftsstatus

Whether a member is covered by a paid membership fee for a stated year.

## Content and participation

### Public content | Publikt innehåll

Content that a visitor may read without member access, including news, events and association pages.

### Event | Evenemang

A scheduled public activity listed on the site.

### Event series | Serie

A named grouping of related events, such as Kaffe med drömmar or Alf Henrikson-dagen. A `serie` document in Sanity that an event refers to, and the calendar's filter ([0021](docs/decisions/0021-content-from-sanity.md)).

### Preview | Förhandsgranskning

The site as it looks with an editor's unpublished drafts, shown inside Sanity's Presentation tool and nowhere else (R009, [0021](docs/decisions/0021-content-from-sanity.md)).

### Fixed page | Sida

One of the pages R004 names, such as Styrelsen, written in Sanity as a `sida` document whose slug is the page's address.

### Offer | Erbjudande

A member benefit or limited-capacity activity for which a member may register. An administrator creates it on `/admin/erbjudanden`, and members see it on `/medlem/erbjudanden` once it is published. Offers live in PostgreSQL, not in Sanity: decided by the user on 2026-09-28.

### Registration closing | Anmälan stänger

The moment after which a member can neither register for an offer nor cancel a registration. It is the closing time the administrator set, or the offer's start when none is set, or never when the offer has neither. The fallback to the start is an agent's choice, made while building R014.

### Offer capacity | Antal platser

The number of places an offer has. Stored in PostgreSQL, not in Sanity, so that taking a place and checking the limit happen in one transaction. See the member model section of [projektplan.md](docs/projektplan.md).

### Offer registration | Anmälan

A member's reservation of a place in an offer.

### Member document | Medlemshandling

A PDF for members only, uploaded by an administrator on `/admin/handlingar` and listed for members on `/medlem/handlingar`. It is one of two kinds: an annual meeting document (årsmöteshandling) or a member letter (medlemsbrev). Member documents live in PostgreSQL, not in Sanity: decided by the user on 2026-09-28.

### Volunteer shift | Volontärpass

A dated task for volunteers at a performance, such as cloakroom or serving work.

### Volunteer booking | Volontärbokning

A member's reservation of one volunteer shift.

## Communication

### Mailing audience | Målgrupp

The members selected for one mailing by an explicit rule, such as all paying members or all volunteers. Only the selected members with an account receive it.

### Mailing | Utskick

One bulk message sent to a mailing audience. A login email is not a mailing.

## System

### Capability | Förmåga

One thing the system can do, such as looking a member up by email or recording a fee. A capability is a method on an application service, never logic inside a controller or a template. ([0014](docs/decisions/0014-one-service-layer-two-adapters.md))

### Application service | Applikationstjänst

The class that holds the capabilities for one part of the domain, and the only thing allowed to reach the database for it.

### Adapter | Adapter

A way into the application services. There are two, and they are peers: the JTE pages, which call a service in process, and the REST API, which wraps the same method in HTTP. Anything one adapter can do, the other can.

## Delivery

### Requirement | Krav

An accepted behavior or result tracked in [requirements.md](docs/requirements.md).

### MUST | Måste (M)

A requirement that version 1 must satisfy before launch.

### SHOULD | Bör (B)

A planned requirement that may be cut before any MUST requirement if time runs out.

### COULD | Kan (K)

An optional requirement that is cut before SHOULD requirements and may wait until a later release.

### Version 1 | Version 1

The first production release, due at the end of week 12, 2026-12-06, with every MUST requirement complete.

### APL period | APL-period

The twelve weeks the students work on the project, from 2026-09-14 to 2026-12-06. Week 1 was lost before the customer was assigned, so there are 11 working weeks. See [decisions/0018](docs/decisions/0018-kanban-with-weekly-syncs.md). Decided by the user on 2026-09-25.

### Week | Vecka

One week of the APL period, Monday to Sunday, numbered from week 1 starting 2026-09-14. The team works in continuous flow, and a week is a unit of the calendar, not a commitment. Replaces "sprint". Decided by the user on 2026-09-25.

### Work item | Arbetsuppgift

An issue without sub-issues, with a size. The unit that moves across the board. Decided by the user on 2026-09-25.

### Supertask | Huvuduppgift

An issue with several work items as sub-issues. Anything larger than a Large is a supertask. It stays in Backlog until it closes, and whoever closes its last sub-issue closes it. Decided by the user on 2026-09-25.

### Size | Storlek

A work item's estimate: Small, Medium or Large, each with a nominal time and a maximum in working days. Set when the item enters Ready, before anyone is assigned, and never changed afterwards. See [decisions/0018](docs/decisions/0018-kanban-with-weekly-syncs.md). Decided by the user on 2026-09-25.

### Cycle time | Cykeltid

The number of working days a work item was in progress on, counting both the first and the last day. Decided by the user on 2026-09-25.

### WIP limit | WIP-gräns

The most work one person may have in progress, by item count and by total nominal time. Decided by the user on 2026-09-25.

### Sync | Avstämning

A short team meeting to unblock work. The week-start sync is mandatory every Monday; any other sync is called by anyone, or by a work item passing its maximum. Decided by the user on 2026-09-25.

### Refill meeting | Påfyllningsmöte

A meeting of the whole team, at most 30 minutes, that splits and sizes the next requirement into work items and moves them to Ready. Called when Ready holds fewer items than there are people. The week-start sync doubles as one. See [decisions/0018](docs/decisions/0018-kanban-with-weekly-syncs.md). Decided by the user on 2026-09-25.

## WIP

### Former member | Tidigare medlem

A member who has not renewed, with the exact transition and retention period still awaiting a board decision.

### Member-only offer detail | Erbjudandeinformation för medlemmar

Offer information that a visitor must not read, if the board decides that offers contain such information.

