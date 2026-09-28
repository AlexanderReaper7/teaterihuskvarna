---
created: 2026-09-28
provenance: user
description: What deleting a member keeps, which household fee covers a member, and who changes a member's email address.
---

# 0025: The member register, deletion, household fees and email changes

The Decision section is the user's. Everything under Agent notes is an agent's.

## Decision

- Deleting a member (R018) deletes the member, the account and its sessions, and keeps the member's fee rows without the member, so the income per year can still be counted. Decided by the user on 2026-09-28.
- A household fee (R019) covers whoever is in the payer's household now. Moving a member into or out of a household changes which household fee covers them. Decided by the user on 2026-09-28.
- A household fee stores the household it was paid for, so the household stays covered after the payer is deleted. Decided by the user on 2026-09-28, after the GPT-6 Sol review found that deleting the payer left the rest of the household unpaid.
- Only an administrator changes a member's email address (R012). A member changes their own phone number and address. Decided by the user on 2026-09-28.

The second point answers T4 in [open-questions](../open-questions.md): the household fee belongs to the household, so a household can pay first and add members afterwards.

## Agent notes

Written by an agent on 2026-09-28. Everything below is a default the user has not reviewed.

- Whether a member has paid is computed, never stored: a fee row of their own for the year, or a household fee row for the year that names their household now. [`V6__fees_households_invitations.sql`](../../src/main/resources/db/migration/V6__fees_households_invitations.sql) explains the columns, and [`FeeLedger`](../../src/main/java/se/teaterihuskvarna/member/FeeLedger.java) has the rule.
- The year is the calendar year in Sweden. Nobody pays through the site: an administrator marks a payment after seeing it arrive on the bankgiro, and can undo the mark.
- The fee amounts are settings, `teaterihuskvarna.association.fee-individual-ore` and `fee-household-ore`, with the produktägare's 50 kr and 100 kr as defaults. The application form and the welcome page read them.
- Only an administrator adds a person to a household. A member with an account can invite the members of their own household who have no account, and nothing more. The [glossary](../../GLOSSARY.md) once said a member could add people too; that was not built.
- An invitation link works once, for seven days, and sending a new one replaces the old. The link opens a page with a button, and only the button creates the account, so a mail scanner that follows the link uses nothing up.
- The register export (R021) is the same CSV format as the other exports, from [`Csv`](../../src/main/java/se/teaterihuskvarna/export/Csv.java).
- Storing the household changes one case of the rule above: a payer who moves to another household leaves the payment with the household it was paid for, and it does not follow the payer. The payer stays covered through their own fee row. The agent chose this so the fee has one household, not one that depends on whether the payer still exists. [`V11__fee_household.sql`](../../src/main/resources/db/migration/V11__fee_household.sql) fills the column for older rows from the payer's household at the time of the migration.
- Not built: deleting a household, and a limit on how many invitations a member can send.
