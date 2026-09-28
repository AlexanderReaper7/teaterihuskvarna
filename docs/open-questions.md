# Open questions

Provenance: unreviewed. An agent wrote this, and the user has not marked which parts are the user's decisions. Treat every claim as an agent's, see [decisions/0019](decisions/0019-provenance-of-documents.md).

This file holds the questions that cannot be asked at the next meeting. A question that can goes in that meeting's questions segment, in its document under [meetings/](meetings/), and the meeting document is the record of its answer. Decided by the user on 2026-09-25.

Each question has an id, and ids are not reused. A resolved question is deleted. Some questions below are quoted in Swedish from the produktägare's plan and stay that way per [decisions/0001](decisions/0001-language-policy.md).

## For the customer, deferred

- [ ] **C9. Does a Bli medlem application become a member when the applicant confirms their email address, or when an administrator approves it?** Requirement R005 says the form creates a member, but a form that writes straight into the register lets bots fill it and puts them in every mailing, so one of the two is needed. Built for now as "a member with an account once the email address is confirmed". At [the first meeting](meetings/2026-09-24-meeting-1.md#what-the-site-does) the answer was to decide later.

## For Lexicon

From the original plan. It blocks nothing in the repository.

- [ ] **L1. Vilket konsultbolag handleder, och på vilka villkor?**

## For Sanity

- [ ] **S1. Which Sanity entity does the association contract with, Sanity US Inc. or Sanity AS, and is it certified under the EU-US Data Privacy Framework?** Both answers belong in the association's record of processing ([research/sanity-personal-data.md](research/sanity-personal-data.md)).

## For the team

- [ ] **T2. Which of the Sanity personal-data measures does the project adopt?** Proxying photographs through the application, reading from `api.sanity.io`, banning AI plugins in the Studio, `DO_NOT_TRACK=1`, stripping EXIF data, and watching the subprocessor list. None is decided. The list and the reasoning are in [research/sanity-personal-data.md](research/sanity-personal-data.md).

## Found by the e2e suite

The Playwright suite in [`e2e/`](../e2e/) ([decisions/0017](decisions/0017-playwright-e2e-in-docker.md)) found seven bugs on 2026-09-23, and all seven were fixed the same day. The comment beside each fix says what it guards against: `SecurityConfiguration` (a new session row at login, the session attribute upsert), `RefusedRequests` (the member at `/admin`, the stale CSRF token), [`passkey.js`](../src/main/resources/static/js/passkey.js) (autofill errors), [`forms.js`](../src/main/resources/static/js/forms.js) (double presses) and [`site.css`](../src/main/resources/static/css/site.css) (buttons at 320 px). A new bug the suite finds goes here, with its test marked `test.fail()` until it is fixed. What the fixes left open:

- [ ] **E1.** Headless Chromium says passkey autofill is available and then refuses it. Whether a desktop Chrome does the same is not checked. Either way the page now shows nothing until the person presses the passkey button.
- [ ] **E2.** In about one Firefox run in 60, a click on the link request button was delivered and no POST was sent. Not found yet.
- [ ] **E3.** With two suites running at once on 2026-09-26, each failed one different Firefox test in [`login.spec.ts`](../e2e/tests/login.spec.ts), and neither failed alone. Both times the list of logged-in devices had rows with an empty device name, which `DeviceNames` never returns, so under load a session reaches the list without the device attribute its login set. The row's button is then named plain "Logga ut", and `logOut` in [`e2e/support/auth.ts`](../e2e/support/auth.ts) matches two buttons. Not found yet. Not marked `test.fail()`, since the test passes when run alone. On 2026-09-28 it recurred in one suite running [`application.spec.ts`](../e2e/tests/application.spec.ts), [`login.spec.ts`](../e2e/tests/login.spec.ts) and [`passkeys.spec.ts`](../e2e/tests/passkeys.spec.ts) in sequence, in "an older link still works after asking for a newer one" under Firefox, and this time the row without a name was the current device, whose session row had `endsAt` but no `device`. The same test passed 15 times in a row alone. On 2026-09-29 the full suite failed once in Firefox in [`devices.spec.ts`](../e2e/tests/devices.spec.ts), "a device logged out from another shows it is logged out": the list showed one device where two had logged in, which fits a session that lost `endsAt`, since `DeviceService` skips a session without it. It passed five times in a row alone.
