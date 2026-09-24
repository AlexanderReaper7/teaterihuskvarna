# TODO – Teater i Huskvarna

## Kommandon (snabbstart)

Alla kommandon körs från repots rot (`teaterihuskvarna/`), där `studio/` redan finns.

### Backend – Spring Boot (Maven)

```powershell
cd C:\Users\deltagare\IdeaProjects\teaterihuskvarna

curl https://start.spring.io/starter.zip `
  -d type=maven-project `
  -d language=java `
  -d bootVersion=4.1.1 `
  -d javaVersion=21 `
  -d groupId=se.teaterihuskvarna `
  -d artifactId=backend `
  -d name=backend `
  -d packageName=se.teaterihuskvarna.backend `
  -d dependencies=web,security,data-jpa,postgresql,validation `
  -o backend.zip

Expand-Archive -Path backend.zip -DestinationPath backend
Remove-Item backend.zip

cd backend
mvn clean install
```

Byt `bootVersion` mot den senaste stabila om `4.1.1` hunnit bli inaktuell (kolla https://spring.io/projects/spring-boot innan ni kör). `-d dependencies=...` går att bygga ut senare i `pom.xml` – se TODO-punkten ovan för fler dependencies (t.ex. Spring Security One-Time Token kräver inget extra dependency-namn utöver `security`, det är inbyggt sedan Spring Boot 3.4+).

### Frontend – Next.js + Tailwind + React

Om `web/` redan skapades (enligt Sanitys egen setup-guide, steg 3) räcker det att navigera dit och starta:

```powershell
cd C:\Users\deltagare\IdeaProjects\teaterihuskvarna\web
npm run dev
```

Om `web/` **inte** finns än, skapa den från repots rot med samma flaggor som Sanitys guide anger (Tailwind + TypeScript + App Router ingår):

```powershell
cd C:\Users\deltagare\IdeaProjects\teaterihuskvarna
npx create-next-app@latest web --tailwind --ts --app --src-dir --eslint
cd web
npm install next-sanity
npm run dev
```

`next-sanity` är hjälppaketet som kopplar Next.js mot ert Sanity-projekt (hämtning av innehåll, bildoptimering, m.m.) – behövs oavsett för att `web/` faktiskt ska kunna visa Evenemang/Nyhet/Sida/Erbjudande/Partner från Studio.



- [ ] **Föreningens logga för Studio-favicon**
  Behövs som bild, helst:
    - Kvadratisk (t.ex. 512×512 px eller 256×256 px)
    - Format: `.ico`, `.png` eller `.svg`

  Källa: fråga produktägaren (Klas) om digital högupplöst version, eller kolla om den går att hämta från nuvarande sida (teaterihuskvarna.se).

  Läggs i: `studio/static/favicon.ico` (skriv över standardfilen). Kom ihåg att den publika sidans favicon sätts separat i `web/app/favicon.ico` – båda bör uppdateras för konsekvens.

## Frontend (web/)

- [ ] **Verifiera Tailwind CSS i `web/`-appen (INTE i `studio/`)**
  Tailwind hör hemma i Next.js-frontend, inte i Sanity Studio:
    - Studio bygger visserligen på Vite, men gränssnittet ritas av Sanitys eget designsystem (`@sanity/ui`) – att lägga till Tailwind där behövs inte och kan krocka med befintliga stilar.
    - Kontrollera om `--tailwind`-flaggan redan användes när `web/` skapades (`npx create-next-app@latest web --tailwind ...`, enligt Sanitys egen setup-guide). Om ja: klart. Om nej: lägg till manuellt i `web/` med `npx tailwindcss init`.

## Backend

- [ ] **Init Spring Boot-projektet (Maven)**
  Ska ligga som egen undermapp i samma repo, som syskon till `studio/` och `web/` (enligt "ett gemensamt repo"-tolkningen av projektplanen):
  ```
  teaterihuskvarna/
  ├── backend/   ← Spring Boot (Maven) – detta steg
  ├── studio/    ← Sanity Studio (klart)
  └── web/       ← Next.js-app
  ```
  Kom ihåg från tidigare i projektet:
    - Java-version: matcha senaste LTS vid start (verifiera enligt "Teknikval och motiveringar" i projektplanen)
    - Dependencies: Spring Web, Spring Security (One-Time Token-inloggning), Spring Data JPA, PostgreSQL-driver, Validation
    - `spring-boot-starter-webmvc` (Spring Boot 4-namnet, inte `-web`) om ni kör Spring Boot 4
    - Databas: PostgreSQL, EU-region vid driftsättning (personuppgiftskrav i projektplanen)
    - Token för engångsinloggning ska lagras i databasen, inte bara i minnet (krav i projektplanen under teknikval)
## Resources for backend:
```application.yaml
# noinspection SpringBootApplicationYaml
spring:

  application:
    name: Teaterihuskvarna

  ai:
    retry:
      max-attempts: 1
    openai:
      api-key: ${OPENAI_API_KEY}

      chat:
        model: gpt-4o
        temperature: 0.2
        max-tokens: 1000

    chat:
      memory:
        repository:
          jdbc:
            # Auto-create the chat memory schema on startup.
            # Default mode is "embedded"; "always" forces creation
            # against the configured datasource (PostgreSQL here).
            initialize-schema: always


    datasource:
      url: jdbc:postgresql://localhost:5432/teater_forening_db?createDatabaseIfNotExist=true&useSSL=true&serverTimezone=UTC
      username: postgres
      password: postgres123
      driver-class-name: org.postgresql.Driver

  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: update
      show-sql: true
    properties:
      hibernate:
        format_sql: false

  mail:
    host: localhost
    port: 1025
    properties:
      mail:
        smtp:
          auth: false
          starttls:
            enable: false

# server och logging ska ALDRIG ligga indenterade under "spring:" -
# de är egna toppnivå-nycklar. Flyttade hit, ut ur spring-blocket.
server:
  port: 8080
  spring:
    web:
      error:
        include-stacktrace: always

logging:
  level:
    org.springframework.web: DEBUG

springdoc:
  api-docs:
    path: /v3/api-docs
    enabled: true
  swagger-ui:
    path: /swagger-ui.html
    enabled: true

jwt:
  secret:
    # Hårdkodad hemlighet borttagen - lägg den i en miljövariabel eller
    # secrets-hantering istället, precis som OPENAI_API_KEY nedan.
    key: ${JWT_SECRET_KEY}

banksignering:
  api-user: ${BANKSIGNERING_API_USER}
  password: ${BANKSIGNERING_PASSWORD}
  company-api-guid: ${BANKSIGNERING_COMPANY_API_GUID}

app:
  cors:
    allowed-origins:
      - http://localhost:5173
      - http://localhost:3000
      - http://localhost:1025
    allowed-methods:
      - GET
      - POST
      - PUT
      - DELETE
      - OPTIONS
    allowed-headers:
      - "*"
    allow-credentials: true
```
## Pom.xml
```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    <groupId>se.lexicon</groupId>// TODO:ask if INVID or or lexicon?
    <artifactId>teaterihuskvarna</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>teaterihuskvarna</name>
    <description>teaterihuskvarna</description>
    <url/>
    <licenses>
        <license/>
    </licenses>
    <developers>
        <developer/>
    </developers>
    <scm>
        <connection/>
        <developerConnection/>
        <tag/>
        <url/>
    </scm>
    <properties>
        <java.version>25</java.version>
        <spring-boot-admin.version>4.1.2</spring-boot-admin.version>
        <spring-cloud-services.version>4.4.1</spring-cloud-services.version>
        <spring-cloud.version>2025.1.3</spring-cloud.version>
    </properties>
    <dependencies>
        <!-- ============================================= -->
        <!-- TILLAGDA: saknade grund-dependencies           -->
        <!-- ============================================= -->

        <!-- REST-controllers / @RestController. I Spring Boot 4 heter denna
             webmvc, inte web som i Boot 3. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>

  
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- @Valid / @NotBlank m.fl. på DTO:er -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- ============================================= -->
        <!-- BEFINTLIGA dependencies                        -->
        <!-- ============================================= -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-h2console</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-batch-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security-oauth2-client</artifactId>
        </dependency>
        <dependency>
            <groupId>de.codecentric</groupId>
            <artifactId>spring-boot-admin-starter-client</artifactId>
        </dependency>
        <dependency>
            <groupId>de.codecentric</groupId>
            <artifactId>spring-boot-admin-starter-server</artifactId>
        </dependency>
        <dependency>
            <groupId>io.pivotal.spring.cloud</groupId>
            <artifactId>spring-cloud-services-starter-config-client</artifactId>
        </dependency>
        <dependency>
            <groupId>io.pivotal.spring.cloud</groupId>
            <artifactId>spring-cloud-services-starter-service-registry</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-config-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-config</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway-server-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-stream</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-devtools</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- ============================================= -->
        <!-- TILLAGD: grund-teststarter                     -->
        <!-- ============================================= -->

        <!-- JUnit 5, Mockito, AssertJ m.m. Ni hade specialiserade
             test-starters (batch-jdbc-test, security-test, oauth2-client-test,
             restdocs) men saknade själva grundpaketet de bygger vidare på. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-batch-jdbc-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-restdocs</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security-oauth2-client-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-stream-test-binder</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.restdocs</groupId>
            <artifactId>spring-restdocs-mockmvc</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>de.codecentric</groupId>
                <artifactId>spring-boot-admin-dependencies</artifactId>
                <version>${spring-boot-admin.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>io.pivotal.spring.cloud</groupId>
                <artifactId>spring-cloud-services-dependencies</artifactId>
                <version>${spring-cloud-services.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springdoc</groupId>
                <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
                <version>2.7.0</version>
            </dependency>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-configuration-processor</artifactId>
                <optional>true</optional>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.asciidoctor</groupId>
                <artifactId>asciidoctor-maven-plugin</artifactId>
                <version>2.2.1</version>
                <executions>
                    <execution>
                        <id>generate-docs</id>
                        <phase>prepare-package</phase>
                        <goals>
                            <goal>process-asciidoc</goal>
                        </goals>
                        <configuration>
                            <backend>html</backend>
                            <doctype>book</doctype>
                        </configuration>
                    </execution>
                </executions>
              
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <executions>
                    <execution>
                        <id>default-compile</id>
                        <phase>compile</phase>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                        <configuration>
                            <annotationProcessorPaths>
                                <path>
                                    <groupId>org.projectlombok</groupId>
                                    <artifactId>lombok</artifactId>
                                </path>
                            </annotationProcessorPaths>
                        </configuration>
                    </execution>
                    <execution>
                        <id>default-testCompile</id>
                        <phase>test-compile</phase>
                        <goals>
                            <goal>testCompile</goal>
                        </goals>
                        <configuration>
                            <annotationProcessorPaths>
                                <path>
                                    <groupId>org.projectlombok</groupId>
                                    <artifactId>lombok</artifactId>
                                </path>
                            </annotationProcessorPaths>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

</project>
```