# Open questions

## From the project plan

These block sprint 1 and are the board's to answer.

- [ ] Hur många medlemmar har föreningen idag? (Avgör Brevo-plan och systemets storlek.)
- [ ] Vem ersätter produktägaren vid frånvaro?
- [ ] Vilket konsultbolag handleder, och på vilka villkor (pris eller partnerskap)?
- [ ] Vem förvaltar efter APL: nästa Lexicon-omgång, konsultbolaget eller båda?
- [ ] Startdatum för APL-perioden.
- [ ] Ska styrelsen ansöka om Microsofts ideella program för Azure-krediter?
- [ ] Ska domänen och befintlig WordPress-sida flyttas eller stängas vid lansering?
- [ ] Hur länge sparas uppgifter om medlemmar som inte förnyat? The answer governs database backups too, not only the live database, per [decisions/0007](decisions/0007-postgres-in-a-container.md).

## Raised during setup

Not in the original document. Added here so they are not lost.

- [ ] Transfer the repository to an organisation the association owns, on `webb@teaterihuskvarna.se`. It currently lives under the personal account AlexanderReaper7 because no such organisation exists yet. Commits, issues and pull requests survive a transfer; Actions secrets and variables do not and must be re-entered. See [decisions/0002](decisions/0002-accounts-under-a-personal-login.md).
- [ ] Apply for GitHub for Nonprofits once the organisation exists. It grants free GitHub Team, which is what makes branch protection on a private repository possible. Separate application from the Microsoft Azure credit above. See [decisions/0003](decisions/0003-no-branch-protection-yet.md).
- [ ] Apply for Microsoft's nonprofit Azure grant, or decide not to. It is 2 000 USD a year, does not roll over, and expires 90 days after issuance if not activated. Hosting is costed without it per [decisions/0007](decisions/0007-postgres-in-a-container.md), so this is headroom rather than a blocker, but it has to be applied for to be worth anything. Requires the association to validate as a nonprofit first.
- [ ] How is consent for photographs handled, and who holds it? Requirement P4 asks for pages about the board and about productions, so names and photographs of identifiable people get published in Sanity by design. That needs a lawful basis, an image release for performers, a practice for audience shots, and an answer to what happens when someone later asks to be removed. **Children in productions need this settled before the first photo is uploaded, not after.** This is a process the board runs, not something the system can enforce. See the personal data section of [projektplan.md](projektplan.md).
