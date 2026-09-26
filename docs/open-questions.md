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

- [ ] **T1. How does the application synchronise a selected mailing audience to Brevo?** Campaigns address Brevo lists or segments. The rule has to keep PostgreSQL authoritative for membership and fee status while preserving Brevo's unsubscribe and suppression state.
- [ ] **T2. Which of the Sanity personal-data measures does the project adopt?** Proxying photographs through the application, reading from `api.sanity.io`, banning AI plugins in the Studio, `DO_NOT_TRACK=1`, stripping EXIF data, and watching the subprocessor list. None is decided. The list and the reasoning are in [research/sanity-personal-data.md](research/sanity-personal-data.md).
- [ ] **T3. The page after a confirmed application asks for the name in the payment message twice**, "med namn i meddelandet" and then "Skriv {name} i meddelandet". The copy is in `messages_sv.properties`. Rewrite it together with the answer to T4.
- [ ] **T4. Must a household have added all its members before it pays the family fee, or is the family fee bound to the account, so members can be added and changed after paying?** The produktägare's wording is "50 kr enskild, 100 kr familj". An application is for one person, and household members are added only after the member exists, by an administrator or by a member of the household. The answer decides what the page after a confirmed application says about the fee, and what "paid" means for a person added to a household later in the year. Was C11, and Klas left it to the team at [the first meeting](meetings/2026-09-24-meeting-1.md#what-the-site-does).
- [ ] **T5. Is the orange `#ee6810` darkened to `#b64f0c`?** The current orange fails the WCAG 2.1 AA contrast that requirement R006 makes a MUST. The darker value passes on white and on mint and reads as the same colour. Orange on teal has no passing value, so footer icon hover would change to white or mint. Was C23, and Klas left it to the team at [the first meeting](meetings/2026-09-24-meeting-1.md#how-the-site-looks).
- [ ] **T6. Where do member-only offer details live?** At [the first meeting](meetings/2026-09-24-meeting-1.md#what-the-site-does) Klas answered C16: offer descriptions and discount details are for members only. A Free Sanity dataset publishes everything, so they need the nonprofit plan's private dataset, which Klas is applying for (C8), or storage in the application.

## Found by the e2e suite

The Playwright suite in `e2e/` ([decisions/0017](decisions/0017-playwright-e2e-in-docker.md)) found seven bugs on 2026-09-23, and all seven were fixed the same day. The comment beside each fix says what it guards against: `SecurityConfiguration` (a new session row at login, the session attribute upsert), `RefusedRequests` (the member at `/admin`, the stale CSRF token), `passkey.js` (autofill errors), `forms.js` (double presses) and `site.css` (buttons at 320 px). A new bug the suite finds goes here, with its test marked `test.fail()` until it is fixed. What the fixes left open:

- [ ] **E1.** Headless Chromium says passkey autofill is available and then refuses it. Whether a desktop Chrome does the same is not checked. Either way the page now shows nothing until the person presses the passkey button.
- [ ] **E2.** In about one Firefox run in 60, a click on the link request button was delivered and no POST was sent. Not found yet.
- [ ] **E3.** With two suites running at once on 2026-09-26, each failed one different Firefox test in `login.spec.ts`, and neither failed alone. Both times the list of logged-in devices had rows with an empty device name, which `DeviceNames` never returns, so under load a session reaches the list without the device attribute its login set. The row's button is then named plain "Logga ut", and `logOut` in `e2e/support/auth.ts` matches two buttons. Not found yet. Not marked `test.fail()`, since the test passes when run alone.
