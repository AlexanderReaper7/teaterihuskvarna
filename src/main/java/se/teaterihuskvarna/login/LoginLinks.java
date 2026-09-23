package se.teaterihuskvarna.login;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/// Creates login links and mails them.
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

    private static final Locale SWEDISH = Locale.of("sv", "SE");

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

    /// Mails a login link if the address belongs to a login of this kind, and
    /// does nothing otherwise. Returns before either has happened: the work runs
    /// on [Background], after the caller's transaction commits if there is one.
    ///
    /// @param kind  whether to look among accounts or among administrator accounts
    /// @param email the address as somebody typed it
    public void send(LoginKind kind, String email) {
        String address = Addresses.normalise(email);
        background.run(() -> create(kind, address));
    }

    private void create(LoginKind kind, String address) {
        Optional<SignedIn> found = Directories.of(kind, directories).find(address);
        if (found.isEmpty()) {
            return;
        }
        String token = Tokens.newToken();
        jdbc.sql("INSERT INTO one_time_token (token_hash, kind, email, expires_at) VALUES (?, ?, ?, ?)")
                .param(Tokens.hash(token))
                .param(kind.code())
                .param(address)
                .param(OffsetDateTime.now(ZoneOffset.UTC).plus(settings.linkLifetime()))
                .update();
        String link = UriComponentsBuilder.fromUri(mail.siteUrl())
                .path(LoginUrls.of(kind).link())
                .queryParam("token", token)
                .build()
                .toUriString();
        Object[] arguments = {link, Lifetimes.describe(messages, settings.linkLifetime()), found.get().fullName()};
        String prefix = "login.mail." + kind.code();
        mailer.send(
                address,
                messages.getMessage(prefix + ".subject", null, SWEDISH),
                messages.getMessage(prefix + ".body", arguments, SWEDISH));
    }
}
