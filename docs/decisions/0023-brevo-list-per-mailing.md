---
created: 2026-09-28
provenance: user
description: How a mailing's audience reaches Brevo, and which audiences there are.
---

# 0023: Each mailing gets its own Brevo list

The Decision section is the user's. Everything under Agent notes is an agent's.

## Decision

- Each mailing creates a new Brevo list with the addresses of its audience, and the draft campaign goes to that list. Decided by the user on 2026-09-28.
- The audiences are every member, members who have paid this year, members who have not, volunteers with a shift in the last 12 months, and the members registered to a chosen offer. The user's words were "the more the merrier". Decided by the user on 2026-09-28.

This answers T1 in [open-questions](../open-questions.md) and amends [0005](0005-brevo-campaign-drafts.md), which left the synchronisation open.

## Agent notes

Written by an agent on 2026-09-28. Everything below is a default the user has not reviewed.

### How it works

- [`MailingService`](../../src/main/java/se/teaterihuskvarna/mailing/MailingService.java) resolves the audience in PostgreSQL, makes a list named `Utskick <date> <subject>` in the folder `BREVO_FOLDER_ID`, adds each address with `updateEnabled`, and creates a draft campaign to the list. The row in `mailing` is written last, so a failure part way leaves at most an unused list in Brevo, never a row that points at nothing.
- A list per mailing means nothing has to be kept in step afterwards. PostgreSQL decides who is in the audience at the moment of preparing, and the list is a copy of that moment.
- The application never sets `emailBlacklisted` on a contact. Brevo's contact API stores an unsubscribe as that flag on the contact, not on a list, so an unsubscribe should outlive every later mailing (R024). Whether adding an existing contact with `listIds` adds to or replaces its lists is not verified against a real account; the design assumes it adds.
- A mailing reaches only members with an account, because the address is on the account ([projektplan](../projektplan.md)).
- The administrator sees the HTML in the application before anything reaches Brevo (R023), and can send a test from the application to their own address. Brevo refuses a test to an address that is not a contact on some list, so the address first joins the list `BREVO_TEST_LIST_ID`. Brevo allows 50 test sends a day.
- The send button stays in Brevo, as [0005](0005-brevo-campaign-drafts.md) decided.
- The log (R025) asks Brevo for each campaign's status and numbers when the page is opened, at most once a minute per mailing, and stops asking a week after a mailing was sent. When Brevo cannot be reached, the log shows the last answer.
- Lists pile up in the folder. Deleting a list after its campaign is sent would lose nothing the log needs, but nothing does it yet.
- "Volunteers" is a member with a booking on a shift that started in the last 365 days.
- Nothing here is verified against a real Brevo account. The tests run against [`FakeBrevo`](../../src/main/java/se/teaterihuskvarna/mailing/FakeBrevo.java), and the calls in [`HttpBrevo`](../../src/main/java/se/teaterihuskvarna/mailing/HttpBrevo.java) follow Brevo's API reference as read on 2026-09-28.
