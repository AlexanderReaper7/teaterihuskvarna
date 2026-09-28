---
created: 2026-09-28
provenance: user
description: How mail and Brevo changes survive a failure after the commit, and how members reach Brevo as contacts.
---

# 0026: An outbox for mail and Brevo, and members as Brevo contacts

The Decision section is the user's. Everything under Agent notes is an agent's.

## Decision

- Mail goes through an outbox: a table the change's own transaction writes to, and a job that retries until the mail is sent. Decided by the user on 2026-09-28, after the GPT-6 Sol review found that a shift reminder was marked as sent before the mail went, so a failed send was never retried.

## Agent notes

Written by an agent on 2026-09-28. Everything below is a default the user has not reviewed.

### The outbox

- [`Outbox`](../../src/main/java/se/teaterihuskvarna/outbox/Outbox.java) writes a row in the caller's transaction and tries it on a background thread after the commit. A failed attempt waits one minute, then twice as long after each further failure, six hours at most, and the row is given up and logged as an error after 10 attempts, about 14 hours. A row is deleted when its work is done, so the table holds only what is pending. [`V12__outbox.sql`](../../src/main/resources/db/migration/V12__outbox.sql) has the table.
- An attempt locks its row with `FOR UPDATE SKIP LOCKED`, so the attempt after the commit and the retry job never do one row twice at once, and neither would two instances of the application.
- Work must be safe to repeat, because an attempt can succeed at the other end and still fail here, such as on a timeout. A mail sent twice in that case is the accepted cost.
- A login mail's link sits in the table in plain text until it is sent, normally within a second. Everywhere else only a token's hash is stored ([0015](0015-login-links-on-spring-security.md)). Someone who can read the table can read the other tables too, and the link expires on its own, so the agent judged the difference small.
- [`Mailer`](../../src/main/java/se/teaterihuskvarna/login/Mailer.java) keeps its rule that a response must not reveal whether an address is known: the mails that depend on an address are queued from `Background`, off the request thread, so the outbox row costs the request nothing.
