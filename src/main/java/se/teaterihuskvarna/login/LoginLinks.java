package se.teaterihuskvarna.login;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import se.teaterihuskvarna.Swedish;

/// Creates login links and their codes, and mails them. A link and its code
/// work only in the browser that asked for them ([LoginBrowser]).
///
/// An address nobody of that kind has gets nothing: no token, no mail, and no
/// sign of it in the return value. The caller shows the same page either way,
/// and [Background] does the lookup, the insert and the mail off the request
/// thread, so neither the page nor its timing says whether the address has an
/// account.
///
/// The database keeps the token's SHA-256, never the token, so a reader of the
/// `one_time_token` table or of a backup cannot log in as anyone. The token
/// itself exists only in the mail. [HashedTokenService] redeems it.
///
/// The mail text comes from `messages_sv.properties`, under
/// `login.mail.member.*` and `login.mail.administrator.*`, because an
/// administrator logs in somewhere else and for other reasons.
@Component
public class LoginLinks {

    private final List<LoginDirectory> directories;
    private final JdbcClient jdbc;
    private final Mailer mailer;
    private final Background background;
    private final MessageSource messages;
    private final LoginSettings settings;
    private final MailSettings mail;

    LoginLinks(List<LoginDirectory> directories, JdbcClient jdbc, Mailer mailer, Background background,
            MessageSource messages, LoginSettings settings, MailSettings mail) {
        this.directories = List.copyOf(directories);
        this.background = background;
        this.jdbc = jdbc;
        this.mailer = mailer;
        this.messages = messages;
        this.settings = settings;
        this.mail = mail;
    }

    /// Mails a login link and code if the address belongs to a login of this
    /// kind, and does nothing otherwise. Returns before either has happened: the
    /// work runs on [Background], after the caller's transaction commits if there
    /// is one.
    ///
    /// @param kind    whether to look among accounts or among administrator accounts
    /// @param email   the address as somebody typed it
    /// @param browser the [LoginBrowser] value of the browser that asked, the only one the link will work in
    public void send(LoginKind kind, String email, String browser) {
        String address = Addresses.normalise(email);
        background.run(() -> create(kind, address, browser));
    }

    /// What the link page should show for a link. A lookup and nothing more:
    /// mail scanners open every link, so opening one must not spend it.
    ///
    /// @param kind    whose link page was opened
    /// @param token   the token in the link
    /// @param browser the browser's login cookie, if it sent one
    /// @return whether the link works here, works only in another browser, or does not work
    public LinkOpening open(LoginKind kind, @Nullable String token, @Nullable String browser) {
        if (token == null || token.isBlank()) {
            return LinkOpening.UNUSABLE;
        }
        Optional<String> boundTo = jdbc.sql("""
                        SELECT browser_hash FROM one_time_token
                        WHERE token_hash = ? AND kind = ? AND expires_at > now()""")
                .param(Tokens.hash(token))
                .param(kind.code())
                .query(String.class)
                .optional();
        if (boundTo.isEmpty()) {
            return LinkOpening.UNUSABLE;
        }
        boolean here = browser != null && LoginBrowser.isWellFormed(browser)
                && MessageDigest.isEqual(
                        boundTo.get().getBytes(StandardCharsets.US_ASCII),
                        Tokens.hash(browser).getBytes(StandardCharsets.US_ASCII));
        return here ? LinkOpening.HERE : LinkOpening.ELSEWHERE;
    }

    private void create(LoginKind kind, String address, String browser) {
        Optional<SignedIn> found = Directories.of(kind, directories).find(address);
        if (found.isEmpty()) {
            return;
        }
        String token = Tokens.newToken();
        String code = Tokens.newCode();
        jdbc.sql("""
                        INSERT INTO one_time_token (token_hash, kind, email, expires_at, browser_hash, code_hash)
                        VALUES (?, ?, ?, ?, ?, ?)""")
                .param(Tokens.hash(token))
                .param(kind.code())
                .param(address)
                .param(OffsetDateTime.now(ZoneOffset.UTC).plus(settings.linkLifetime()))
                .param(Tokens.hash(browser))
                .param(Tokens.hash(code))
                .update();
        String link = mail.link(LoginUrls.of(kind).link(), token);
        Object[] arguments = {
            link, Lifetimes.describe(messages, settings.linkLifetime()), found.get().fullName(), code};
        String prefix = "login.mail." + kind.code();
        mailer.send(
                address,
                messages.getMessage(prefix + ".subject", null, Swedish.LOCALE),
                messages.getMessage(prefix + ".body", arguments, Swedish.LOCALE));
    }
}
