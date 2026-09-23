# Teater i Huskvarna

Website and member register for the Teater i Huskvarna association. What the system has to do is in [docs/projektplan.md](docs/projektplan.md); why it is built this way is in [docs/decisions/](docs/decisions/).

## Running it

Docker is the only thing that has to be installed. No JDK, no Maven: the build
uses the pinned JDK 25 image and the checked-in Maven wrapper, per
[decisions/0011](docs/decisions/0011-maven-and-the-build-in-a-container.md).

```sh
cp .env.example .env     # local defaults work; fill in credentials when needed
docker compose up -d --build
curl http://localhost:8000/
```

The stack is Caddy on port 8000, the Spring Boot application behind it, and
PostgreSQL 18.2. Flyway applies the migrations in
`src/main/resources/db/migration` at startup.

## Tests

Unit tests need nothing. Integration tests (`*IT`) start a real PostgreSQL
container through Testcontainers, so they need a docker socket.

```sh
docker run --rm --network host \
  -v "$PWD":/w -w /w \
  -v "$HOME/.m2":/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  eclipse-temurin:25.0.4_7-jdk-alpine \
  ./mvnw -B -ntp verify
```

`docker build .` runs the unit tests only. A build stage has no docker daemon,
so the integration tests run here and in CI instead.

## Static analysis

Checkstyle, SpotBugs and PMD all fail the build ([0013](docs/decisions/0013-three-static-analysis-gates.md)).
Checkstyle is bound to `validate`, so a style violation stops the build before
anything compiles and `docker build` runs it too. SpotBugs and PMD need bytecode
and run at `verify`. The `verify` command above is the one that runs all three;
`./mvnw test` runs only Checkstyle.

Doc comments are `///` Markdown (JEP 467). Checkstyle rejects `/** */`, but it
cannot read what is inside a `///` comment, so `@param` and `@return` are a
convention here rather than a gate. 0013 has the measurement and the option that
was costed and not taken.

## How the code is arranged

Every capability is a method on an application service in a domain package such
as `se.teaterihuskvarna.member`. Two adapters sit over it as peers: `web` holds
the JTE controllers, `api` holds the REST endpoints, and both call the same
service method. Anything the site can do, the API can do
([0014](docs/decisions/0014-one-service-layer-two-adapters.md)).

`AdapterRulesTest` fails the build if an adapter reaches a repository or an
entity, if the two adapters start depending on each other, or if a JTE page uses
a service method that no endpoint exposes.

## VS Code

`.vscode/` is checked in. Open the folder and VS Code offers the extensions in
`extensions.json`; none are required.

Every task in `tasks.json` runs through docker, so nothing here needs a JDK or
Maven on the machine. `Ctrl+Shift+B` runs `maven: verify`, the command
`AGENTS.md` requires. The others cover `test`, `compile`, `checkstyle` alone,
the docs check, the compose stack and the image build. Compiler and Checkstyle
messages land in the Problems panel.

The Java language server is the exception: it runs on the host JDK, and this
project targets Java 25. If the host JDK is a different version the editor can
offer APIs the build rejects, and `./mvnw verify` is what catches that.
`.devcontainer/` is the fallback, a pinned JDK 25 with the docker socket passed
through, for when that gap starts costing time.

To debug the running application, pick "Attach to the application" in the Run
view. It starts the stack with `compose.debug.yaml`, which opens JDWP on
`127.0.0.1:5005`. That file is named rather than called `compose.override.yaml`
on purpose, so a plain `docker compose up` never opens a debug port.

## Documentation

`docs/check.py` guards the quoted requirement text, the relative links between
documents and the Mermaid diagrams. CI runs it; see
[.github/workflows/docs.yml](.github/workflows/docs.yml).

## Where things are

| Path | What |
| --- | --- |
| `src/main/java/se/teaterihuskvarna/` | Application code |
| `src/main/jte/` | Templates, compiled to Java classes at build time |
| `src/main/resources/messages_sv.properties` | Fixed Swedish copy ([0001](docs/decisions/0001-language-policy.md)) |
| `src/main/resources/db/migration/` | Flyway migrations ([0010](docs/decisions/0010-flyway-for-migrations.md)) |
| `checkstyle.xml`, `suppressions.xml` | Style ruleset and its exemptions ([0013](docs/decisions/0013-three-static-analysis-gates.md)) |
| `spotbugs-exclude.xml` | SpotBugs exclusions, each with its reasoning beside it |
| `src/test/java/se/teaterihuskvarna/architecture/` | The adapter rules ([0014](docs/decisions/0014-one-service-layer-two-adapters.md)) |
| `.vscode/` | Tasks, launch configurations and extension suggestions |
| `compose.debug.yaml` | JDWP on loopback, loaded only when named |
| `docs/decisions/` | Why the technical choices are what they are |
