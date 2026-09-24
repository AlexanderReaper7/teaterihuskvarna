package se.teaterihuskvarna.member;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import se.teaterihuskvarna.Swedish;
import se.teaterihuskvarna.login.Background;
import se.teaterihuskvarna.login.Email;
import se.teaterihuskvarna.login.Lifetimes;
import se.teaterihuskvarna.login.LinkRequestLimiter;
import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.LoginSettings;
import se.teaterihuskvarna.login.LoginUrls;
import se.teaterihuskvarna.login.MailSettings;
import se.teaterihuskvarna.login.Mailer;
import se.teaterihuskvarna.login.Tokens;

/// Membership applications: the Bli medlem form, and the confirmation link that
/// turns an application into a member with an account.
///
/// The rules are in `docs/projektplan.md`. The form answers the same way
/// whether or not the address already belongs to an account, and the form
/// counts against the same rate limit as the login page, because otherwise it
/// would answer "is this address a member?" for anyone who asked.
@Service
@Validated
@Transactional(readOnly = true)
public class MembershipApplicationService {

    private static final String CONFIRMATION_PATH = "/bli-medlem/bekrafta";

    private final MembershipApplicationRepository applications;
    private final AccountRepository accounts;
    private final MemberRepository members;
    private final LinkRequestLimiter limiter;
    private final Background background;
    private final Mailer mailer;
    private final MessageSource messages;
    private final LoginSettings login;
    private final MailSettings mail;
    private final AssociationSettings association;

    MembershipApplicationService(
            MembershipApplicationRepository applications,
            AccountRepository accounts,
            MemberRepository members,
            LinkRequestLimiter limiter,
            Background background,
            Mailer mailer,
            MessageSource messages,
            LoginSettings login,
            MailSettings mail,
            AssociationSettings association) {
        this.applications = applications;
        this.accounts = accounts;
        this.members = members;
        this.limiter = limiter;
        this.background = background;
        this.mailer = mailer;
        this.messages = messages;
        this.login = login;
        this.mail = mail;
        this.association = association;
    }

    /// Receives a Bli medlem form. Returns nothing, and returns the same way in
    /// every case, so the caller cannot tell which of these happened:
    ///
    /// - the address or the client is over the rate limit, and nothing is sent;
    /// - the address belongs to an account, which gets a mail pointing to the
    ///   login page instead;
    /// - otherwise the application is stored, replacing any earlier one for the
    ///   address, and a confirmation link is mailed.
    ///
    /// Everything after the rate limit runs on [Background], so the request
    /// thread does the same work in every case and the response time does not
    /// tell the cases apart either.
    ///
    /// @param form          what the visitor filled in
    /// @param clientAddress the client's IP address, for the rate limit
    /// @throws jakarta.validation.ConstraintViolationException if the form breaks a constraint
    @Transactional
    public void apply(@Valid ApplicationForm form, String clientAddress) {
        Email email = new Email(form.email());
        if (!limiter.tryAcquire(email.value(), clientAddress)) {
            return;
        }
        background.run(() -> receive(form, email));
    }

    private void receive(ApplicationForm form, Email email) {
        if (accounts.findByEmailIgnoreCase(email.value()).isPresent()) {
            alreadyMember(email);
            return;
        }

        String fullName = form.fullName().strip();
        String token = Tokens.newToken();
        Instant now = Instant.now();
        applications.replace(
                fullName,
                email.value(),
                blankToNull(form.phone()),
                blankToNull(form.address()),
                blankToNull(form.postalCode()),
                blankToNull(form.city()),
                Tokens.hash(token),
                now,
                now.plus(login.applicationLifetime()));

        String link = mail.link(CONFIRMATION_PATH, token);
        String lifetime = Lifetimes.describe(messages, login.applicationLifetime());
        mailer.send(
                email,
                messages.getMessage("application.mail.subject", null, Swedish.LOCALE),
                messages.getMessage("application.mail.body", new Object[] {fullName, link, lifetime},
                        Swedish.LOCALE));
    }

    /// Points a member who applied again to the login page, with no token. A
    /// login link here would work in any browser, since the application form
    /// sets no login cookie, and would reopen what [se.teaterihuskvarna.login.LoginBrowser]
    /// closes: anyone applying with their own address could pass the link on.
    private void alreadyMember(Email email) {
        String login = mail.link(LoginUrls.of(LoginKind.MEMBER).page(), null);
        mailer.send(
                email,
                messages.getMessage("application.mail.member.subject", null, Swedish.LOCALE),
                messages.getMessage("application.mail.member.body", new Object[] {login}, Swedish.LOCALE));
    }

    /// Follows a confirmation link. The application is deleted whatever the
    /// outcome, so a link works once at most.
    ///
    /// @param token the token from the link
    /// @return the new member's welcome, or empty if the link is unknown, used, or
    ///         expired, or if an account took the address after the application was made
    @Transactional
    public Optional<Welcome> confirm(@Nullable String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        // The lock makes a second confirmation of the same link wait for the
        // first, then find the row gone. The unique index on account email is
        // the backstop if two different links for one address race.
        Optional<MembershipApplication> found = applications.findByTokenHash(Tokens.hash(token));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        MembershipApplication application = found.get();
        applications.delete(application);

        if (!application.getExpiresAt().isAfter(Instant.now())) {
            return Optional.empty();
        }
        // An invitation, or another application confirmed first, may have given
        // this address an account since the mail went out.
        if (accounts.findByEmailIgnoreCase(application.getEmail()).isPresent()) {
            return Optional.empty();
        }

        Member member = new Member(application.getFullName());
        member.setPhone(application.getPhone());
        member.setAddress(application.getAddress());
        member.setPostalCode(application.getPostalCode());
        member.setCity(application.getCity());
        members.save(member);
        accounts.save(new Account(member, new Email(application.getEmail())));

        return Optional.of(new Welcome(member.getFullName(), application.getEmail(), association.bankgiro()));
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
