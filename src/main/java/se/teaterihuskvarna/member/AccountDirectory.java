package se.teaterihuskvarna.member;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import se.teaterihuskvarna.login.LoginDirectory;
import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.SignedIn;

/// Answers the member login page: which account, if any, an address or an id
/// belongs to.
/// Only accounts. An administrator with the same address is found by the
/// administrator package's directory, on the other login page.
@Component
@Transactional(readOnly = true)
class AccountDirectory implements LoginDirectory {

    private final AccountRepository accounts;

    AccountDirectory(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public LoginKind kind() {
        return LoginKind.MEMBER;
    }

    @Override
    public Optional<SignedIn> find(String email) {
        return accounts.findByEmailIgnoreCase(email).map(AccountDirectory::signedIn);
    }

    @Override
    public Optional<SignedIn> findById(long id) {
        return accounts.findById(id).map(AccountDirectory::signedIn);
    }

    private static SignedIn signedIn(Account account) {
        return new SignedIn(LoginKind.MEMBER, account.getId(), account.getEmail(), account.getMember().getFullName());
    }
}
