# Open questions

## From the project plan

These block sprint 1 and are the board's to answer.

- [ ] Hur många medlemmar har föreningen idag? (Avgör Brevo-plan och systemets storlek.)
- [ ] Vem ersätter produktägaren vid frånvaro?
- [ ] Who maintains the system after the APL period ends, under what agreement, and who holds the credentials? This is the plan's largest stated risk: the association budgets a few hours a year, and the students leave at week 12. An answer naming a person or a company, not a hope that the next Lexicon cohort picks it up.
- [ ] Ska styrelsen ansöka om Microsofts ideella program för Azure-krediter? It is 2 000 USD a year, does not roll over, and expires 90 days after issuance if not activated. Hosting is costed without it per [decisions/0007](decisions/0007-postgres-in-a-container.md), so this is headroom rather than a blocker. Requires the association to validate as a nonprofit first, and is a separate application from GitHub for Nonprofits below.
- [ ] Ska domänen och befintlig WordPress-sida flyttas eller stängas vid lansering?
- [ ] Hur länge sparas uppgifter om medlemmar som inte förnyat? The answer governs database backups too, not only the live database, per [decisions/0007](decisions/0007-postgres-in-a-container.md).

Three of the original document's questions are not in that list. "Vilket konsultbolag handleder, och på vilka villkor" and "Startdatum för APL-perioden" are Lexicon's to settle and block nothing in the repository. The maintenance question was reworded from "Vem förvaltar efter APL: nästa Lexicon-omgång, konsultbolaget eller båda?", because all three of its answers are guesses about who might volunteer, and the question needs a name.

## Raised during setup

Not in the original document. Added here so they are not lost.

- [ ] Transfer the repository to an organisation the association owns, on `webb@teaterihuskvarna.se`. It currently lives under the personal account AlexanderReaper7 because no such organisation exists yet. Commits, issues and pull requests survive a transfer; Actions secrets and variables do not and must be re-entered. See [decisions/0002](decisions/0002-accounts-under-a-personal-login.md).
- [ ] Apply for GitHub for Nonprofits once the organisation exists. It grants free GitHub Team, which is what makes branch protection on a private repository possible. Separate application from the Microsoft Azure credit above. See [decisions/0003](decisions/0003-no-branch-protection-yet.md).
- [ ] How is consent for photographs handled, and who holds it? Requirement P4 asks for pages about the board and about productions, so names and photographs of identifiable people get published in Sanity by design. That needs a lawful basis, an image release for performers, a practice for audience shots, and an answer to what happens when someone later asks to be removed. **Children in productions need this settled before the first photo is uploaded, not after.** This is a process the board runs, not something the system can enforce. See the personal data section of [projektplan.md](projektplan.md).
- [ ] Apply for [Sanity's nonprofit plan](https://www.sanity.io/docs/platform-management/non-profit-plan) after the project exists. It mirrors Growth within quota at no charge and adds private datasets, Growth roles and 25 included users. The Free plan remains the fallback. See [decisions/0006](decisions/0006-sanity-for-now.md).
- [ ] Are offer descriptions and discount details public, or may only members read them? Published documents in a Free Sanity dataset are public. Member-only details need the nonprofit plan's private dataset or storage in the application.
- [ ] How does the application synchronise a selected mailing audience to Brevo? Campaigns address Brevo lists or segments. The rule must keep PostgreSQL authoritative for membership and fee status while preserving Brevo's unsubscribe and suppression state.
