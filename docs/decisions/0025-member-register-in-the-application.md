---
created: 2026-09-28
provenance: user
description: Member deletion, household fees and management, and who changes a member's email address.
---

# 0025: The member register, deletion, household fees and email changes

The Decision section is the user's. Everything under Agent notes is an agent's.

## Decision

- Deleting a member (R018) deletes the member, the account and its sessions, and keeps the member's fee rows without the member, so the income per year can still be counted. Decided by the user on 2026-09-28.
- A household fee (R019) covers whoever is in the payer's household now. Moving a member into or out of a household changes which household fee covers them. Decided by the user on 2026-09-28.
- A household fee stores the household it was paid for, so the household stays covered after the payer is deleted. Decided by the user on 2026-09-28, after the GPT-6 Sol review found that deleting the payer left the rest of the household unpaid.
- Only an administrator changes a member's email address (R012). A member changes their own phone number and address. Decided by the user on 2026-09-28.
- A member can create and manage their own household, including editing and removing household members who have accounts. Removal only ends household membership, preserving the person's membership and account. A member can leave their own household. Decided by the user on 2026-09-30. The user chose this over restricting edits and removals to people without accounts, and chose removal from the household over deleting membership and login access.
- The member who creates a household is its first owner. Only its owner can rename it, add people, or edit and remove other household members. Administrators can change the owner. An owner must choose another household member with an account before leaving; if none remains, administrators take over. Other members can leave directly. Decided by the user on 2026-10-01 after review of PR #66. This narrows the household editing permission in the 2026-09-30 decision.

The second point answers T4 in [open-questions](../open-questions.md): the household fee belongs to the household, so a household can pay first and add members afterwards.

## Agent notes

Written by an agent on 2026-09-28. Everything below is a default the user has not reviewed.

- Whether a member has paid is computed, never stored: a fee row of their own for the year, or a household fee row for the year that names their household now. [`V6__fees_households_invitations.sql`](../../src/main/resources/db/migration/V6__fees_households_invitations.sql) explains the columns, and [`FeeLedger`](../../src/main/java/se/teaterihuskvarna/member/FeeLedger.java) has the rule.
- The year is the calendar year in Sweden. Nobody pays through the site: an administrator marks a payment after seeing it arrive on the bankgiro, and can undo the mark.
- The fee amounts are settings, `teaterihuskvarna.association.fee-individual-ore` and `fee-household-ore`, with the produktägare's 50 kr and 100 kr as defaults. The application form and the welcome page read them.
- Members create a household only when they belong to none, and creation makes the caller its first member. The creator is recorded as its first owner; only the current member owner can rename it, add people, and edit or remove other members. Administrators can reassign ownership. New people have no account until they accept an invitation. Login email changes remain administrator-only. This implements the user's 2026-09-30 decision with the owner restriction decided on 2026-10-01.
- Ownership uses a nullable member id in [`V14__household_owner.sql`](../../src/main/resources/db/migration/V14__household_owner.sql); null means administrators manage the household. Existing rows keep their data and do not receive an inferred creator. Administrators can assign an owner from the household's account holders. An administrator moving or deleting the owner also returns management to administrators. The older household insert remains valid with the added column. Application rollback can leave the column in place; dropping it requires reverting owner-dependent application code first.
- Removal preserves fee history, accounts, sessions and pending invitations. Household fee coverage follows the remaining membership under the existing rule. A member who leaves can create a new household. Moving an existing member into another household remains an administrator operation.
- An invitation link works once, for seven days, and sending a new one replaces the old. The link opens a page with a button, and only the button creates the account, so a mail scanner that follows the link uses nothing up.
- The register export (R021) is the same CSV format as the other exports, from [`Csv`](../../src/main/java/se/teaterihuskvarna/export/Csv.java).
- Storing the household changes one case of the rule above: a payer who moves to another household leaves the payment with the household it was paid for, and it does not follow the payer. The payer stays covered through their own fee row. The agent chose this so the fee has one household, not one that depends on whether the payer still exists. [`V11__fee_household.sql`](../../src/main/resources/db/migration/V11__fee_household.sql) fills the column for older rows from the payer's household at the time of the migration.
- Not built: deleting a household, and a limit on how many invitations a member can send.
