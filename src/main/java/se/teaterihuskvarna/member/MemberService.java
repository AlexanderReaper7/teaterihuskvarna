package se.teaterihuskvarna.member;

import jakarta.validation.Valid;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import se.teaterihuskvarna.Swedish;
import se.teaterihuskvarna.export.Csv;
import se.teaterihuskvarna.login.Email;
import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.PasskeyService;
import se.teaterihuskvarna.login.Sessions;

/// Everything the system can do with the member register: a member's own
/// details (R012), and the administrators' register with search, add, edit,
/// delete and export (R018, R021).
///
/// This is the only way in. The repositories are package private so that no
/// adapter can reach the database around this class, and an ArchUnit rule keeps
/// `web` and `api` off the entities as well.
///
/// Both adapters call these methods: `web` as a plain method call, `api` behind
/// its own HTTP endpoint. That is what makes their behaviour identical rather
/// than merely similar, and `AdapterRulesTest` is the check that `api` has not
/// fallen behind. See `docs/decisions/0014-one-service-layer-two-adapters.md`.
@Service
@Validated
@Transactional(readOnly = true)
public class MemberService {

    /// How many members a search returns at most. The association has a few
    /// hundred members, so a search that matches more is one that should be
    /// narrowed.
    public static final int SEARCH_LIMIT = 200;

    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ISO_LOCAL_DATE.withZone(FeeLedger.SWEDEN);

    private final MemberRepository members;
    private final AccountRepository accounts;
    private final HouseholdRepository households;
    private final InvitationRepository invitations;
    private final FeeLedger ledger;
    private final PasskeyService passkeys;
    private final Sessions sessions;
    private final MessageSource messages;
    private final ApplicationEventPublisher events;

    MemberService(
            MemberRepository members,
            AccountRepository accounts,
            HouseholdRepository households,
            InvitationRepository invitations,
            FeeLedger ledger,
            PasskeyService passkeys,
            Sessions sessions,
            MessageSource messages,
            ApplicationEventPublisher events) {
        this.members = members;
        this.accounts = accounts;
        this.households = households;
        this.invitations = invitations;
        this.ledger = ledger;
        this.passkeys = passkeys;
        this.sessions = sessions;
        this.messages = messages;
        this.events = events;
    }

    /// Looks up the member a logged-in account belongs to. The id comes from
    /// `SignedIn.id()`, which for a member login is the account's id, not the
    /// member's.
    ///
    /// @param accountId the logged-in account's id
    /// @return the member that account belongs to, or empty if the account is gone
    public Optional<MemberDetails> findByAccount(long accountId) {
        return accounts.findById(accountId).map(account -> details(account.getMember(), account));
    }

    /// A member changes their own name, phone and postal address (R012). The
    /// address they log in with is not on the form: only an administrator
    /// changes it.
    ///
    /// @param accountId the logged-in account's id
    /// @param form      the new values
    /// @return the member as stored
    /// @throws jakarta.validation.ConstraintViolationException if the form breaks a constraint
    /// @throws NoSuchMember if the account is gone
    @Transactional
    public MemberDetails updateContact(long accountId, @Valid ContactForm form) {
        Account account = accounts.findById(accountId).orElseThrow(NoSuchMember::new);
        Member member = account.getMember();
        member.setFullName(form.fullName().strip());
        member.setContact(form.contact());
        events.publishEvent(new MemberChanged(member.getId()));
        return details(member, account);
    }

    /// Searches the register by name, account address, phone and city, as a
    /// case insensitive substring of any of them. A blank query lists everyone.
    ///
    /// @param query what the administrator typed, or null
    /// @return the first [#SEARCH_LIMIT] matches by name, and whether there were more
    public MemberList search(@Nullable String query) {
        String typed = query == null ? "" : query.strip().toLowerCase(Swedish.LOCALE);
        String pattern = "%" + typed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<Member> found = members.search(pattern, Limit.of(SEARCH_LIMIT + 1));
        boolean more = found.size() > SEARCH_LIMIT;
        List<Member> shown = more ? found.subList(0, SEARCH_LIMIT) : found;
        return new MemberList(detailsOf(shown), more);
    }

    /// @param memberId a member
    /// @return everything an administrator sees about the member
    /// @throws NoSuchMember if there is no such member
    public MemberFile find(long memberId) {
        Member member = members.findById(memberId).orElseThrow(NoSuchMember::new);
        Account account = accounts.findByMember(memberId).orElse(null);
        List<FeeStatus> fees = ledger.history(member);
        HouseholdDetails household = null;
        Household current = member.getHousehold();
        if (current != null) {
            household = HouseholdService.details(current, members.findByHousehold(current.getId()), accounts,
                    invitations);
        }
        Instant invitation = invitations.findByMemberIdIn(List.of(memberId)).stream()
                .map(Invitation::getExpiresAt)
                .findFirst()
                .orElse(null);
        int year = ledger.currentYear();
        FeeStatus thisYear = fees.stream().filter(fee -> fee.year() == year).findFirst().orElseThrow();
        return new MemberFile(MemberDetails.of(member, account, thisYear), fees, household, invitation);
    }

    /// Adds a member (R018). An address gives the member an account at once;
    /// without one the member cannot log in until an invitation is accepted.
    ///
    /// @param form the new member
    /// @return the member as stored
    /// @throws jakarta.validation.ConstraintViolationException if the form breaks a constraint
    /// @throws EmailTaken if another account has the address
    /// @throws NoSuchHousehold if the chosen household does not exist
    @Transactional
    public MemberDetails add(@Valid MemberForm form) {
        Member member = new Member(form.fullName().strip());
        member.setContact(form.contact());
        member.setHousehold(household(form.householdId()));
        members.save(member);
        String email = Blank.toNull(form.email());
        Account account = null;
        if (email != null) {
            refuseTaken(new Email(email), null);
            account = accounts.save(new Account(member, new Email(email)));
        }
        flush();
        events.publishEvent(new MemberChanged(member.getId()));
        return details(member, account);
    }

    /// Changes every field of a member, the address and the household included
    /// (R018, R019). An address on a member without an account creates one, and
    /// withdraws any open invitation, since its link would now fail.
    ///
    /// @param memberId the member to change
    /// @param form     the new values
    /// @return the member as stored
    /// @throws jakarta.validation.ConstraintViolationException if the form breaks a constraint
    /// @throws NoSuchMember if there is no such member
    /// @throws EmailTaken if another account has the address
    /// @throws AccountNeedsEmail if the address is blank and the member has an account
    /// @throws NoSuchHousehold if the chosen household does not exist
    @Transactional
    public MemberDetails update(long memberId, @Valid MemberForm form) {
        Member member = members.findById(memberId).orElseThrow(NoSuchMember::new);
        Account account = accounts.findByMember(memberId).orElse(null);
        String email = Blank.toNull(form.email());
        if (email == null && account != null) {
            throw new AccountNeedsEmail();
        }
        member.setFullName(form.fullName().strip());
        member.setContact(form.contact());
        member.setHousehold(household(form.householdId()));
        if (email != null) {
            Email address = new Email(email);
            refuseTaken(address, account);
            if (account == null) {
                account = accounts.save(new Account(member, address));
                invitations.deleteByMember(memberId);
            } else {
                account.setEmail(address);
            }
        }
        flush();
        events.publishEvent(new MemberChanged(memberId));
        return details(member, account);
    }

    /// Deletes a member with their account, passkeys, sessions and open
    /// invitation. Their fee payments stay, with the member's id set to NULL
    /// by the database, so each year's income can still be counted: the user's
    /// decision of 2026-09-28.
    ///
    /// Sessions are deleted here, and the member chain's `ActiveLoginFilter`
    /// refuses a session written by a login that raced this deletion.
    ///
    /// @param memberId the member to delete
    /// @throws NoSuchMember if there is no such member
    @Transactional
    public void delete(long memberId) {
        Member member = members.findById(memberId).orElseThrow(NoSuchMember::new);
        Optional<Account> account = accounts.findByMember(memberId);
        if (account.isPresent()) {
            long accountId = account.get().getId();
            passkeys.removeAll(LoginKind.MEMBER, accountId);
            accounts.delete(account.get());
            sessions.end(LoginKind.MEMBER, accountId);
        }
        members.delete(member);
        members.flush();
        events.publishEvent(new MemberChanged(memberId));
    }

    /// The whole register as a CSV file for a spreadsheet (R021), in the format
    /// [Csv] describes. One row per member, by name.
    ///
    /// @return the file's text, with the byte order mark
    public String exportCsv() {
        int year = ledger.currentYear();
        List<String> header = List.of(
                text("register.csv.name"),
                text("register.csv.email"),
                text("register.csv.phone"),
                text("register.csv.address"),
                text("register.csv.postalCode"),
                text("register.csv.city"),
                text("register.csv.household"),
                messages.getMessage("register.csv.paid", new Object[] {String.valueOf(year)}, Swedish.LOCALE),
                text("register.csv.memberSince"));
        List<List<String>> rows = new ArrayList<>();
        for (MemberDetails member : detailsOf(members.findAllByName())) {
            Instant paidAt = member.fee().paidAt();
            rows.add(Arrays.asList(
                    member.fullName(),
                    member.email(),
                    member.phone(),
                    member.address(),
                    member.postalCode(),
                    member.city(),
                    member.household(),
                    paidAt == null ? null : CSV_DATE.format(paidAt),
                    CSV_DATE.format(member.memberSince())));
        }
        return Csv.write(header, rows);
    }

    /// The member as a mailing reaches them, for their Brevo contact. Only a
    /// member with an account has an address.
    ///
    /// @param memberId a member, who may have been deleted
    /// @return the member's name and address, or empty without a member or an account
    public Optional<Recipient> recipient(long memberId) {
        return members.findById(memberId).flatMap(member -> accounts.findByMember(memberId)
                .map(account -> new Recipient(memberId, member.getFullName(), account.getEmail())));
    }

    private MemberDetails details(Member member, @Nullable Account account) {
        return MemberDetails.of(member, account, ledger.year(ledger.currentYear()).status(member));
    }

    private List<MemberDetails> detailsOf(List<Member> found) {
        Map<Long, Account> byMember = new HashMap<>();
        List<Long> ids = found.stream().map(Member::getId).toList();
        if (!ids.isEmpty()) {
            for (Account account : accounts.findByMembers(ids)) {
                byMember.put(account.getMember().getId(), account);
            }
        }
        FeeLedger.Payments year = ledger.year(ledger.currentYear());
        List<MemberDetails> details = new ArrayList<>();
        for (Member member : found) {
            details.add(MemberDetails.of(member, byMember.get(member.getId()), year.status(member)));
        }
        return details;
    }

    private @Nullable Household household(@Nullable Long householdId) {
        if (householdId == null) {
            return null;
        }
        return households.findById(householdId).orElseThrow(NoSuchHousehold::new);
    }

    /// The unique index on `LOWER(email)` is the backstop when two requests
    /// race past this check; [#flush] turns that into the same exception.
    private void refuseTaken(Email email, @Nullable Account own) {
        Optional<Account> holder = accounts.findByEmailIgnoreCase(email.value());
        if (holder.isPresent() && (own == null || !holder.get().getId().equals(own.getId()))) {
            throw new EmailTaken();
        }
    }

    private void flush() {
        try {
            accounts.flush();
        } catch (DataIntegrityViolationException e) {
            throw new EmailTaken(e);
        }
    }

    private String text(String key) {
        return messages.getMessage(key, null, Swedish.LOCALE);
    }
}
