#!/bin/sh
# Fails when a pinned Temurin version disagrees with another. Dependabot raises
# the FROM lines in the Dockerfile but cannot see the Windows tasks in
# .vscode/tasks.json, which run the same JDK image directly.
set -eu
cd "$(dirname "$0")/.."

jdk=$(sed -n 's/^FROM eclipse-temurin:\([^ ]*\)-jdk-alpine .*/\1/p' Dockerfile)
jre=$(sed -n 's/^FROM eclipse-temurin:\([^ ]*\)-jre-alpine .*/\1/p' Dockerfile)
if [ -z "$jdk" ] || [ -z "$jre" ]; then
  echo "Dockerfile: no eclipse-temurin JDK or JRE FROM line found" >&2
  exit 1
fi
status=0
if [ "$jdk" != "$jre" ]; then
  echo "Dockerfile: the JDK is $jdk but the JRE is $jre" >&2
  status=1
fi
for tag in $(grep -o 'eclipse-temurin:[^"]*' .vscode/tasks.json | sort -u); do
  if [ "$tag" != "eclipse-temurin:$jdk-jdk-alpine" ]; then
    echo ".vscode/tasks.json: $tag, but the Dockerfile builds with eclipse-temurin:$jdk-jdk-alpine" >&2
    status=1
  fi
done
exit $status
