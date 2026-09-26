# Two stages, per docs/decisions/0008-everything-in-containers.md: a layered
# build with a pinned JRE base, not spring-boot:build-image, so that Dependabot
# can see the runtime in a FROM line.
#
# The build stage is a plain JDK plus the checked-in wrapper rather than a maven
# image, because Docker Hub's maven image stops at 4.0.0-rc-5 and this project
# pins 4.0.0-rc-6: docs/decisions/0011-maven-and-the-build-in-a-container.md.

# The JDK with git. scripts/maven.sh builds this stage alone and runs Maven in
# it. The git plugin runs git itself, so that untracked files count as
# uncommitted changes (see the plugin's comment in pom.xml) and so that a git
# worktree records its own commit rather than the main checkout's.
FROM eclipse-temurin:25.0.4_7-jdk-alpine AS jdk
RUN apk add --no-cache git

# The repository's git directory, supplied as a named build context:
# `--build-context git=.git`, or additional_contexts in compose.yaml. In a git
# worktree .git is a file pointing outside the checkout, so the directory has
# to come from a second context. This empty stage is the fallback when none is
# given: the git plugin then finds no repository and the build fails, instead
# of COPY --from pulling an image named git from Docker Hub.
FROM scratch AS git

FROM jdk AS build
WORKDIR /build

# The wrapper's Maven download gets its own layer. It is not safe to share: two
# builds installing it into one directory at once delete each other's files.
COPY .mvn/ .mvn/
COPY mvnw ./
RUN ./mvnw -B -v

# The whole worktree, and not only what the build reads. The git
# plugin records whether the worktree differed from the commit, and a tracked
# file left out of the copy would count as deleted, so every image would claim
# uncommitted changes. .dockerignore therefore lists only paths git ignores.
# The cost is that editing any file, docs included, reruns the build below. The
# build stage is discarded, so none of this reaches the runtime image.
COPY . .
# BUILD_GIT_WORKTREE is the worktree's name under .git/worktrees, empty in the
# main checkout; the post-checkout hook writes it to .env. The git plugin
# resolves a worktree to the main checkout and runs git there, so GIT_DIR and
# GIT_WORK_TREE, set on the build below, choose the worktree instead.
ARG BUILD_GIT_WORKTREE=
COPY --from=git . /git
RUN ln -s /git .git
# Unit tests run here. Integration tests (*IT) need a docker daemon this stage
# does not have, so failsafe stays out of the image build. Maven's repository is
# a BuildKit cache mount shared by every image build on the machine, so neither
# a source edit nor a pom.xml change downloads what an earlier build already
# has. Maven 4 locks it per artifact, so parallel builds can share it. The
# cache is in no layer.
RUN --mount=type=cache,id=teaterihuskvarna-m2,target=/root/.m2/repository \
    GIT_DIR="/git${BUILD_GIT_WORKTREE:+/worktrees/$BUILD_GIT_WORKTREE}" GIT_WORK_TREE=/build \
    ./mvnw -B -ntp -Dmaven.gitcommitid.nativegit=true package

FROM eclipse-temurin:25.0.4_7-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app
USER app

COPY --from=build /build/target/*.jar /app/application.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
