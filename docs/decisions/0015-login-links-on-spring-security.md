# 0015: Login links on Spring Security

2026-09-23

## Decision

Members and administrators log in with Spring Security 7.1.1's one-time-token login. The flow is Spring's: the filter that takes an address and generates a token, the filter that takes a token and logs in, the session, CSRF and the access rules. This project supplies the parts that Spring's defaults get wrong for it: a token store that keeps hashes, a lookup that says nothing about unknown addresses, a rate limit, and mail sent after the response. They live in `se.teaterihuskvarna.login`.

Sessions live in PostgreSQL through Spring Session JDBC 4.1.1. Flyway owns every table involved: V2 for tokens and rate limit rows, V3 for sessions.

The rules this has to meet are in [projektplan.md](../projektplan.md), "Member login must not reveal membership".

Passkeys are a second way in, beside the link, and have their own record: [0016](0016-passkeys-beside-links.md).

## Why Spring's flow and not one written here

The user does not want to own authentication code. A hand-rolled flow means writing and maintaining the session fixation defence, the CSRF check on the link, the security context handling and the logout, all of which Spring Security already ships and tests. What remains here is a token store and some glue, which is small enough to read in one sitting.

## The store keeps hashes, not tokens

`one_time_token.token_hash` holds the SHA-256 of the token, never the token. Someone who reads the table or a backup gets no link that works.

The hash is unsalted and fast, on purpose. Salts and slow hashes defend secrets people can guess, such as passwords. A token is 256 random bits from `SecureRandom` (`Tokens.newToken`), so there is nothing to guess, and the hash only has to be one way.

A token is consumed with one `DELETE ... RETURNING`, so of two concurrent clicks on the same link exactly one gets a row back. One statement leaves no gap between the check and the delete to reason about.

## Known and unknown addresses look the same

Spring's `GenerateOneTimeTokenFilter` passes whatever the token service returns straight to the success handler, without a null check (read from the 7.1.1 bytecode). So the token service always returns a token, one that is never stored or sent. The success handler redirects to the same "link sent" page in both cases.

The request thread does only what every request does: the CSRF check, the rate limit insert, the redirect. The directory lookup, the token insert and the mail run afterwards on a background executor (`Background`, then `Mailer`). An SMTP round trip on the request thread would make a known address answer measurably slower than an unknown one, and so would the token insert, on a smaller scale. The membership application form works the same way: after validation and the rate limit, the account lookup, the application upsert and the mail all run in the background.

The link in the mail opens a page with a form that POSTs the token. Mail scanners follow GET links to check them, and a link that logged in on GET would be spent before its owner clicked it.

2026-09-23: a script on that page, `static/js/login-link.js`, submits the form as soon as the page loads, so a person who follows the link is logged in without pressing anything. The button stays for browsers without JavaScript. This keeps out scanners that only fetch the HTML. Some gateways open links in a headless browser that runs JavaScript, and those will still spend the token; for such a mailbox the page behaves as if the server logged in on GET. Logging in on GET was rejected for that reason: it would lose every scanned mailbox rather than only those. The script is a file rather than inline, so a later Content-Security-Policy need not allow inline script.

## Sessions in PostgreSQL

A member session lasts 30 days. In memory, every deploy would end it. Spring Session JDBC keeps sessions in `SPRING_SESSION`, so they survive a restart.

`spring.session.jdbc.initialize-schema` is `never`, and V3 creates the tables instead. V3 is `schema-postgresql.sql` from the spring-session-jdbc 4.1.1 jar, and on 2026-09-23 it was identical to that file apart from its header comment and tabs turned to spaces. Upgrading Spring Session means comparing the two again.

`SPRING_SESSION.PRINCIPAL_NAME` is indexed and holds `SignedIn.getUsername()`, such as `administrator:7`. Removing an administrator deletes every session under that name, so the removal takes effect at once rather than when the 8 hours run out.

## Two filter chains

One address may belong to both an account and an administrator account, so one lookup cannot decide who is logging in. Each login kind has its own filter chain, token service and authentication provider, under `/logga-in` and `/admin/logga-in`. The token row records its kind, and a member token cannot log in on the administrator page.

Both chains keep the security context in the same session attribute, so a browser holds one login at a time. Logging in as the other kind replaces the first.

A page the login does not reach, such as `/admin` for a member, sends the person to that chain's login page, where an anonymous visitor goes too. Spring's default is a 403, which showed the Whitelabel error page; `RefusedRequests` replaced it on 2026-09-23. Under `/api/` it stays 403.

## A new session row at login

Logging in moves the session to a new row (`migrateSession`), rather than giving the same row a new id, which is Spring's default (`changeSessionId`). Changed on 2026-09-23, after the e2e suite lost logins. Spring Session JDBC 4.1.1 saves a session by its row and writes the id with it, so a request that loaded the session before the login and saved after it put the old id back, and the browser's new cookie found nothing. Static files load the session too, and the link page submits while its fonts are still loading, so this hit the first visit from a phone: 17 of 20 attempts in the e2e test. With a new row, the late save finds no row to update. The cost is copying the session's attributes at every login, a few rows.

## The REST adapter shares the filters

[0014](0014-one-service-layer-two-adapters.md) requires that anything the site can do, the API can do. Login is the one capability that is not a service method: the filters are the login. So the API has no login endpoint of its own. An API client fetches a CSRF token from `GET /api/csrf` and posts form data to the same URLs the pages post to. Parity comes from sharing the filter, not from a second endpoint that would have to stay in step with it.

## The rate limit answers like a success

A filter in front of the token filter counts link requests per address and per client IP address in `link_request`. Over the limit, it answers with the same redirect a successful request gets and sends nothing. A distinct error would tell a caller the address had been asked for recently, which is itself a hint that it exists.

The client IP address is the one Traefik forwards: `server.forward-headers-strategy: native` in `application.yaml`, with the reasoning beside it.

## Rejected

- **Spring's `JdbcOneTimeTokenService`.** It stores the token itself in `one_time_tokens.token_value`: its insert is `INSERT INTO one_time_tokens (token_value, username, expires_at) VALUES (?, ?, ?)`, and the value is `UUID.randomUUID().toString()` (read from the spring-security-core 7.1.1 bytecode on 2026-09-23). A read of the database or a backup would yield working links for every unexpired token. It also keys tokens by username alone, with no room for two login kinds.
- **A token filter written here.** It is the same work as the flow above with none of Spring's tests behind it.
- **Magic links from an external identity provider.** That is another processor holding the member register's addresses, with its own processor terms, per the personal data section of [projektplan.md](../projektplan.md). It buys nothing Spring's filter does not already do.
- **In-memory sessions.** A deploy would log everyone out, and a 30-day member session would last until the next push to `main`.

## What it costs to undo

Swapping the token store for Spring's means a table rename and accepting plaintext tokens. Going back to in-memory sessions means deleting one dependency and V3's tables through a new migration. Dropping Spring's flow for one written here means owning everything listed under "Why Spring's flow".
