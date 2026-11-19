#!/bin/sh
# Fails when two pins of one image disagree. Dependabot raises the FROM lines in
# the Dockerfile but cannot see the Windows tasks in .vscode/tasks.json, which
# run the same JDK image directly. It raises the PostgreSQL image in
# compose.yaml but cannot see the one the integration tests start, which has to
# be the image production runs: docs/decisions/0007-postgres-in-a-container.md.
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

tests=src/test/java/se/teaterihuskvarna/PostgresTestConfiguration.java
compose=$(sed -n 's/^ *image: *\(postgres:[^ ]*\)$/\1/p' compose.yaml)
test=$(grep -o '"postgres:[^"]*"' "$tests" | tr -d '"')
if [ -z "$compose" ] || [ -z "$test" ]; then
  echo "compose.yaml or $tests: no postgres image found" >&2
  status=1
elif [ "$compose" != "$test" ]; then
  echo "$tests: $test, but compose.yaml runs $compose" >&2
  status=1
fi
exit $status
