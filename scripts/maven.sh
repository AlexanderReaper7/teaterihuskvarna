#!/bin/sh
# Runs the checked-in Maven wrapper in the pinned JDK container, with the
# docker socket for the integration tests: docs/decisions/0011.
#
#   sh scripts/maven.sh verify
#   sh scripts/maven.sh -Dtest=LoginIT verify
#
# Maven's repository and the wrapper's download live in the named volume
# teaterihuskvarna-cache, shared by every checkout on the machine. Maven 4 locks
# it per artifact with file locks, so parallel builds can share it.
#
# The wrapper's own Maven download, in /cache/.m2/wrapper, is not locked. Two
# builds on an empty volume at the same moment both install it and delete each
# other's files; the Dockerfile's image builds failed that way with
# DirectoryNotEmptyException. Only the very first build on a machine can hit
# it, and running that one alone avoids it. The user accepted the risk on
# 2026-09-26. Keeping the download out of the shared volume would fix it.
set -eu

cd "$(dirname "$0")/.."

# The Dockerfile's jdk stage: the pinned JDK plus git. Rebuilding it is a cache
# hit after the first time.
image=teaterihuskvarna-jdk
docker build -q --target jdk -t "$image" - < Dockerfile > /dev/null
cache=teaterihuskvarna-cache
user=

# The git plugin runs git itself (-Dmaven.gitcommitid.nativegit), so untracked
# files count as uncommitted changes. In a git worktree the plugin (10.0.1)
# resolves .git to the main checkout and runs git there, so GIT_DIR and
# GIT_WORK_TREE point git back at this worktree; git reads them before looking
# at its working directory. The worktree's git directory names the shared one by
# its host path, so that is mounted at the same path. safe.directory is for
# Docker Desktop, where the build runs as root on files git sees as someone
# else's.
common=
worktree=
if [ -f .git ]; then
  common="$(git rev-parse --path-format=absolute --git-common-dir)"
  worktree="$(git rev-parse --path-format=absolute --git-dir)"
fi

# On Linux a bind mount keeps the container's user id, so a build as root
# leaves target/ owned by root and the checkout cannot be deleted without sudo.
# The build runs as the host user instead, in the socket's group so
# Testcontainers can reach docker. The cache volume is handed to that user
# first, and so is anything in target/ an older build left owned by root.
# Docker Desktop on Windows and macOS maps ownership itself, and runs as root
# as before.
if [ "$(uname -s)" = Linux ]; then
  owner="$(id -u):$(id -g)"
  docker run --rm -v "$cache":/cache -v "$PWD":/w "$image" sh -c \
    'chown "$1" /cache && if [ -d /w/target ]; then find /w/target ! -user "${1%:*}" -exec chown "$1" {} +; fi' \
    sh "$owner"
  user="--user $owner --group-add $(stat -Lc %g /var/run/docker.sock)"
fi

# $user is unquoted on purpose: it is either empty or four words.
# shellcheck disable=SC2086
exec docker run --rm --network host $user \
  -e HOME=/cache -v "$cache":/cache \
  -v "$PWD":/w -w /w ${common:+-v} ${common:+"$common:$common"} \
  ${worktree:+-e} ${worktree:+"GIT_DIR=$worktree"} ${worktree:+-e} ${worktree:+GIT_WORK_TREE=/w} \
  -e GIT_CONFIG_COUNT=1 -e GIT_CONFIG_KEY_0=safe.directory -e GIT_CONFIG_VALUE_0='*' \
  -v /var/run/docker.sock:/var/run/docker.sock \
  "$image" ./mvnw -B -ntp -Dmaven.gitcommitid.nativegit=true "$@"
