package se.teaterihuskvarna.member;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Everything the system can do with the member register.
///
/// This is the only way in. `MemberRepository` is package private so that no
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

    private final MemberRepository members;

    MemberService(MemberRepository members) {
        this.members = members;
    }

    /// Looks a member up by the address their login link would go to.
    ///
    /// @param email the address to look up, in any case
    /// @return the member with that address, or empty
    public Optional<MemberDetails> findByEmail(String email) {
        return members.findByEmailIgnoreCase(email).map(MemberDetails::of);
    }
}
