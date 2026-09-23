package se.teaterihuskvarna.member;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Everything the system can do with the member register.
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
@Transactional(readOnly = true)
public class MemberService {

    private final AccountRepository accounts;

    MemberService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /// Looks up the member a logged-in account belongs to. The id comes from
    /// `SignedIn.id()`, which for a member login is the account's id, not the
    /// member's.
    ///
    /// @param accountId the logged-in account's id
    /// @return the member that account belongs to, or empty if the account is gone
    public Optional<MemberDetails> findByAccount(long accountId) {
        return accounts.findById(accountId).map(account -> MemberDetails.of(account.getMember(), account));
    }
}
