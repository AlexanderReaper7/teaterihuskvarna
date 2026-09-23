# 0016: Passkeys beside login links

2026-09-23

## Decision

Members and administrators may add passkeys and log in with them. A login link still works for everyone, and it is still the only way in for someone who has no passkey or has lost the device. Passkeys are not in the requirement table; the user added them on 2026-09-23.

Spring Security 7.1.1's WebAuthn support does the cryptography and stores the passkeys, with webauthn4j underneath. This project wires Spring's four filters by hand, once per kind of login, and supplies the lookup that turns a passkey into a [SignedIn](../../src/main/java/se/teaterihuskvarna/login/SignedIn.java). The code is in `se.teaterihuskvarna.login`: `PasskeyLogin`, `PasskeyAuthenticationProvider`, `PasskeyConfiguration` and `PasskeyService`.

What a person sees:

- The login pages have a passkey button, and the address field lists passkeys among its autofill suggestions (`autocomplete="email webauthn"`). Picking one logs in.
- `/medlem` and `/admin` list the person's passkeys with a label, the date added and the date last used, and have buttons to add and remove them.
- After a login by link, the page offers once to add a passkey. "Inte nu" hides the offer until the next link login. "Fråga inte igen" stops it on that browser; see "Declining is per browser" below.
- The label is the browser and system, such as "Firefox på Windows", read from the user agent in `static/js/passkey.js`. Nobody types a name.

## The Swedish word

2026-09-23: the Swedish copy says "lösenordsnyckel", plural "lösenordsnycklar". Nobody agrees on a word. On that date Apple's, Google's and Microsoft's Swedish help pages and Chrome's Swedish strings said "nyckel". Firefox's said "lösenordsnyckel". PayPal titled its page "inloggningsnyckel" and said "passkey" in the text, and Säkerhetskollen said "lösennycklar". Swedish banks and Klarna log in with BankID and name no passkey at all.

The user chose "lösenordsnyckel" after that comparison. It matches Firefox, and it names a login key where a bare "nyckel" could mean any key. The cost is that most members' phones and browsers will call the same thing "nyckel" when they save it. "passkey", the English loanword the first version used, appeared in none of the big three's Swedish text. "säkerhetsnyckel" is taken, since Microsoft and Firefox use it for hardware keys.

## Why not `http.webAuthn()`

Spring's configurer puts every chain on the same paths, `/webauthn/**` and `/login/webauthn`, with one user lookup, one success URL and one session attribute. This system has two login kinds on two chains ([0015](0015-login-links-on-spring-security.md), "Two filter chains"), and a member's passkey must not log in on the administrator page. So `PasskeyLogin` builds the same four filters with per-kind paths from `PasskeyUrls` (`/logga-in/passkey/**` and `/medlem/passkeys/**` for members, the same under `/admin` for administrators) and a per-kind session attribute for the challenges.

Three departures from Spring's configurer, each on purpose:

- **Session fixation.** Spring's configurer in 7.1.1 does not give its login filter the chain's `SessionAuthenticationStrategy`, so a passkey login keeps the session id it arrived with. `PasskeyLogin.configure` sets it, and fails at startup if it is missing. `PasskeyIT.aMemberAddsAPasskeyAndLogsInWithIt` checks that the session id changes.
- **Registration after the access rules.** Spring places the filter that hands out registration challenges before `AuthorizationFilter`. There, an anonymous POST would make Spring write an owner row to `user_entities`. Here both registration filters run after the access rules, so only a logged-in person of the right kind reaches them.
- **Removal through a service.** Spring's registration filter also answers DELETE. Here that matcher never matches, and `PasskeyService.remove` does the job, so the page and the REST API remove passkeys the same way ([0014](0014-one-service-layer-two-adapters.md)). Adding a passkey has no service method: the browser's ceremony talks to the filter, as login does in 0015, "The REST adapter shares the filters".

## Who a passkey belongs to

Spring's own provider puts the stored owner record in the session and finds the person through a `UserDetailsService` by name. Every page here expects a `SignedIn`, and the name Spring stores is a principal name such as `member:7`. So `PasskeyAuthenticationProvider` lets Spring check the signature, reads the id out of the name for its own kind only, and asks that kind's `LoginDirectory`. A removed administrator's passkey fails even if a row was somehow left behind.

The browser shows the person the address, or the address with "(administratör)" appended, rather than `member:7`. `PasskeyConfiguration` rewrites the name and display name in the options it sends, and leaves the stored row alone. The suffix is there because one address may hold both a member's and an administrator's passkey, and the browser offers both on either page.

Login is discoverable: the options request carries no address, and the browser offers whichever passkeys it holds for the site. Nothing about the request or the answer depends on whether an address belongs to anyone, which keeps the rule in [projektplan.md](../projektplan.md), "Member login must not reveal membership".

## Spring's tables, and no foreign key

V4 creates `user_entities` and `user_credentials`, copied from the spring-security-web 7.1.1 jar with tabs as spaces, so Flyway owns them like every other table. Upgrading Spring Security means comparing V4 against the new copies, as V3 is for Spring Session.

The timestamps are Spring's `timestamp`, not `timestamptz`. `JdbcUserCredentialRepository` writes `java.sql.Timestamp` in the JVM's zone and reads it back the same way, and the container runs in UTC. Keeping Spring's type keeps V4 a copy of Spring's files, so the comparison on upgrade stays a plain diff.

`user_entities.name` is a principal name that points at either `account` or `administrator`, so it cannot be a foreign key. Nothing in the database removes passkeys when their owner goes. `PasskeyService.removeAll` does, and `AdministratorService.remove` calls it, which `PasskeyIT.removingAnAdministratorRemovesTheirPasskeys` checks. **Whatever later removes an account must call it too.** Nothing removes accounts yet.

The alternative was a table of this project's own with a real foreign key per kind, and a `UserCredentialRepository` written here to read it. That is more code to own for one delete call.

## The relying party is the site address

The relying party id is the host of `teaterihuskvarna.mail.site-url`, the same address login links point at. A passkey belongs to that host name. If the site moves to another domain, every passkey stops working and people log in by link until they add new ones. Nothing breaks beyond that, because links still work.

## Declining is per browser

2026-09-23: "Fråga inte igen" sets a cookie rather than a database field. A passkey lives on one device, so a refusal on a shared computer should not stop the offer on the person's own phone. The user chose this over a column or a table, which would have silenced every device and stored one more fact about the person.

The cookie is `p=1`, `HttpOnly`, `SameSite=Lax`, with `Path` set to the page's own path: `/medlem` or `/admin`. The browser sends it only with requests under that path, so it costs three bytes on those requests and nothing on the rest of the site, and a member's refusal does not touch the administrator page. It is `Secure` when the request came over HTTPS, as the proxy reports it.

It lasts 400 days, the longest Chrome accepts. After that the offer returns once per link login until declined again. `localStorage` was the other per-browser option, rejected because Safari deletes script-written storage after 7 days of browsing without a visit to the site, which a member logging in monthly would hit, and because nothing in the build could test it. EU ePrivacy rules, in Sweden the electronic communications act (LEK), cover the cookie either way. Article 29 Working Party Opinion 04/2012 treats a user interface customisation cookie as exempt from consent, and this is one the person asks for by pressing the button. Whether that holds for a cookie lasting 400 days was not checked against the opinion's text on 2026-09-23; it belongs in the association's privacy review, and the privacy text should mention the cookie either way.

## User verification is preferred, not required

A passkey login asks the authenticator for user verification, a PIN or a fingerprint, but accepts one that answers without it. That is Spring Security 7.1.1's default, `UserVerificationRequirement.PREFERRED`, which `Webauthn4JRelyingPartyOperations` sets for both registration and login. A review on 2026-09-23 pointed out that a stolen security key without a PIN then logs its owner in, administrators included, and suggested `REQUIRED`. The user kept `PREFERRED` the same day. `REQUIRED` would refuse every key without a PIN, including ones already registered, and their owners would fall back to login links.

## Personal data

A passkey row holds a public key, the label (browser and system), when it was added and when it was last used. The last one says when the person last logged in with it. They sit in the same database as the member register and leave only with a backup. The association's record of personal data categories, per [projektplan.md](../projektplan.md), should list them.

## webauthn4j is pinned

spring-security-webauthn 7.1.1 asks for webauthn4j-core 0.31.9.RELEASE, and Spring Boot does not manage the version. `pom.xml` pins 0.31.10.RELEASE, Maven Central's latest on 2026-09-23, with `webauthn4j-test` at the same release for the software authenticator the tests use. A Spring Security upgrade means checking which release it asks for.

## Rejected

- **`http.webAuthn()`.** One set of paths for both kinds; see above.
- **Passkeys for administrators only.** Members gain the most from not waiting for a mail, and the code is the same for both kinds.
- **Remembering "Fråga inte igen" per person.** A column or table would stop the offer on every device, for a key that belongs to one.
- **Typing a label.** An extra step on every add, for a list most people will have one entry in.

## What it costs to undo

Turning passkeys off means removing `PasskeyLogin` from the two chains and the templates' sections; login links carry on unchanged. Dropping the tables takes a new migration. People lose nothing but the shortcut.
