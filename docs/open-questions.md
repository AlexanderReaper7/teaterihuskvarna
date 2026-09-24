# Open questions

The first two parts are for the customer, the board and the produktägare. "For this meeting" is what gets asked at the next meeting, in the order to ask it. The rest of the customer's questions come after, and then everything that is not for the customer. Each question has an id, so an answer can be written down against "C7" during the meeting. A resolved question is deleted, and its answer goes into [projektplan.md](projektplan.md) or a decision record in the same change. Ids are not reused.

Some questions below are quoted in Swedish from the produktägare's plan and stay that way per [decisions/0001](decisions/0001-language-policy.md).

## For this meeting

The order starts with a fact, moves to what the site does and how it looks while the screenshots are on screen, then personal data, and ends with who does what.

### The association today

- [ ] **C5. Hur många medlemmar har föreningen idag?** From the original plan. The number decides the Brevo plan, because the free plan stops at 300 mails a day, and the size of the system.

### What the site does

- [ ] **C9. Does a Bli medlem application become a member when the applicant confirms their email address, or when an administrator approves it?** Requirement P5 says the form creates a member, but a form that writes straight into the register lets bots fill it and puts them in every mailing, so one of the two is needed. Built for now as "a member with an account once the email address is confirmed".
- [ ] **C11. Must a household have added all its members before it pays the family fee, or is the family fee bound to the account, so members can be added and changed after paying?** The produktägare's wording is "50 kr enskild, 100 kr familj". An application is for one person, and household members are added only after the member exists, by an administrator or by a member of the household. The answer decides what the page after a confirmed application says about the fee, and what "paid" means for a person added to a household later in the year.
- [ ] **C16. Are offer descriptions and discount details public, or for members only?** Anything published in a Free Sanity dataset is public. Member-only details need the nonprofit plan's private dataset from C8, or storage in the application.
- [ ] **C18. May mailings be sent from Brevo instead of the admin view?** The original flow previews, sends and logs a mailing in the application. [decisions/0005](decisions/0005-brevo-campaign-drafts.md) has the application create a draft in Brevo, where the administrator test-sends and sends it, and Brevo's campaign history is the log (U2, U4). Built as 0005 says until this is answered.

### How the site looks

The produktägare said they liked the old site's graphics. [design/old-site](../design/old-site/README.md) surveys that design. Have its screenshots open.

- [ ] **C20. Which part of the old graphics should the new site keep?** The typing hero ("FÖRENINGEN TEATER I HUS" + VAGN, KUR, DJUR, ARREST, KVARNA), the teal and mint palette, the layout, or the logo. The survey's guess is the typing hero, so show [home.png](../design/old-site/screenshots/home.png) first.
- [ ] **C23. May the orange `#ee6810` be darkened to `#b64f0c`?** The current orange fails the WCAG 2.1 AA contrast that requirement P6 makes a MUST. The darker value passes on white and on mint and reads as the same colour. Orange on teal has no passing value, so footer icon hover would change to white or mint.
- [ ] **C21. Does the association have original files for its artwork, and the right to use them?** The website only has raster copies, and neither can be scaled up.
  - The logo, as a vector file, and in a dark-on-light version. The web copy is a white-on-dark PNG that cannot be recoloured.
  - Jätten Vist. Who drew it, and does the association have the original and the right to use it? The web copy is 180x338 pixels.

### Personal data

The board's obligations, which the system cannot carry for them.

- [ ] **C13. How long is data about members who have not renewed kept?** From the original plan. The answer covers database backups too, not only the live database ([decisions/0007](decisions/0007-postgres-in-a-container.md)).
- [ ] **C14. How is consent for photographs handled, and who holds it?** Requirement P4's board and production pages publish names and photographs of identifiable people. That needs a lawful basis, an image release for performers, a practice for audience shots, and an answer for when someone asks to be removed. **Children in productions need this settled before the first photo is uploaded.** The board runs this process, and the system cannot enforce it.
- [ ] **C15. Who writes down the purpose and lawful basis for each kind of personal data the association keeps?** The plan requires it. Sanity holds names and photographs, the application holds the member register, and Brevo holds mail addresses.

### Who does what

Each answer here is a name and a date, so the meeting ends with its action items.

- [ ] **C6. Should the domain and the WordPress site be moved or shut down at launch?** Pointing teaterihuskvarna.se at the new site waits for this answer. If anything keeps running on a subdomain such as `www.`, that affects how login cookies are scoped ([decisions/0015](decisions/0015-login-links-on-spring-security.md)).
- [ ] **C7. Who creates a GitHub organisation for the association, on `webb@teaterihuskvarna.se`?** The repository currently lives under the personal account AlexanderReaper7 and moves there once it exists ([decisions/0002](decisions/0002-accounts-under-a-personal-login.md)).
- [ ] **C8. Who applies for the three nonprofit programmes, and when?** Each is a separate application, and the association has to validate as a nonprofit first.
  - GitHub for Nonprofits, needs the organisation from C7. It gives free GitHub Team, which is what makes branch protection on a private repository possible ([decisions/0003](decisions/0003-no-branch-protection-yet.md)).
  - [Sanity's nonprofit plan](https://www.sanity.io/docs/platform-management/non-profit-plan), after the Sanity project exists. It gives private datasets, Growth roles and 25 users at no charge. The Free plan is the fallback ([decisions/0006](decisions/0006-sanity-for-now.md)).
  - Microsoft's Azure credit. The original plan asks whether to apply at all. It is 2 000 USD a year, does not roll over, and expires 90 days after issuance if not activated. Hosting is costed without it ([decisions/0007](decisions/0007-postgres-in-a-container.md)), so this is headroom, not a blocker.

## For the customer, later

### People we need named

Several release checks in [projektplan.md](projektplan.md) need a named person, not a role.

- [ ] **C1. Vem ersätter produktägaren vid frånvaro?** From the original plan.
- [ ] **C2. Who maintains the system after the APL period ends, under what agreement, and who holds the credentials?** The answer is a person or a company. This is the plan's largest stated risk, because the association budgets a few hours a year and the students leave at week 12. The original asked "nästa Lexicon-omgång, konsultbolaget eller båda?", and all three of those answers are guesses about who might volunteer.
- [ ] **C3. Which two board members get administrator access to the production accounts?** The plan requires at least two, and a release check has the second one sign in to every production account.
- [ ] **C4. Which editors and administrators test the system before launch?** A release check times an editor who has never used the system publishing an event, and another has real administrators mark a fee paid, export the register and send a mailing.

### Membership and fees

- [ ] **C10. Is 817-5531 still the association's bankgiro number?** The old site's footer gives it next to organisation number 826001-8224 ([join.png](../design/old-site/screenshots/join.png)), but that site has not been updated in three years. A new member is told to pay the fee to it.
- [ ] **C12. Can the association provide real bankgiro payments, anonymised, before reconciliation is built?** The plan's rule for matching a payment to a member has to be checked against them. A payer's name may differ from the member's.

### Personal data

- [ ] **C17. Which private email addresses and phone numbers must never appear on the public site?** A release check crawls the site for the board's private contact details, and it needs the list. The old site published several.

### Design

- [ ] **C19. Is carrying over the old site's design part of version 1 at all?**

## For everyone else

### For Lexicon

Both are from the original plan. Neither blocks anything in the repository.

- [ ] **L1. Vilket konsultbolag handleder, och på vilka villkor?**
- [ ] **L2. Startdatum för APL-perioden.**

### For Sanity

- [ ] **S1. Which Sanity entity does the association contract with, Sanity US Inc. or Sanity AS, and is it certified under the EU-US Data Privacy Framework?** Both answers belong in the association's record of processing ([research/sanity-personal-data.md](research/sanity-personal-data.md)).

### For the team

- [ ] **T1. How does the application synchronise a selected mailing audience to Brevo?** Campaigns address Brevo lists or segments. The rule has to keep PostgreSQL authoritative for membership and fee status while preserving Brevo's unsubscribe and suppression state.
- [ ] **T2. Which of the Sanity personal-data measures does the project adopt?** Proxying photographs through the application, reading from `api.sanity.io`, banning AI plugins in the Studio, `DO_NOT_TRACK=1`, stripping EXIF data, and watching the subprocessor list. None is decided. The list and the reasoning are in [research/sanity-personal-data.md](research/sanity-personal-data.md).
- [ ] **T3. The page after a confirmed application asks for the name in the payment message twice**, "med namn i meddelandet" and then "Skriv {name} i meddelandet". The copy is in `messages_sv.properties`. Rewrite it together with the answer to C11.

### Found by the e2e suite

The Playwright suite in `e2e/` ([decisions/0017](decisions/0017-playwright-e2e-in-docker.md)) found seven bugs on 2026-09-23, and all seven were fixed the same day. The comment beside each fix says what it guards against: `SecurityConfiguration` (a new session row at login, the session attribute upsert), `RefusedRequests` (the member at `/admin`, the stale CSRF token), `passkey.js` (autofill errors), `forms.js` (double presses) and `site.css` (buttons at 320 px). A new bug the suite finds goes here, with its test marked `test.fail()` until it is fixed. What the fixes left open:

- [ ] **E1.** Headless Chromium says passkey autofill is available and then refuses it. Whether a desktop Chrome does the same is not checked. Either way the page now shows nothing until the person presses the passkey button.
- [ ] **E2.** In about one Firefox run in 60, a click on the link request button was delivered and no POST was sent. Not found yet.
