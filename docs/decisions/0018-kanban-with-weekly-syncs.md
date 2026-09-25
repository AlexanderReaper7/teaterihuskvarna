---
created: 2026-09-25
provenance: user
description: How the team plans, sizes and tracks its work.
---

# 0018: Kanban with weekly syncs on a GitHub Projects board

The decisions are the user's. Where an agent proposed a rule and the user accepted it without giving a reason, the rule says so, and any reasoning for it is under Agent notes. Everything under Agent notes is an agent's.

## The board

Work is tracked on the [Kanban project](https://github.com/users/AlexanderReaper7/projects/1), linked to this repository.

- Each row of [requirements.md](../requirements.md) is one issue, and the work on a requirement goes in sub-issues of that issue. A requirement's progress is on the board, and requirements.md has no Status column. Decided by the user on 2026-09-25. Issues #4 to #28 were created from the table on 2026-09-25.
- The board holds issues only, no pull request cards: "No PR cards. atleast on this board."
- The `Priority` field has the values MUST, SHOULD and COULD.
- The `Item reopened` and `Auto-archive items` workflows are on.

## Kanban, in weeks

The team works in continuous flow, not sprints. In the user's words: "kanban. so use weeks instead of sprints." This replaces both the two-week sprints in [projektplan-original.en.md](../projektplan-original.en.md) and the one-week sprints this record first held. The APL course does not expect Scrum artefacts.

The `Week` field has twelve one-week iterations, Monday to Sunday. The APL period is 12 weeks: "the first week was wasted because we hadnt received the customer yet. so 12 weeks visible, 11 working weeks total. 5 days per week, 8 hours per day." Week 1 began 2026-09-14, and week 12 ends 2026-12-06.

## Meetings

| Meeting | When | Length |
| --- | --- | --- |
| Week-start sync | Every Monday at about 09:00, mandatory | 1 hour allocated |
| Product owner meeting with Klas | Weekly, tentatively Thursday | At least 1 hour |
| Retrospective | Friday of weeks 3, 5, 7, 9 and 11, and a closing one in week 12 | Not set |
| Sync | When anyone calls it, or when an item passes its maximum | At most 15 minutes |

- The week-start sync is also the weekly refill of Ready.
- The product owner meeting is "as regular as can be. preferably weekly or so, minimum 1 hour allocated. this is also review."
- The team agrees the retrospective's time of day.
- A sync ends when every problem raised has an owner and a next step, and after that only the people involved stay. An agent proposed this, and the user accepted it.
- Replenishment: Klas orders the requirements at the product owner meeting. Besides the Monday refill, the team breaks down the next requirement whenever Ready holds fewer items than there are people. An agent proposed this, and the user accepted it.

The recurring events are in [calendar.ics](../meetings/calendar.ics), written from the meetings in [kanban.toml](../../kanban.toml) by the template's `kanban/ics.py`. It holds only the Monday sync until the other two have times.

## Work items and sizes

A work item is an issue without sub-issues, and needs a size. An issue with sub-issues is a supertask and has no size, so a requirement that is one work item's worth of work stays a single issue. An agent proposed this, and the user accepted it.

The sizes:

| Size | Nominal | Maximum |
| --- | --- | --- |
| Small | Half a day | 2 working days in progress |
| Medium | 1 day | 3 working days in progress |
| Large | 3 days | 5 working days in progress |

- "anything larger than one week should be a supertask, not a work item. super tasks have several work items."
- Time is counted in days, not hours: "measuring hours makes the assumption that all hours are equal but i think humans are differently productive over the day. a task that start just before lunch or just before end of day will have less velocity/productivity than one started abit after start of day."
- A day counts if the item was in progress on it, and both the first and the last day count.
- An item passing its maximum calls a sync.
- Review is separate from the maximum: only days In progress count toward it. Decided by the user on 2026-09-25.
- An item gets its size when it enters Ready, before anyone is assigned to it, and the size does not change after it leaves Ready. An agent proposed this, and the user accepted it.
- A size is the same whoever does the work.
- An item may enter Ready when it has a size, acceptance criteria, and no open question to Klas. An agent proposed this, and the user accepted it.
- Work under about an hour gets no size. The user prefers it as a checklist line in the issue it belongs to, and deferred any firmer rule "for when/if it becomes a problem". A smaller size, Tiny, was considered and removed.

The nominal and maximum numbers came from a discussion between the user and an agent. The user set "large max 5 days", and accepted the rest.

## Work-in-progress limits

The limits are per person, by count and by total nominal time, and allow "atleast 2 medium tasks concurrently". The starting numbers were proposed by an agent and accepted by the user:

- At most 3 items in progress per person.
- At most 3 days of nominal time in progress per person.
- At most 3 items in review for the whole team.

## The board check

The Status column descriptions on the board state the rule for each column, so the Ready rules show where an item is pulled. A script reads the board every weekday morning, and reports items past their maximum, broken WIP limits, a Ready column with fewer items than people, and broken rules. The user asked for the script, and approved setting the Ready rules through the API.

The script lives in the template repository [kanban-weeks](https://github.com/AlexanderReaper7/kanban-weeks), so other projects can reuse it. [kanban.toml](../../kanban.toml) holds this project's numbers, and `.github/workflows/kanban.yml` calls the template's workflow at a pinned tag. The token is a machine account's, which can reach only this repository and the project. Decided by the user on 2026-09-25.

The report is a comment on the closed issue labelled `board report`, posted when the findings change. Subscribe to that issue to get it.

## Agent notes

Written by an agent on 2026-09-25. None of this is the user's reasoning.

- **Choices the agent made alone.**
  - The iteration names "Week 1" to "Week 12".
  - The Size colours, and the option descriptions that state each size's nominal and maximum.
  - Moving R010 and R011 to Done and closing them, R005 and R012 to In progress, and the rest to Backlog, following their Status in requirements.md.
  - `Item reopened` sets Backlog.
  - `Auto-archive items` uses `is:issue is:closed updated:<@today-4w`, and four weeks is the agent's number.
  - The auto-add filter is `is:issue is:open`.
  - The Swedish words for the new glossary terms.
- **Why the priority values are words.** The template's P0, P1 and P2 collided with the requirement ids of the time, P1 to P6, which are now R001 to R006.
- **Pull request cards.** With them, one piece of work is two cards. `Pull request linked to issue` and `Pull request merged` move the issue card instead, but only when a pull request names its issue with a closing keyword such as `Closes #12`, and nothing enforces that.
- **The rules the agent proposed, and the reasons it gave for them.**
  - The 15-minute sync cap. A meeting called about one problem otherwise grows into everyone watching two people solve it.
  - The Tiny size was removed because every exception in this design came from it:
    - It counted toward the item limit but not the time limit.
    - It needed a different reaction at its maximum.
    - It raised the question of promoting an overdue Tiny to Small, which would have rewritten the recorded estimate.
    - Measured in days, a Tiny and a Small both come out as about 1 day in progress, so a Tiny estimate could never be checked.
  - Maximums add one day of slack for the time of day an item starts. A Small started at 15:00 has not reached its nominal half day by the end of that day.
  - Large has the least slack. Started after lunch on a Monday, it reaches its nominal 3 days on Thursday afternoon, which is 4 days in progress against a maximum of 5.
  - The time cap cannot go below 3 days, or nobody could ever start a Large.
  - Sizing in Ready, before anyone is assigned, keeps the estimator's own speed out of the size. Freezing the size keeps the estimate measurable against the cycle time.
- **Research on productivity over the day.** The user asked for it, and it bears on the reason for counting in days.
  - A 2025 systematic review of 65 studies (Chauhan et al., [Chronobiology International 42(4)](https://doi.org/10.1080/07420528.2025.2490495)) found no main effect of chronotype in more than 80% of studies. It found no time-of-day effect in 37 of 45 young-adult studies. It found a synchrony effect, meaning better performance at one's own best time, in 29 of 64 young-adult studies. Every task in those studies lasted minutes, in a lab.
  - The post-lunch dip is real, and occurs even without lunch (Monk 2005, [PubMed 15892914](https://pubmed.ncbi.nlm.nih.gov/15892914/)).
  - Developers rate their own productivity differently over the day. Meyer et al. 2017 ([PDF](https://gwern.net/doc/psychology/writing/2017-meyer.pdf)) asked 20 developers every hour for three weeks. They found morning people (20%), low-at-lunch people and afternoon people (40%).
  - The agent's reading: the evidence for a biological daily curve in 18 to 25 year olds is weak. The evidence that breaks and interruptions cost time is stronger. Either way, counting whole days absorbs both without modelling them.
- **Why a script.** GitHub column limits count one column for the whole team, and only warn. The per-person limits, the maximums and the sync trigger need a script.
- **Choices in kanban-weeks the agent made alone.**
  - A supertask counts toward no WIP limit, since its work items already do.
  - `team.size` is 4, the students in the customer's plan, and sets the Ready refill threshold.
  - The check runs at 05:00 UTC on weekdays, 07:00 in summer time and 06:00 in winter.
  - The report goes to a closed issue because the board's auto-add filter takes open issues only, and a comment notifies subscribers where an edited issue body does not.
  - The machine account gets the Read role on the repository and Write on the project, the least that lets it comment and set `Start date`.
  - The Backlog description became generic, "Waits here in the order Klas sets", so the template writes the same text for every project.
- **Where an item's start comes from.** The issue timeline has a `ProjectV2ItemStatusChangedEvent`, but moving issue #4 Backlog → Ready → Backlog through the API on 2026-09-25 recorded none. Whether a move in the browser records one is unchecked. The script uses the later of such an event and the `Start date` field, and `--write` sets `Start date` to today on each item in progress without one. The day it records is only right if `--write` runs every working day. An item sent back from In review to In progress keeps its `Start date`, so its days in review then count toward its maximum.
- **Why a machine account.** GitHub's documentation says "`GITHUB_TOKEN` is scoped to the repository level and cannot access projects", and recommends a personal access token (classic) with the `project` and `repo` scopes for user projects ([Automating Projects using Actions](https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/automating-projects-using-actions), read 2026-09-25). A fine-grained token has no permission for a user-owned project. The repository is private, so the token needs `repo`, which covers every repository its owner can reach. Anyone with write access can read a secret by pushing a workflow that prints it, and the students have write access.
- **Not checked yet.** Whether the scheduled workflow works: it cannot run until the machine account and its `BOARD_TOKEN` secret exist. The scripts were run by hand against this board and against a scratch project on 2026-09-25.
- **Not checked by the script, by design.** Acceptance criteria, open questions to Klas, and that a size never changed after Ready. Whether GitHub keeps a history of project field changes that could show the last is unchecked; if it does not, the script would have to record each size itself.
- **Setting the Status descriptions.** `updateProjectV2Field` replaces the option list. Passing each option's existing `id` kept every item's Status; the agent compared all 25 before and after.
- **Workflows.** The GraphQL API cannot create a project workflow, only delete one, so workflows are changed in the browser on the project's Workflows page.
- **Arithmetic.**
  - 11 working weeks of 5 days at 8 hours is 440 hours per student.
  - From week 3, 4 students × 10 weeks × 5 days is 200 person-days, before meetings.
