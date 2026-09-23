# Two stages, per docs/decisions/0008-everything-in-containers.md: a layered
# build with a pinned JRE base, not spring-boot:build-image, so that Dependabot
# can see the runtime in a FROM line.
#
# The build stage is a plain JDK plus the checked-in wrapper rather than a maven
# image, because Docker Hub's maven image stops at 4.0.0-rc-5 and this project
# pins 4.0.0-rc-6: docs/decisions/0011-maven-and-the-build-in-a-container.md.

FROM eclipse-temurin:25.0.4_7-jdk-alpine AS build
WORKDIR /build

# The git plugin runs git itself here, so that untracked files count as
# uncommitted changes: see the plugin's comment in pom.xml.
RUN apk add --no-cache git

# Dependencies resolve in their own layer, so editing a source file does not
# re-download the world.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline

# The whole worktree, .git included, and not only what the build reads. The git
# plugin records whether the worktree differed from the commit, and a tracked
# file left out of the copy would count as deleted, so every image would claim
# uncommitted changes. .dockerignore therefore lists only paths git ignores.
# The cost is that editing any file, docs included, reruns the build below. The
# build stage is discarded, so none of this reaches the runtime image.
COPY . .
# Unit tests run here. Integration tests (*IT) need a docker daemon this stage
# does not have, so failsafe stays out of the image build.
RUN ./mvnw -B -ntp -Dmaven.gitcommitid.nativegit=true package

FROM eclipse-temurin:25.0.4_7-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app
USER app

COPY --from=build /build/target/*.jar /app/application.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
