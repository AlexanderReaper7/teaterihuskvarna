package se.teaterihuskvarna;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import se.teaterihuskvarna.login.LoginBrowser;
import se.teaterihuskvarna.login.LoginKind;

/// What every integration test shares: the whole application against a real
/// PostgreSQL, MockMvc with every filter (Spring Session and Spring Security
/// included), a mocked mail sender, and empty tables before each test.
///
/// Every IT extends this class and adds no context configuration of its own.
/// Spring caches an application context by its configuration, mocked beans
/// included, so identical configuration means one context and one container for
/// the whole run. A subclass that adds a `@MockitoBean` or a property gets a
/// second context, and a second container with it.
///
/// Test data goes in through SQL rather than the repositories, which are package
/// private in the packages that own them and which other code is free to change.
/// The first administrator is the exception: the application seeds it at startup
/// from `teaterihuskvarna.first-administrator.*`, and the cleanup keeps it.
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfiguration.class, BackgroundTasks.class})
public abstract class IntegrationTestSupport {

    /// How long a test waits for a mail. [se.teaterihuskvarna.login.Mailer] sends
    /// on a background thread after the commit, so the mail can arrive after the
    /// response has.
    protected static final long MAIL_WAIT_MILLIS = 5000;

    /// The mail sender, mocked so nothing leaves the test. Spring resets it after
    /// each test.
    @MockitoBean
    protected JavaMailSender mailSender;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    private BackgroundTasks backgroundTasks;

    @Value("${teaterihuskvarna.first-administrator.email}")
    protected String firstAdministratorEmail;

    @Value("${teaterihuskvarna.mail.site-url}")
    protected String siteUrl;

    /// The [LoginBrowser] cookie per login, as one browser would hold it:
    /// [#requestLink] sends and keeps it, [#followLink] and [#enterCode] send it.
    private final Map<LoginKind, Cookie> browser = new EnumMap<>(LoginKind.class);

    /// The tables emptied before every test, children before parents because of
    /// the foreign keys. Passkeys have none, so they go first by choice.
    /// `EmptiedTablesIT` fails when the database has a table that is not here,
    /// so a new table cannot leak rows from one test into the next.
    public static final List<String> EMPTIED = List.of(
            "spring_session",
            "user_credentials",
            "user_entities",
            "one_time_token",
            "link_request",
            "membership_application",
            "mailing",
            "outbox",
            "volunteer_booking",
            "volunteer_shift",
            "offer_registration",
            "offer",
            "member_document",
            "fee",
            "invitation",
            "account",
            "member",
            "household");

    private static final AtomicBoolean IDS_APART = new AtomicBoolean();

    /// Gives the ids of every table a million of their own, once per run.
    ///
    /// The tests insert a member and its account together, so the two ids
    /// were often the same number, and code that passed one where the other
    /// belongs still found a row and the test passed. With the sequences
    /// apart, an id of the wrong kind names no row.
    @BeforeEach
    protected void idsApart() {
        if (!IDS_APART.compareAndSet(false, true)) {
            return;
        }
        // Not information_schema.sequences, which leaves out the sequence of an identity column.
        List<String> sequences = jdbc.sql("""
                        SELECT sequencename FROM pg_sequences
                        WHERE schemaname = 'public' ORDER BY sequencename""")
                .query(String.class)
                .list().stream().map(Objects::requireNonNull).toList();
        for (int i = 0; i < sequences.size(); i++) {
            jdbc.sql("SELECT setval(CAST(? AS REGCLASS), ?)")
                    .param(sequences.get(i))
                    .param((i + 1) * 1_000_000L)
                    .query(Long.class)
                    .single();
        }
    }

    /// The first administrator is made active again rather than deleted, since
    /// the application only seeds it when no administrator exists at all, and
    /// that happens at startup, not between tests.
    @BeforeEach
    protected void emptyTables() {
        browser.clear();
        for (String table : EMPTIED) {
            jdbc.sql("DELETE FROM " + table).update();
        }
        jdbc.sql("UPDATE administrator SET removed_at = NULL, removed_by = NULL WHERE LOWER(email) = LOWER(?)")
                .param(firstAdministratorEmail)
                .update();
        jdbc.sql("DELETE FROM administrator WHERE LOWER(email) <> LOWER(?)")
                .param(firstAdministratorEmail)
                .update();
    }

    /// @param fullName the member's name
    /// @return the new member's id
    protected long insertMember(String fullName) {
        return jdbc.sql("INSERT INTO member (full_name, created_at) VALUES (?, now()) RETURNING id")
                .param(fullName)
                .query(Long.class)
                .single();
    }

    /// @param fullName the member's name
    /// @param email    the account's address
    /// @return the new account's id, which is what a member's `SignedIn` carries
    protected long insertAccount(String fullName, String email) {
        long memberId = insertMember(fullName);
        return jdbc.sql("INSERT INTO account (member_id, email, created_at) VALUES (?, ?, now()) RETURNING id")
                .param(memberId)
                .param(email)
                .query(Long.class)
                .single();
    }

    /// @param email    the administrator account's address
    /// @param fullName the administrator's name
    /// @return the new administrator's id
    protected long insertAdministrator(String email, String fullName) {
        return jdbc.sql("INSERT INTO administrator (email, full_name, created_at) VALUES (?, ?, now()) RETURNING id")
                .param(email)
                .param(fullName)
                .query(Long.class)
                .single();
    }

    /// @return the id of the administrator the application seeded at startup
    protected long firstAdministratorId() {
        return jdbc.sql("SELECT id FROM administrator WHERE LOWER(email) = LOWER(?)")
                .param(firstAdministratorEmail)
                .query(Long.class)
                .single();
    }

    /// @param table a table name
    /// @return how many rows it holds
    protected long rowsIn(String table) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }

    /// Waits until the mail the test caused so far has gone out, then requires
    /// exactly `count` mails in total since the test started, or since the last
    /// [#forgetMails].
    ///
    /// @param count how many mails must go out
    /// @return the mails, in the order they were sent
    protected List<SimpleMailMessage> awaitMails(int count) {
        awaitMailSettled();
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(count)).send(sent.capture());
        return sent.getAllValues();
    }

    /// @return the one mail sent since the test started, or since the last [#forgetMails]
    protected SimpleMailMessage awaitMail() {
        return awaitMails(1).getFirst();
    }

    /// Waits until the mail the test caused so far has gone out, and fails if there was any.
    protected void assertNoMail() {
        awaitMailSettled();
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    /// Waits, at most [#MAIL_WAIT_MILLIS], until no mail is on its way: no task
    /// is left on the executor [se.teaterihuskvarna.login.Background] uses, and
    /// no mail in the outbox is due. A mail leaves the outbox in the transaction
    /// that hands it to the sender, so a due row is one not sent yet. A mail
    /// that failed is not due until its retry, and does not count.
    ///
    /// The count is read before the outbox, never after. A task commits its
    /// outbox row before it ends, so every row a finished task wrote is visible
    /// to the query that follows. In the other order, a task could write its
    /// row just after the query and end just before the count.
    private void awaitMailSettled() {
        assertThat(backgroundTasks.counting())
                .as("BackgroundTasks counts the tasks on applicationTaskExecutor")
                .isTrue();
        long deadline = System.nanoTime() + MAIL_WAIT_MILLIS * 1_000_000;
        while (backgroundTasks.pending() > 0 || mailsDue() > 0) {
            assertThat(System.nanoTime() - deadline)
                    .as("background tasks or mail still pending after %d ms", MAIL_WAIT_MILLIS)
                    .isNegative();
            LockSupport.parkNanos(5_000_000);
        }
    }

    private long mailsDue() {
        return jdbc.sql("SELECT COUNT(*) FROM outbox WHERE kind = 'mail' AND next_attempt_at <= now()")
                .query(Long.class)
                .single();
    }

    /// Makes the relay refuse every mail from now on. A refused mail stays in
    /// the outbox, where a test can read what is stored with it, and still
    /// counts as sent to [#awaitMails].
    protected void refuseEveryMail() {
        doThrow(new MailSendException("relay down")).when(mailSender).send(any(SimpleMailMessage.class));
    }

    /// @return `not_after` of the one mail in the outbox, or null if it has none
    protected @Nullable Instant notAfterOfTheMailInTheOutbox() {
        return jdbc.sql("SELECT not_after FROM outbox WHERE kind = 'mail'")
                .query((row, number) -> Optional.ofNullable(row.getTimestamp(1)).map(Timestamp::toInstant))
                .single()
                .orElse(null);
    }

    /// @param sql a query for one timestamp
    /// @return the timestamp
    protected Instant instantFrom(String sql) {
        return jdbc.sql(sql).query(Timestamp.class).single().toInstant();
    }

    /// Makes the mail helpers count from now.
    protected void forgetMails() {
        clearInvocations(mailSender);
    }

    /// Finds the token in a link to `path` on the configured site. Anchored to the
    /// site address, so a link built from the request's Host header does not
    /// match, and so `/logga-in/lank` does not match inside `/admin/logga-in/lank`.
    ///
    /// @param mail the mail to read
    /// @param path the link's path, such as `/logga-in/lank`
    /// @return the token
    protected String tokenIn(SimpleMailMessage mail, String path) {
        String text = String.valueOf(mail.getText());
        Matcher link = Pattern.compile(Pattern.quote(siteUrl + path + "?token=") + "([A-Za-z0-9_-]+)")
                .matcher(text);
        assertThat(link.find()).as("a link to %s%s in the mail:%n%s", siteUrl, path, text).isTrue();
        return link.group(1);
    }

    /// @param mail the mail to read
    /// @return the six digit code in it
    protected static String codeIn(SimpleMailMessage mail) {
        String text = String.valueOf(mail.getText());
        Matcher code = Pattern.compile("^(\\d{6})$", Pattern.MULTILINE).matcher(text);
        assertThat(code.find()).as("a code on a line of its own in the mail:%n%s", text).isTrue();
        return code.group(1);
    }

    /// @param kind which login
    /// @return the login page's path, which a POST asks for a link on
    protected static String loginPage(LoginKind kind) {
        return switch (kind) {
            case MEMBER -> "/logga-in";
            case ADMINISTRATOR -> "/admin/logga-in";
        };
    }

    /// @param kind which login
    /// @return the path of the link in the mail, which a POST with the token logs in on
    protected static String linkPath(LoginKind kind) {
        return loginPage(kind) + "/lank";
    }

    /// @param kind which login
    /// @return where a successful login lands
    protected static String landing(LoginKind kind) {
        return switch (kind) {
            case MEMBER -> "/medlem";
            case ADMINISTRATOR -> "/admin";
        };
    }

    /// Asks for a link from this test's browser, sending the [LoginBrowser]
    /// cookie it holds for the kind and keeping the one the response sets.
    ///
    /// @param kind  which login page
    /// @param email the address to type in
    /// @return the response to asking for a link
    /// @throws Exception from MockMvc
    protected MvcResult requestLink(LoginKind kind, String email) throws Exception {
        MvcResult result = mockMvc.perform(post(loginPage(kind)).param("email", email).with(csrf())
                        .with(inBrowser(kind)))
                .andReturn();
        Cookie set = result.getResponse().getCookie(LoginBrowser.COOKIE);
        if (set != null) {
            browser.put(kind, set);
        }
        return result;
    }

    /// Presses the button on the link page in this test's browser.
    ///
    /// @param kind  which login the link belongs to
    /// @param token the token from the mail
    /// @return the response to the button on the link page
    /// @throws Exception from MockMvc
    protected MvcResult followLink(LoginKind kind, String token) throws Exception {
        return mockMvc.perform(post(linkPath(kind)).param("token", token).with(csrf()).with(inBrowser(kind)))
                .andReturn();
    }

    /// Types the code from the mail on the "link sent" page in this test's browser.
    ///
    /// @param kind which login the code belongs to
    /// @param code what to type
    /// @return the response to the form
    /// @throws Exception from MockMvc
    protected MvcResult enterCode(LoginKind kind, String code) throws Exception {
        return mockMvc.perform(post(linkPath(kind)).param("code", code).with(csrf()).with(inBrowser(kind)))
                .andReturn();
    }

    /// @param kind which login
    /// @return the [LoginBrowser] cookie this test's browser holds for it, or null before it asked for a link
    protected @Nullable Cookie browserCookie(LoginKind kind) {
        return browser.get(kind);
    }

    private RequestPostProcessor inBrowser(LoginKind kind) {
        Cookie cookie = browser.get(kind);
        return request -> {
            if (cookie != null) {
                Cookie[] existing = request.getCookies();
                request.setCookies(existing == null ? new Cookie[] {cookie}
                        : Stream.concat(Arrays.stream(existing), Stream.of(cookie)).toArray(Cookie[]::new));
            }
            return request;
        };
    }

    /// Logs in the way a person does: ask for a link, read it from the mail, press
    /// the button. Forgets earlier mails first.
    ///
    /// @param kind  which login
    /// @param email the address to log in with
    /// @return the login response, whose cookies [#sessionOf] carries on
    /// @throws Exception from MockMvc
    protected MvcResult logInByLink(LoginKind kind, String email) throws Exception {
        forgetMails();
        requestLink(kind, email);
        MvcResult login = followLink(kind, tokenIn(awaitMail(), linkPath(kind)));
        assertRedirect(login, landing(kind));
        return login;
    }

    /// Sends a response's cookies on a later request, the way a browser would.
    /// This is how a test reaches the session Spring Session stored in
    /// PostgreSQL, rather than a session MockMvc keeps in memory.
    ///
    /// Adds to any cookie the request already has rather than replacing it, as
    /// a browser sends every cookie it holds for the path.
    ///
    /// @param result a response that may have set a session cookie
    /// @return a post processor that puts its live cookies on a request
    protected static RequestPostProcessor sessionOf(MvcResult result) {
        Cookie[] cookies = Arrays.stream(result.getResponse().getCookies())
                .filter(cookie -> cookie.getMaxAge() != 0)
                .toArray(Cookie[]::new);
        return request -> {
            if (cookies.length > 0) {
                Cookie[] existing = request.getCookies();
                request.setCookies(existing == null ? cookies
                        : Stream.concat(Arrays.stream(existing), Arrays.stream(cookies)).toArray(Cookie[]::new));
            }
            return request;
        };
    }

    /// @param result a response
    /// @return the redirect's path and query without scheme or host, or null for no redirect
    protected static @Nullable String locationOf(MvcResult result) {
        String location = result.getResponse().getRedirectedUrl();
        if (location == null) {
            return null;
        }
        URI uri = URI.create(location);
        return uri.getRawQuery() == null ? uri.getRawPath() : uri.getRawPath() + "?" + uri.getRawQuery();
    }

    /// Spring Security writes some redirects with the host and some without, so
    /// this compares the path and query only.
    ///
    /// @param result a response
    /// @param path   the path, and query if any, it must redirect to
    protected static void assertRedirect(MvcResult result, String path) {
        assertThat(result.getResponse().getStatus()).as("status of a redirect to %s", path).isBetween(300, 399);
        assertThat(locationOf(result)).isEqualTo(path);
    }

    /// @param result a page
    /// @return its body with every run of white space as one space, so that a tag
    ///         the template splits over two lines can be matched
    /// @throws UnsupportedEncodingException never, UTF-8 is always there
    protected static String bodyOf(MvcResult result) throws UnsupportedEncodingException {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8).replaceAll("\\s+", " ");
    }

    /// Asserts what a form's page shows for a field whose value could not become
    /// the form's type: a link to the field in the summary, the value as it was
    /// typed, and the message tied to the field.
    ///
    /// @param page    the page, from [#bodyOf]
    /// @param field   the field's name, which is also its `id`
    /// @param typed   what was sent in it
    /// @param message the Swedish message the field gets
    protected static void assertUnreadable(String page, String field, String typed, String message) {
        assertThat(page).contains(
                "<div role=\"alert\">",
                "<li><a href=\"#" + field + "\">" + message + "</a></li>",
                "id=\"" + field + "\" name=\"" + field + "\"",
                "value=\"" + typed + "\"",
                "aria-invalid=\"true\" aria-describedby=\"" + field + "-error\"",
                "<span id=\"" + field + "-error\" class=\"field-error\">" + message + "</span>");
    }

    /// Independent of `Tokens.hash`, so a test does not check the hash with the
    /// code it is checking.
    ///
    /// @param text a token
    /// @return its SHA-256 in lower case hex
    protected static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /// Everything a client can see of a response apart from header values, which
    /// differ by session id and date. Two responses that must not tell a known
    /// address from an unknown one have to be equal in this.
    ///
    /// @param status   the status code
    /// @param location the redirect's path and query, or null
    /// @param body     the body
    /// @param headers  the names of the headers set
    public record Visible(int status, @Nullable String location, String body, Set<String> headers) {

        /// @param result a response
        /// @return what a client sees of it
        /// @throws UnsupportedEncodingException if the body has an unknown charset
        public static Visible of(MvcResult result) throws UnsupportedEncodingException {
            MockHttpServletResponse response = result.getResponse();
            return new Visible(
                    response.getStatus(),
                    locationOf(result),
                    response.getContentAsString(),
                    new TreeSet<>(response.getHeaderNames()));
        }
    }
}
