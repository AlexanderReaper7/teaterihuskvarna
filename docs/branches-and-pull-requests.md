---
created: 2026-09-27
provenance: unreviewed
description: How to take a work item from its issue to main, step by step, with branches, commits and pull requests, and how to review a teammate's pull request.
---

# Branches, commits and pull requests

Every change reaches `main` through a pull request, and only Alexander merges it. This guide takes one work item from its card to `main`, then covers reviewing someone else's. The rules come from [decisions/0020](decisions/0020-pull-requests-and-merging.md), and the board side of the same journey is in [onboarding](onboarding.md).

Each step shows the terminal command and the same thing in VS Code. The terminal commands work in PowerShell and in Git Bash. In VS Code, the Source Control view is `Ctrl+Shift+G`, and the Command Palette is `Ctrl+Shift+P`.

## Once per clone

1. Run every test and check once, as in [First run](../README.md#first-run). Besides testing, it installs the [git hooks](../README.md#git-hooks) and creates `.env`.
2. Tell git to merge when a pull finds that your branch and GitHub's have both moved. Without this, `git pull` stops with "Need to specify how to reconcile divergent branches":

   ```sh
   git config pull.rebase false
   ```

## 1. Create the branch from the issue

Open the issue of your card. In the right-hand column, under **Development**, choose **Create a branch**. Keep the suggested name, which starts with the issue number, and keep `main` as the source. GitHub then links the branch to the card.

A new branch always starts from `main`, never from your previous branch. After a merge your old branch is finished, and anything built on it drags its commits into the next pull request.

Get the branch on your machine.

In a terminal:

```sh
git fetch
git switch <branch-name>
```

In VS Code: Command Palette, **Git: Fetch**. Then click the branch name at the bottom left of the window and choose `origin/<branch-name>` from the list.

## 2. Commit as you go

Commit whenever something works, even a little. Your branch's commit messages do not reach `main`, so "wip" is fine there. Write them in English, as everything in the repository is ([decisions/0001](decisions/0001-language-policy.md)).

In a terminal:

```sh
git add <files>
git commit -m "<message>"
```

In VS Code: in Source Control, press **+** beside each file to stage it, type the message in the box at the top, and press **Commit**.

Run the tests before you push: `Ctrl+Shift+B` in VS Code, or the command under [Tests](../README.md#tests).

## 3. Push, and open a draft pull request early

In a terminal:

```sh
git push -u origin <branch-name>
```

In VS Code: press **Sync Changes** in Source Control. It sends your commits and brings in any that are on GitHub.

Open the repository on GitHub. It offers **Compare & pull request** for a branch you just pushed. Choose **Create draft pull request** from the arrow beside the green button, then press **Draft pull request**. A draft says "not finished yet", and CI still builds it, so you find out early if something breaks. CI runs only on a branch that has a pull request.

- **Title:** one sentence in English that says what the change does, such as `Show the next performance on the start page`. When Alexander merges, the title becomes the commit on `main`, so it is the one message that has to be good.
- **Description:** the template asks for the issue and how you tested. Keep the line `Closes #<number>` with your issue's number. When the pull request is merged, GitHub closes the issue and the card moves to Done.

The check `pull request / closes-an-issue` fails when the description closes no issue. To fix it, edit the description and add the line. Saving the edit runs the check again.

## 4. Keep your branch up to date

When `main` moves on, bring its changes into your branch by merging.

In a terminal:

```sh
git fetch
git merge origin/main
git push
```

In VS Code: Command Palette, **Git: Fetch**, then **Git: Merge...** and choose `origin/main`. Then **Sync Changes**.

If git reports a conflict, it lists the files where both sides changed the same lines. In each, choose what the code should be:

- In a terminal, open the file and look for the `<<<<<<<`, `=======` and `>>>>>>>` lines. Keep what should stay, delete the markers, then `git add` the file. When all are done, `git commit`.
- In VS Code, the files are under **Merge Changes** in Source Control. Each conflict offers **Accept Current Change**, **Accept Incoming Change** and **Accept Both Changes**, or **Resolve in Merge Editor** for a side-by-side view. Stage each file with **+** when it is done, then **Commit**.

Then run the tests again before you push. A merge without conflicts can still break the build. Ask in a [sync](onboarding.md#sync) if you are unsure which side is right.

Never rebase a branch you have pushed, and never force-push. If a push is refused because GitHub has commits you do not, pull (`git pull`, or **Sync Changes**) and push again.

## 5. Ask for review

When the work is done and CI is green:

1. Press **Ready for review** in the merge box at the bottom of the pull request.
2. Ask a teammate to review it. Alexander merges only after a teammate has approved it, because the customer's plan says every change is reviewed by at least one other student.
3. Move your card to **In review**, as in [onboarding](onboarding.md).

If the reviewer asks for changes, move the card back to **In progress**, commit the changes on the same branch and push. The pull request updates by itself, so do not open a new one. When you are done, move the card to **In review** again and tell the reviewer.

## 6. After the merge

Alexander merges, and GitHub deletes the branch on GitHub. Clean up your machine and start the next item from `main`.

In a terminal:

```sh
git switch main
git pull
git branch -D <branch-name>
```

In VS Code: click the branch name at the bottom left and choose `main`, then **Sync Changes**. Command Palette, **Git: Delete Branch...**, choose your branch, and confirm when VS Code says it is not fully merged.

The branch counts as "not fully merged", and `git branch -d` refuses it, because a merge puts your work on `main` as new commits. That is why the command is `-D`. Nothing is lost: the pull request keeps your commits.

Do not keep committing on a merged branch. The next piece of work gets a new branch, from step 1.

## Reviewing a teammate's pull request

When In review is full, reviewing is the most useful thing you can do, see [Limits](onboarding.md#limits). A review answers two questions: does the change do what its issue asks, and would you be happy to maintain it.

1. Open the pull request and read its issue first. The issue's acceptance criteria are the list the change has to meet.
2. Open the **Files changed** tab and read the whole diff. Check that:
   - every acceptance criterion is met,
   - the tests cover what changed, and CI is green,
   - names and comments make sense to someone who did not write them,
   - nothing real is in it: no passwords, no real member data. Development uses made-up test data only.
3. When the change shows in the app, try it. Fetch and switch to the branch as in [step 1](#1-create-the-branch-from-the-issue), and start the stack as in [README.md](../README.md). Do not commit on someone else's branch.
4. Comment on a line with the comment button that appears beside the line number when you hover over the line. Say what is wrong and why, and suggest what to do instead. A question is a fine comment.
5. Finish with **Review changes** at the top of **Files changed**, choose one of the following, and press **Submit review**:
   - **Approve** when you would merge it as it is.
   - **Request changes** when something must change first. The author moves the card back to In progress.
   - **Comment** when you have questions but no verdict yet.

Never press the merge button, even though GitHub shows it to you. Alexander merges after your approval.

## When there is no issue

Work without an issue is rare. Such a pull request gets the label `no-issue`, which passes the check. Ask Alexander before you use it.
