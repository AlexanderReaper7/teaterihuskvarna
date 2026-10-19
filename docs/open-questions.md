# Open questions

Provenance: unreviewed. An agent wrote this, and the user has not marked which parts are the user's decisions. Treat every claim as an agent's, see [decisions/0019](decisions/0019-provenance-of-documents.md).

This file holds the questions that cannot be asked at the next meeting. A question that can goes in that meeting's questions segment, in its document under [meetings/](meetings/), and the meeting document is the record of its answer. Decided by the user on 2026-09-25.

Each question has an id, and ids are not reused. A resolved question is deleted. Some questions below are quoted in Swedish from the produktägare's plan and stay that way per [decisions/0001](decisions/0001-language-policy.md).

## For the customer, deferred

C1-C4 were deferred from [meeting 2](meetings/2026-09-30-meeting-2.md) by the user on 2026-09-30 to focus on the current build. They remain required follow-ups before launch.

- [ ] **C1. Vem ersätter produktägaren vid frånvaro?** From the original plan.
- [ ] **C2. Who maintains the system after the APL period ends, under what agreement, and who holds the credentials?** The answer is a person or a company. This is the plan's largest stated risk, because the association budgets a few hours a year and the students leave at week 12. The original asked "nästa Lexicon-omgång, konsultbolaget eller båda?", and all three of those answers are guesses about who might volunteer.
- [ ] **C3. Which two board members get administrator access to the production accounts?** The plan requires at least two, and a release check has the second one sign in to every production account.
- [ ] **C4. Which editors and administrators test the system before launch?** A release check times an editor who has never used the system publishing an event, and another has real administrators mark a fee paid, export the register and send a mailing.

C9, C10 and C17 were also deferred from that meeting by the user on 2026-09-30.

- [ ] **C9. Does a Bli medlem application become a member when the applicant confirms their email address, or when an administrator approves it?** Requirement R005 says the form creates a member, but a form that writes straight into the register lets bots fill it and puts them in every mailing, so one of the two is needed. Built for now as "a member with an account once the email address is confirmed". At [the first meeting](meetings/2026-09-24-meeting-1.md#what-the-site-does) the answer was to decide later.
- [ ] **C10. Is 817-5531 still the association's bankgiro number?** The old site's footer gives it next to organisation number 826001-8224 ([join.png](../design/old-site/screenshots/join.png)). A new member is told to pay the fee to it.
- [ ] **C17. Which private email addresses and phone numbers must never appear on the public site?** A release check crawls the site for the board's private contact details, and it needs the list. The old site published several.

### Agent notes

C12 is deferred until bank reconciliation is considered. The [system plan](projektplan.md#must-requirements-determine-whether-version-1-can-launch) excludes automatic bank reconciliation from version 1; fee marking remains manual.

- [ ] **C12. Can the association provide real bankgiro payments, anonymised, before reconciliation is built?** The plan's rule for matching a payment to a member has to be checked against them. A payer's name may differ from the member's.
- [ ] **C27. What does a bankgiro payment with an email address as the message look like on the association's statement?** Members write their account's email address as the message. Banks cap the message at as few as 12 characters until autumn 2026, and whether `@` survives is unknown, see [research/bankgiro-payment-message.md](research/bankgiro-payment-message.md). A 1 kr test payment from SEB and from the Swedbank app, read by whoever sees the statement, answers both. Move it to the next meeting's document when that document is created.

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
