# How the team works

<!-- Provenance: user. An agent wrote this guide from decisions/0018, and the user read all of it and approved it on 2026-09-25. -->

This guide covers the team's week, with the purpose of each meeting, and the [Kanban board](https://github.com/users/AlexanderReaper7/projects/1) on GitHub: what is on it, how to take work from it, and the few rules that keep it useful. Branches, commits and pull requests are in [branches-and-pull-requests.md](branches-and-pull-requests.md).

The rules come from [decisions/0018](decisions/0018-kanban-with-weekly-syncs.md), which also says why.

## The idea

All the work on the project is written down as issues. Each issue is a card on the board, and the board has one column for each stage a piece of work goes through. When you work on something, its card sits in your column. Anyone can open the board and see who is doing what, what is waiting, and what is stuck, without asking.

Two rules make it work:

1. **You pull work, nobody hands it to you.** When you have room, you take the card you want to work on (preferably the top card) from Ready and put your name on it.
2. **You limit how much you have going at once.** Finishing one thing beats having four things half done.

That is what "Kanban" means here. There are no sprints. Work flows through the board continuously, and the team meets at set times to look at it together, see [The week](#the-week).

## Words you will see

| Word | What it means |
| --- | --- |
| Issue | A written piece of work on GitHub, with a title, a description and a number such as `#32`. |
| Card | How an issue looks on the board. Moving a card between columns changes the issue's Status. |
| Column | One stage: Backlog, Ready, In progress, In review, Done. |
| Requirement | One thing the customer asked for, listed in [requirements.md](requirements.md) with an id such as R012. Each requirement is one issue. |
| Sub-issue | An issue that belongs to a bigger issue, its parent. |
| Work item | An issue without sub-issues. This is what you actually do. It has a size. |
| Supertask | An issue with sub-issues. You never work on it directly, you work on its sub-issues. It has no size. |
| Size | How big a work item is: Small, Medium or Large. |
| WIP limit | "Work in progress" limit, the most cards allowed in progress or in review at once. |
| Sync | A short meeting, at most 15 minutes, to get something unstuck. See [Sync](#sync). |
| Refill meeting | A meeting to split and size new work when Ready runs low. See [Refill meeting](#refill-meeting). |
| Klas/product owner | The product owner, who speaks for the customer and decides the order of the work. |

The [glossary](../GLOSSARY.md) has the exact definitions.

## The week

The team works in weeks, Monday to Sunday (5 working days). Week 1 began on 2026-09-14 and week 12 ends on 2026-12-06. A week is a unit of the calendar, not a promise: nobody commits to a list of cards to finish by Friday. Cards move across the board all the time, and the meetings are the fixed points where the team looks at the board together.

| Meeting | When | Length | Who comes |
| --- | --- | --- | --- |
| [Sync](#sync) | When anyone calls one, or a card passes its maximum | At most 15 minutes | Everyone, then only the people involved |
| [Refill meeting](#refill-meeting) | When Ready holds fewer than 4 cards | At most 30 minutes | Everyone |
| [Week-start sync and refill](#week-start-sync-and-refill) | Every Monday at 09:00 | 1 hour set aside | Everyone. It is mandatory |
| [Product owner meeting](#product-owner-meeting) | Weekly, probably Thursday | At least 1 hour | The team and Klas |
| [Retrospective](#retrospective) | Friday of weeks 3, 5, 7, 9 and 11, and a last one in week 12 | Not set | Everyone |

The Monday meeting is in [calendar.ics](meetings/calendar.ics), which any calendar app can import. The others get added once they have a fixed time.

### Sync

A sync is a short meeting to get something unstuck. Anyone may call one at any time, for anything that blocks them: a question nobody can answer alone, a card that turned out bigger than it looked, two people about to do the same work. A card that passes its maximum days in progress also calls one, see [Sizes](#sizes-and-how-long-an-item-may-take).

A sync lasts at most 15 minutes. It ends when every problem raised has someone who owns it and a next step, and after that only the people involved stay. The sync is where a problem gets an owner. Solving it happens afterwards, without the whole team watching.

Call one early. Asking on the first day you are stuck costs 15 minutes; asking on the third costs two days.

### Refill meeting

Ready is where work waits to be pulled. When it holds fewer cards than there are people on the team, four, someone will soon have nothing to pull. Whoever notices calls a refill meeting. The whole team comes, for at most 30 minutes, and:

1. Takes the next requirement in Klas's order, the top of Backlog.
2. Splits it into work items, see [Splitting work into sub-issues](#splitting-work-into-sub-issues).
3. Agrees a size for each work item together.
4. Moves each work item that meets the Ready rule into Ready: it has a size, acceptance criteria, and no open question to Klas.

A card with an open question to Klas stays in Backlog. The question goes in the next meeting's document under [meetings](meetings/), and the card moves once Klas has answered.

Nobody is assigned at a refill. The team agrees the size before anyone knows who will do the work, so the size is the same whoever ends up doing it.

### Week-start sync and refill

Every Monday at 09:00 the whole team meets, with an hour set aside. The meeting is a sync and refill meeting in one:

1. Go through the board together: what was finished last week, what waits in review, and what has been in progress for a long time.
2. Give every problem an owner and a next step, as in a [sync](#sync).
3. Refill Ready, as in a [refill meeting](#refill-meeting).

Afterwards everyone should know what they will pull next and who to ask when stuck.

### Product owner meeting

Klas is the product owner. Klas speaks for the customer, and decides what gets built first. The team meets Klas once a week, probably on Thursdays, for at least an hour. The meeting is also the review of finished work:

- The team shows Klas what was finished since the last meeting.
- Klas puts the requirements in order. That order is the order of Backlog, and the next refill takes from the top of it.
- The team asks the questions collected in the meeting's document under [meetings](meetings/), and the document records the answers.

Any question for Klas that comes up during the week goes into the next meeting's document right away, so nothing is forgotten on Thursday.

### Retrospective

On the Friday of weeks 3, 5, 7, 9 and 11, and once more in week 12, the team holds a retrospective at a time it agrees on. A retrospective is about how the team works, not what it builds: which rules helped, which got in the way, and where cards got stuck.

If a rule in this guide keeps getting in the way, bring it up there. The rules come from [decisions/0018](decisions/0018-kanban-with-weekly-syncs.md), so a rule that changes, changes there first.

## The board

![The whole board, with five columns from Backlog to Done](onboarding/board.png)

Open [the board](https://github.com/users/AlexanderReaper7/projects/1). The tabs along the top are different views of the same cards. **Backlog** is the one to use day to day.

Each column's rule is written at its top:

| Column | What is in it | Who moves cards in |
| --- | --- | --- |
| Backlog | Work that is not ready to start, in the order Klas sets. | GitHub, when an issue is created |
| Ready | Work anyone may start. It has a size, acceptance criteria, and no open question to Klas. Nobody is assigned yet. | The team, when it sizes an item |
| In progress | Work someone is doing right now. | You, when you start |
| In review | Work that is finished, has a linked pull request, and waits for someone else to check it. | You, when you are done |
| Done | Finished and closed. | GitHub, when the issue closes |

The numbers next to a column name count its cards. In progress shows `0 / 12` and In review `0 / 3`: the second number is the limit, explained under [Limits](#limits).

A card shows the repository and issue number, the title, and its fields. The coloured tags are Priority (MUST, SHOULD, COULD) and Size (Small, Medium, Large). A face in the corner is whoever is assigned. A supertask shows a progress bar counting its closed sub-issues.

## A card's trip across the board

This is one work item going from Backlog to Done. It starts as a sub-issue of a supertask, and the supertask closes after it:

![Animation: a sub-issue is created from the supertask's side panel, gets a size, moves to Ready, gets an assignee, moves to In progress and In review, and moves to Done when it is closed. Closing the supertask then moves it to Done too](onboarding/card-journey.gif)

The steps, one at a time:

### 1. Pick the top card in Ready

Take the card at the top of Ready. The order is Klas's, so the top card is the one that matters most right now. Before you take it, check the [limits](#limits): if you already have three cards in progress, finish one first.

If Ready is empty, call a [refill meeting](#refill-meeting). Nobody should start work from Backlog, because those cards are not ready yet: they may be missing a size, or have an open question.

### 2. Open it and assign yourself

Click the card's title. A panel opens on the right with the whole issue.

![The side panel of a card, with the description on the left and the fields on the right](onboarding/card-panel.png)

Read the description and the acceptance criteria. Acceptance criteria are the list of things that must be true when the work is finished. They are how you, and the reviewer, know when you are done. If something is unclear, ask before you start, not after.

Then click **Assign yourself** under Assignees, at the top right of the panel. Your name on the card tells everyone the card is taken.

![The panel after assigning: the assignee is set, Status is Ready, Priority COULD and Size Small](onboarding/assigned.png)

Leave the Size alone. It was set when the card entered Ready and it never changes afterwards, even if the work turns out bigger or smaller. That is on purpose: comparing the size with how long it really took is how the team learns to estimate.

### 3. Drag it to In progress

Close the panel with <kbd>Esc</kbd>, then drag the card from **Ready** into **In progress**. You can also change **Status** in the panel instead of dragging.

The day you move it counts as your first day on it. See [Sizes](#sizes-and-how-long-an-item-may-take) for why that matters.

### 4. Do the work, and tick off what is done

If the work has a branch, link it to the card under **Development** in the side panel, so others can find the code while the card is in progress.

The acceptance criteria are checkboxes. Tick each one when it is true. Anyone looking at the issue then sees how far you are.

![Both acceptance criteria ticked](onboarding/criteria-ticked.png)

If you get stuck, say so the same day. Waiting a day to ask costs the team a day.

### 5. Drag it to In review

When all the criteria are ticked, drag the card to In review. A card in In review must have a pull request linked to it, so link yours first: it shows under **Development** in the card's side panel. Someone else now checks the work. While it waits there, its days stop counting toward the maximum.

If the reviewer finds something to fix, the card goes back to In progress.

### 6. Done happens by itself

When the issue closes, GitHub moves the card to Done, so there is nothing to drag.

A supertask does not close by itself. If you close the last open sub-issue of a supertask, close the supertask too, and GitHub moves it to Done. Until then the supertask's card stays in Backlog, and its progress bar shows how far along it is.

## Sizes, and how long an item may take

Every work item has one of three sizes:

![The Size menu: Small, Medium and Large with their nominal time and maximum](onboarding/size-menu.png)

| Size | Nominal, the time it should take | Maximum, days in progress before a sync |
| --- | --- | --- |
| Small | Half a day | 2 working days |
| Medium | 1 day | 3 working days |
| Large | 3 days | 5 working days |

Time is counted in whole days. A day counts if the card was in In progress at any point that day, and the first and last day both count. Days in In review do not count. So a Small started Tuesday afternoon and moved to review Wednesday morning has used 2 days, which is its maximum and still fine. On Thursday it would be over.

**When a card passes its maximum, the team holds a [sync](#sync).** That is not a punishment. It means the estimate was wrong or something is blocking you, and both are things the team needs to know. The sync is at most 15 minutes, and it ends when the problem has someone who owns it and a next step.

Anything bigger than a Large is not one work item. It becomes a supertask with sub-issues, see [Splitting work](#splitting-work-into-sub-issues).

Work under about an hour usually fits better as a checklist line in the issue it belongs to than as a card of its own.

## Limits

The limits exist so that work gets finished instead of started:

| Limit | Number |
| --- | --- |
| Cards in progress, per person | 3 |
| Allocated nominal days in progress, per person | 3 |
| Cards in review, for the whole team | 3 |

Allocated nominal days are the nominal times of your cards in progress, added up. Two Mediums are 2 days, so you have room for a Small but not a Large.

GitHub shows the team's totals in the column headers, `0 / 12` for In progress and `0 / 3` for In review. It only counts. It does not stop you from dragging a card into a full column, and it does not know about the per-person limits at all. Those are yours to keep. The **My items** view shows your own cards:

![The My items view, filtered to the cards assigned to the viewer](onboarding/my-items.png)

When In review is full, do not start something new. Review someone else's card first. That is the fastest way to get your own card moving again.

A script will check the board every weekday morning and report broken limits and cards past their maximum, as a comment on the issue labelled `board report`. It needs a machine account that does not exist yet, [#30](https://github.com/AlexanderReaper7/teaterihuskvarna/issues/30), so until then nobody checks but you.

## Splitting work into sub-issues

Every requirement is an issue, and most requirements are too big for one work item. The work goes in sub-issues under the requirement's issue. That turns the requirement into a supertask.

To add one, open the parent issue and click **Create sub-issue** under its description:

![An issue with no sub-issues yet, and the Create sub-issue button under its description](onboarding/issue-with-no-sub-issues.png)

Give it a title that says what will exist when it is done, a short description, and acceptance criteria as a checklist:

![The Create sub-issue dialog, filled in with a title, a description and two acceptance criteria](onboarding/create-sub-issue.png)

The parent now lists the sub-issue, and counts how many are closed:

![The parent issue with one sub-issue listed, 0 of 1 closed](onboarding/parent-with-sub-issue.png)

The new sub-issue lands in Backlog on its own. It does not have a size yet. Sizing happens as a team, see the next section.

A good work item:

- is small enough to be a Small, Medium or Large
- has acceptance criteria that someone else can check
- can be reviewed on its own

## How cards get into Ready

Ready is filled at the [week-start sync](#week-start-sync-and-refill), and at a [refill meeting](#refill-meeting) whenever it holds fewer than 4 cards. Both follow the same steps.

Before a card goes to Ready, check its column rule: it has a size, it has acceptance criteria, and it has no open question to Klas.

## The other views

The tabs at the top show the same cards arranged differently.

**Priority board** splits the board into rows by Priority. MUST has to be in version 1, SHOULD ought to be, COULD can wait for phase 2.

![The Priority board, with the MUST row at the top](onboarding/priority-board.png)

**Team items** is a table grouped by Status, handy for scanning everything at once.

**My items** shows only the cards assigned to you.

**Roadmap** shows cards on a timeline.

Typing in **Filter by keyword or by field** narrows any view. Filtering is only for you: GitHub offers a **Save** button afterwards, which changes the view for everyone. Click **Discard** instead.

![The board filtered to the word Example, with Discard and Save buttons at the right](onboarding/filter.png)

## What GitHub does by itself

| When | GitHub |
| --- | --- |
| An issue is opened in this repository | Adds it to the board, in Backlog |
| A sub-issue is added to an issue on the board | Adds the sub-issue to the board |
| An issue closes | Moves its card to Done |
| A card is moved to Done | Closes the issue |
| An issue is reopened | Moves its card back to Backlog |
| A closed card has not changed in four weeks | Archives it, so it leaves the board |
| The last sub-issue of a supertask closes | Nothing. Close the supertask yourself |

## Common mistakes

- **Starting a card from Backlog.** It is not ready. It gets sized at the next refill, and if Ready is empty, call a [refill meeting](#refill-meeting).
- **Forgetting to assign yourself.** Two people then do the same work.
- **Taking a fourth card because the third is waiting on something.** Say what you are waiting on instead. That is what a sync is for.
- **Changing the Size.** It stays what the team agreed, even when it was wrong.
- **Working on a supertask.** Work happens in its sub-issues. A supertask has no size.
- **Leaving a finished supertask open.** When you close its last sub-issue, close the supertask too.
- **Saving a filter.** It changes the view for everyone.
