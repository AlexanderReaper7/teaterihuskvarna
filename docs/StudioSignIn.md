---
created: 2026-10-12
provenance: agent
description: What happened when an administrator signed in to the app and went on to Sanity Studio.
---

# Signing in to Sanity Studio from the app, tested 2026-10-12

An agent wrote this record from the tester's notes and screenshots. The tester ran the steps by hand. It belongs to the live acceptance of R008 and R009, and records no tokens, secrets or member data. The only address in it, `karin.holmberg@example.test`, is invented.

## What was tested

Signing in to the app as the seeded administrator `karin.holmberg@example.test`, then following the link to Sanity and reaching `/studio/` ([0006](decisions/0006-sanity-for-now.md), [0021](decisions/0021-content-from-sanity.md)).

## Runs

1. **Already signed in to Sanity.** The tester's browser held a session for their own Sanity account. Following the link landed on that account directly. The app's session played no part.
2. **Signed out of Sanity.** Sanity asked for its own sign-in. Creating an account with `karin.holmberg@example.test` failed: Sanity sent the verification mail itself, to an address that cannot receive mail, so the local mail catcher never saw it. The code that was tried came from an older, expired mail ("Wrong code"), and the address then answered "A user with this email already exists" because an unverified account was already pending.
3. **No verified Sanity account.** `/studio/` required authorization and stopped at "Verify your email" (a 4-digit code). That step belongs to creating an account and appears only the first time. A verified account goes straight to Studio.
4. **Account that is a member of the Sanity project.** Reached `/studio/` with edit rights.

## Findings

- Sanity has its own sign-in. The app's administrator session is not used and nothing from it is passed on.
- Nothing in the app leaks. The Gmail account and the name that appeared came from the browser's own Sanity and Google sessions.
- An invented `.test` address can never become a Sanity account, because the verification mail cannot be delivered. The Sanity part of a test therefore needs real addresses, while the app's mail keeps going to Mailpit.
- Who may use Studio is decided by who is invited to the Sanity project and with which role, not by the app's roles. On the Free plan the roles are Administrator and Viewer only, so an invited editor can also delete the dataset ([0006](decisions/0006-sanity-for-now.md)).

## Not tested

A verified Sanity account that is not a member of the project. It should be denied. Testing it takes two real addresses.

## Open question

Whether an editor should sign in once for both the app and Sanity, or have a separate Sanity account. The plan asks only for the second. The question goes in the next meeting's document under [meetings](meetings/).