#!/bin/sh
# Merges Dependabot's grouped minor and patch pull requests, one per run, and
# asks Dependabot to rebase the next one. Run by
# .github/workflows/dependabot-merge.yml on main. The user's decision and the
# agent's reasons are in docs/decisions/0020-pull-requests-and-merging.md.
#
#   sh scripts/dependabot-merge.sh             merge and request rebases
#   sh scripts/dependabot-merge.sh --dry-run   only say what it would do
#
# GH_TOKEN reads. MERGE_TOKEN is the owner's token, and is used for exactly two
# calls: the merge, and the comment that asks Dependabot to rebase. A dry run
# needs no MERGE_TOKEN.
#
# A pull request is merged only when all of this holds:
#
# - Dependabot opened it, from a branch of this repository whose name ends in
#   a group called <something>-minor-and-patch. A major update comes alone,
#   and the pmd and temurin groups have other names, so those stay open for
#   the owner.
# - Every commit on it is Dependabot's own: authored by dependabot[bot],
#   committed by GitHub (web-flow) and signed by GitHub. Anyone with write
#   access can push to a Dependabot branch, and a commit can name any author,
#   but only GitHub makes a commit with that committer and a valid signature.
# - It has no conflict and is up to date with the base branch, which the
#   ruleset on main requires of everyone else too.
# - Every check the ruleset requires has passed on its newest commit, and no
#   other check on that commit has failed or is still running.
#
# The owner's token can bypass the rulesets, so this script is what stands in
# for them. It reads the required checks from the ruleset and merges nothing
# if it finds none.
#
# One merge leaves every other pull request behind main. Asking Dependabot to
# rebase them all would build each of them again after every merge, so one is
# asked at a time, and none while another is up to date and waiting for its
# checks.
set -eu

DRY_RUN=false
if [ "${1:-}" = "--dry-run" ]; then
    DRY_RUN=true
fi

REPO=${GITHUB_REPOSITORY:-$(gh repo view --json nameWithOwner --jq .nameWithOwner)}
OWNER=${REPO%/*}
NAME=${REPO#*/}
GROUP='^dependabot/.+-minor-and-patch-[0-9a-f]+$'
# A request Dependabot has not acted on is asked again after this long.
REBASE_PATIENCE_SECONDS=$((6 * 60 * 60))

say() {
    echo "$*"
    if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
        echo "- $*" >> "$GITHUB_STEP_SUMMARY"
    fi
}

as_owner() {
    if [ -z "${MERGE_TOKEN:-}" ]; then
        echo "::error::MERGE_TOKEN is empty, so nothing can be merged or rebased." >&2
        exit 1
    fi
    GH_TOKEN=$MERGE_TOKEN "$@"
}

BASE=$(gh api "repos/$REPO" --jq .default_branch)
REQUIRED=$(gh api "repos/$REPO/rules/branches/$BASE" \
    --jq '[.[] | select(.type == "required_status_checks") | .parameters.required_status_checks[].context] | unique')
if [ "$(echo "$REQUIRED" | jq length)" -eq 0 ]; then
    echo "::error::The rulesets on $BASE name no required check, so nothing is merged." >&2
    exit 1
fi

# The $ names are GraphQL variables, not shell ones.
# shellcheck disable=SC2016
PULLS=$(gh api graphql -F owner="$OWNER" -F name="$NAME" -F base="$BASE" -f query='
    query($owner: String!, $name: String!, $base: String!) {
      repository(owner: $owner, name: $name) {
        pullRequests(states: OPEN, baseRefName: $base, first: 50, orderBy: {field: CREATED_AT, direction: ASC}) {
          nodes {
            number title isDraft isCrossRepository mergeable headRefName headRefOid
            author { login __typename }
            comments(last: 50) { nodes { body createdAt } }
          }
        }
      }
    }' --jq '[.data.repository.pullRequests.nodes[]
        | select(.author.__typename == "Bot" and .author.login == "dependabot")]')

# Prints one word for a pull request, then a reason: skip, wait, behind or ready.
judge() {
    pull=$1
    number=$(echo "$pull" | jq -r .number)
    head=$(echo "$pull" | jq -r .headRefOid)

    if ! echo "$pull" | jq -r .headRefName | grep -Eq "$GROUP"; then
        echo "skip not a minor-and-patch group"
        return
    fi
    if [ "$(echo "$pull" | jq '.isCrossRepository or .isDraft')" = true ]; then
        echo "skip a draft, or from another repository"
        return
    fi

    foreign=$(gh api "repos/$REPO/pulls/$number/commits" --paginate --jq '[.[]
        | select((.author.login == "dependabot[bot]" and .committer.login == "web-flow"
            and .commit.verification.verified == true and .commit.verification.reason == "valid") | not)
        | .sha[0:7]] | join(" ")')
    if [ -n "$foreign" ]; then
        echo "skip holds commits that are not Dependabot's: $foreign"
        return
    fi

    # Before the question of conflicts: a rebase is the answer to both, and
    # GitHub works out whether a pull request merges cleanly only when asked,
    # so the first answer is often that it does not know yet.
    if [ "$(gh api "repos/$REPO/compare/$BASE...$head" --jq .behind_by)" -gt 0 ]; then
        echo "behind $BASE"
        return
    fi

    case $(echo "$pull" | jq -r .mergeable) in
        MERGEABLE) ;;
        CONFLICTING)
            echo "skip conflicts with $BASE"
            return ;;
        *)
            echo "wait GitHub has not worked out whether it merges cleanly"
            return ;;
    esac

    runs=$(gh api "repos/$REPO/commits/$head/check-runs?filter=latest&per_page=100" --paginate \
        --jq '.check_runs[] | {name, status, conclusion}' | jq -s .)
    verdict=$(jq -rn --argjson runs "$runs" --argjson required "$REQUIRED" '
        ($required - [$runs[] | select(.conclusion == "success") | .name]) as $missing
        | [$runs[] | select(.status != "completed") | .name] as $running
        | [$runs[] | select(.status == "completed"
            and (.conclusion | IN("success", "skipped", "neutral") | not)) | .name] as $failed
        | if ($failed | length) > 0 then "skip failed checks: \($failed | unique | join(", "))"
          elif ($running | length) > 0 then "wait checks still running: \($running | unique | join(", "))"
          elif ($missing | length) > 0 then "wait required checks not reported yet: \($missing | join(", "))"
          else "ready"
          end')
    echo "$verdict"
}

# Whether a rebase was asked for after the newest commit, and not too long ago.
rebase_pending() {
    pull=$1
    number=$(echo "$pull" | jq -r .number)
    head=$(echo "$pull" | jq -r .headRefOid)
    pushed=$(gh api "repos/$REPO/commits/$head" --jq .commit.committer.date)
    echo "$pull" | jq -e --arg pushed "$pushed" --argjson patience "$REBASE_PATIENCE_SECONDS" '
        [.comments.nodes[] | select(.body | startswith("@dependabot rebase")) | .createdAt | fromdateiso8601]
        | max as $asked
        | $asked != null and $asked > ($pushed | fromdateiso8601) and (now - $asked) < $patience' > /dev/null
}

count=$(echo "$PULLS" | jq length)
say "$count open pull requests from Dependabot, required checks: $(echo "$REQUIRED" | jq -r 'join(", ")')"

ready=""
waiting=false
behind=""
# Every pull request that is Dependabot's own and has no failure, in order.
eligible=""
index=0
while [ "$index" -lt "$count" ]; do
    pull=$(echo "$PULLS" | jq -c ".[$index]")
    index=$((index + 1))
    number=$(echo "$pull" | jq -r .number)
    verdict=$(judge "$pull")
    say "#$number $(echo "$pull" | jq -r .title): $verdict"
    case $verdict in
        ready)
            eligible="$eligible $number"
            [ -n "$ready" ] || ready=$number ;;
        wait*)
            eligible="$eligible $number"
            waiting=true ;;
        behind*)
            eligible="$eligible $number"
            behind="$behind $number" ;;
    esac
done

acted=false
rebase=""
if [ -n "$ready" ]; then
    head=$(echo "$PULLS" | jq -r ".[] | select(.number == $ready) | .headRefOid")
    acted=true
    if [ "$DRY_RUN" = true ]; then
        say "Would squash-merge #$ready at $head."
    else
        as_owner gh api -X PUT "repos/$REPO/pulls/$ready/merge" -f merge_method=squash -f sha="$head" \
            --jq '"Merged: \(.merged), \(.sha)"'
        say "Squash-merged #$ready at $head."
    fi
    # The merge leaves every other one behind, whatever it was a moment ago.
    for number in $eligible; do
        if [ "$number" != "$ready" ]; then
            rebase=$number
            break
        fi
    done
elif [ "$waiting" = false ]; then
    rebase=$(echo "$behind" | awk '{print $1}')
fi

if [ -n "$rebase" ]; then
    pull=$(echo "$PULLS" | jq -c ".[] | select(.number == $rebase)")
    if rebase_pending "$pull"; then
        say "#$rebase has been asked to rebase and has not moved yet."
    else
        acted=true
        if [ "$DRY_RUN" = true ]; then
            say "Would ask Dependabot to rebase #$rebase."
        else
            as_owner gh api -X POST "repos/$REPO/issues/$rebase/comments" -f body="@dependabot rebase" --jq .html_url
            say "Asked Dependabot to rebase #$rebase."
        fi
    fi
fi

if [ -n "${GITHUB_OUTPUT:-}" ]; then
    echo "acted=$acted" >> "$GITHUB_OUTPUT"
fi
if [ "$acted" = false ]; then
    say "Nothing to do."
fi
