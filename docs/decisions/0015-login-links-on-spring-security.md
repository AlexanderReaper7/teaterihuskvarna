# 0015: Login links on Spring Security

2026-09-23

## Decision

Members and administrators log in with Spring Security 7.1.1's one-time-token login. The flow is Spring's: the filter that takes an address and generates a token, the filter that takes a token and logs in, the session, CSRF and the access rules. This project supplies the parts that Spring's defaults get wrong for it: a token store that keeps hashes, a lookup that says nothing about unknown addresses, a rate limit, mail sent after the response, and a cookie that makes a link work only in the browser that asked for it. They live in `se.teaterihuskvarna.login`.

Sessions live in PostgreSQL through Spring Session JDBC 4.1.1. Flyway owns every table involved: V2 for tokens and rate limit rows, V3 for sessions, V5 for binding tokens to a browser.

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

## A link works only in the browser that asked for it

Since 2026-09-23. Before that, anyone could ask for a link to their own address and send it to someone else, in a mail or a chat message. The other person would click it and be logged in as the sender, and anything they then typed, such as their own contact details, would land in the sender's account. That is login CSRF. A review raised it that day, and the user rejected fixes that depend on the person reading the page carefully: nobody checks whose name is in the corner before typing.

Asking for a link sets a cookie, `login-browser` (`LoginBrowser`, `LoginBrowserFilter`), and the token row stores its SHA-256 in `browser_hash`. The POST that redeems a link has to carry the same cookie, or it redeems nothing and leaves the row alone, so the link still works where it was asked for. The cookie is a random value of 256 bits, like a token. A browser that asks again keeps its value, so its older links keep working. Every request for a link gets the cookie, whether the address is known, unknown or over the rate limit, so the response still does not tell them apart.

The cost is the person who asks on a laptop and reads the mail on a phone. That includes an iPhone whose mail program opens links in its own browser. So the mail also carries a six digit code, which the person types on the "link sent" page that the asking browser is still showing. The code is bound to the browser in the same way, its hash is in `code_hash`, and redeeming it deletes the row, just as the link does. Opening the link in another browser shows a page that says to type the code there or to ask for a new link here (`login/elsewhere.jte`), rather than a button that would fail.

The mail says, next to the code: "Ge aldrig koden till någon. Föreningen frågar aldrig efter den." A code brings a risk the link did not have: someone can phone the person and ask them to read it out, the usual scam with WhatsApp codes. That attacker needs the person's help. The link attack needed nothing but a click.

How other services handle it, looked up on 2026-09-23:

- [Supabase's PKCE flow](https://supabase.com/docs/guides/auth/sessions/pkce-flow) keeps a secret in the browser that asked, and its [passwordless docs](https://supabase.com/docs/guides/auth/auth-email-passwordless) say the link works only there. [Its users report](https://github.com/orgs/supabase/discussions/15708) the cost on phones whose mail program opens links in its own browser.
- [Clerk](https://clerk.com/docs/guides/secure/best-practices/protect-email-links) calls it "Require the same device and browser" and turns it on by default.
- [Auth0's email magic link](https://auth0.com/docs/authenticate/passwordless/authentication-methods/email-magic-link) requires the request and the link to happen in the same browser.
- [Slack](https://slack.com/help/articles/212681477-Sign-in-to-Slack) mails a code that is typed where the person asked. A code alone would meet the threat as well, but requirement I1 asks for "engångslänk i e-post".

This design uses both: a link bound to the browser, and a code for every other case. No large site that sends both in the same mail was found, and none was looked for beyond these four.

### How strong the code is

A code allows five tries (`HashedTokenService.CODE_TRIES`), and a wrong code does not spend the link. The tries are counted in the `UPDATE` that finds the rows, so guesses sent at once cannot get more than five between them. `LoginIT.codeGuessesSentAtOnceStillStopAtFive` sends 20 at once. It was checked to fail when the count moved to a separate statement, with a 50 ms pause added between the two statements to widen the race.

An attacker who guesses from their own browser can ask for 5 links an hour to a victim's address (`requests-per-address`), with 5 tries each: 25 guesses an hour against a million codes. That is 1 in 40 000 per hour, about 1.8% over 30 days and about 20% over a year of guessing without pause. The victim gets 120 mails a day meanwhile, so the attack is loud. Eight digits would bring the year down to about 0.2%. The user kept six digits on 2026-09-23 with these numbers in front of them.

### Limits

- **The code's timing.** The code is checked on the request thread, and the `UPDATE` finds a row only if this browser asked for a link to a known address. A browser that asked about an unknown address gets the same redirect, a fraction of a millisecond sooner. The difference was not measured. Each sample costs a link request, which the rate limit caps at five an hour per address, and the difference sits under the noise of an internet round trip.
- **Cookies set from a sibling host.** A page on another host under the same domain, such as an old WordPress site on `www.` while the application runs elsewhere, can set a `login-browser` cookie for the parent domain with the same path. It could plant the attacker's value in the victim's browser and bring the attack back. The `__Host-` name prefix stops that, but it requires `Path=/` and `Secure`, so the cookie would go with every request on the site and not at all over plain HTTP in development. The cookie uses the login page's path instead, the way the passkey offer cookie does in [0016](0016-passkeys-beside-links.md). Whether any sibling host will exist depends on the open question about the WordPress site in [open-questions.md](../open-questions.md).
- **Consent.** The cookie is set when the person asks to log in, is needed to log in, and lasts as long as a link, one hour. Article 29 Working Party Opinion 04/2012 lists authentication cookies among those exempt from consent. The privacy text should still name it.
- **The REST adapter.** An API client has to keep the cookie from the request for a link to the POST that logs in, as a browser does.

### Bli medlem from an address that already has an account

Before this change, the form mailed such an address a login link. The form sets no `login-browser` cookie, so that link would be bound to nobody, and it cannot be bound to the browser that filled in the form without reopening the attack: anyone could apply with their own address and pass the link on. Since 2026-09-23 the mail says the address already belongs to a member and links to the plain login page, `/logga-in`, with no token. The user chose this that day. The cost is one extra step for a member who applied again: type the address on the login page.

## Sessions in PostgreSQL

A member session lasts 30 days. In memory, every deploy would end it. Spring Session JDBC keeps sessions in `SPRING_SESSION`, so they survive a restart.

`spring.session.jdbc.initialize-schema` is `never`, and V3 creates the tables instead. V3 is `schema-postgresql.sql` from the spring-session-jdbc 4.1.1 jar, and on 2026-09-23 it was identical to that file apart from its header comment and tabs turned to spaces. Upgrading Spring Session means comparing the two again.

`SPRING_SESSION.PRINCIPAL_NAME` is indexed and holds `SignedIn.getUsername()`, such as `administrator:7`. Removing an administrator deletes every session under that name, so the removal takes effect at once rather than when the 8 hours run out.

That delete misses one session. A login that looked the administrator up just before the removal committed writes its session row just after the delete, and the row works for 8 hours. So since 2026-09-23 every request in the administrator chain also asks whether the administrator is still active, and deletes the session if not (`ActiveLoginFilter`). The user chose this after a review that day. The other fix considered was deleting the sessions after the removal commits, which narrows the window without closing it.

The cost was measured the same day on the e2e stack, 5 000 sequential keep-alive requests after 2 000 warm-up, two runs per build. Median milliseconds without the check, then with it:

| Path | Without | With |
| --- | --- | --- |
| `/admin` | 1.192, 1.130 | 1.203, 1.115 |
| `/api/admin/administrators` | 1.009, 1.004 | 0.964, 1.060 |

The difference is smaller than the spread between two runs of the same build. By itself, the lookup (`removed_at IS NULL` by primary key) took 0.014 ms in `pgbench`. Spring Session's own load of the session takes 0.023 ms. Members get no such check, because nothing removes an account yet. Whatever later removes one should add the filter to the member chain.

## Two filter chains

One address may belong to both an account and an administrator account, so one lookup cannot decide who is logging in. Each login kind has its own filter chain, token service and authentication provider, under `/logga-in` and `/admin/logga-in`. The token row records its kind, and a member token cannot log in on the administrator page.

Both chains keep the security context in the same session attribute, so a browser holds one login at a time. Logging in as the other kind replaces the first.

A page the login does not reach, such as `/admin` for a member, sends the person to that chain's login page, where an anonymous visitor goes too. Spring's default is a 403, which showed the Whitelabel error page; `RefusedRequests` replaced it on 2026-09-23. Under `/api/` it stays 403. The administrator login page says it is for administrators and links to the member one, because that is where a member lands. It does not check who is logged in: the user asked on 2026-09-23 only that the page make clear whose it is.

A path no rule mentions, such as `/finns-inte`, answers 404 for everyone, anonymous or logged in, since 2026-09-23. Before that it sent the person to log in, which cannot help: no login reaches a page that does not exist. The last rule still denies the request, so the plan's "deny unless a rule grants it" holds and a controller added later stays shut until a rule names it. The rule marks the request as it denies it, and the refusal handlers answer a marked request with 404 (`UnknownPaths`). That keeps the rules the only list of paths. Under `/api/` the 404 has no body, as the 401 there has none. The cost is that anyone can tell an unknown path from one behind a login, which the plan does not forbid. Browsers see one error page, for this 404 and for every other error (`ErrorPage`), in place of Whitelabel.

A form whose CSRF token no longer matches a session, because the browser restarted or the session timed out while the page stayed open, goes back to its page with a line asking the person to send it again. The address they typed is lost. The user kept it that way on 2026-09-23 over two ways to keep the address. Carrying it in the redirect puts an address in the URL, and so in browser history and access logs. Dropping the CSRF check on the two forms that ask for a link would make an exception to a rule every other form follows. Typing an address again after a browser restart costs less than either.

## A new session row at login

Logging in moves the session to a new row (`migrateSession`), rather than giving the same row a new id, which is Spring's default (`changeSessionId`). Changed on 2026-09-23, after the e2e suite lost logins. Spring Session JDBC 4.1.1 saves a session by its row and writes the id with it, so a request that loaded the session before the login and saved after it put the old id back, and the browser's new cookie found nothing. Static files load the session too, and the link page submits while its fonts are still loading, so this hit the first visit from a phone: 17 of 20 attempts in the e2e test. With a new row, the late save finds no row to update. The cost is copying the session's attributes at every login, a few rows.

Spring's request cache is off in both chains since 2026-09-23. It saves a refused anonymous request in the session, to go back to after login, but `LoginSuccessHandler` always goes to the chain's own page and never reads it. Each save still wrote a session row, so every refused request, a bot's 404s included, left one in the database until it expired. Going back to the page someone first asked for would need that cache, and turning it on again means `LoginSuccessHandler` reading it too.

## The REST adapter shares the filters

[0014](0014-one-service-layer-two-adapters.md) requires that anything the site can do, the API can do. Login is the one capability that is not a service method: the filters are the login. So the API has no login endpoint of its own. An API client fetches a CSRF token from `GET /api/csrf` and posts form data to the same URLs the pages post to. Parity comes from sharing the filter, not from a second endpoint that would have to stay in step with it. The client keeps the `login-browser` cookie between the two POSTs, as a browser does ("A link works only in the browser that asked for it").

## The rate limit answers like a success

A filter in front of the token filter counts link requests per address and per client IP address in `link_request`. Over the limit, it answers with the same redirect a successful request gets and sends nothing. A distinct error would tell a caller the address had been asked for recently, which is itself a hint that it exists.

The client IP address is the one Traefik forwards: `server.forward-headers-strategy: native` in `application.yaml`, with the reasoning beside it.

## Rejected

- **Spring's `JdbcOneTimeTokenService`.** It stores the token itself in `one_time_tokens.token_value`: its insert is `INSERT INTO one_time_tokens (token_value, username, expires_at) VALUES (?, ?, ?)`, and the value is `UUID.randomUUID().toString()` (read from the spring-security-core 7.1.1 bytecode on 2026-09-23). A read of the database or a backup would yield working links for every unexpired token. It also keys tokens by username alone, with no room for two login kinds.
- **A token filter written here.** It is the same work as the flow above with none of Spring's tests behind it.
- **Magic links from an external identity provider.** That is another processor holding the member register's addresses, with its own processor terms, per the personal data section of [projektplan.md](../projektplan.md). It buys nothing Spring's filter does not already do.
- **In-memory sessions.** A deploy would log everyone out, and a 30-day member session would last until the next push to `main`.
- **A link that approves the browser that asked.** Some flows log in the waiting tab once the link is opened anywhere, which covers laptop to phone for free. It turns login CSRF into account takeover: the attacker asks for a link to the victim's address from their own browser, the victim clicks it, and the attacker is logged in as the victim. [Clerk](https://clerk.com/docs/guides/secure/best-practices/protect-email-links) names this attack as the reason its setting defaults to on.
- **Showing whose account the page is logged in to, and trusting the person to notice.** The user rejected it on 2026-09-23: nobody reads the page that closely.

## What it costs to undo

Swapping the token store for Spring's means a table rename and accepting plaintext tokens. Unbinding links from the browser means dropping `LoginBrowserFilter`, the browser condition in `HashedTokenService` and the code, and brings back login CSRF. Going back to in-memory sessions means deleting one dependency and V3's tables through a new migration. Dropping Spring's flow for one written here means owning everything listed under "Why Spring's flow".
