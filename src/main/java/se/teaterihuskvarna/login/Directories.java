package se.teaterihuskvarna.login;

import java.util.List;

/// Picks the one [LoginDirectory] that answers for a kind of login, out of every
/// directory the application context holds. The member and administrator
/// packages each contribute one; this package never names either.
final class Directories {

    private Directories() {
    }

    /// @param kind        the kind of login to look up
    /// @param directories every directory bean
    /// @return the directory for that kind
    /// @throws IllegalStateException if no directory, or more than one, answers for that kind
    static LoginDirectory of(LoginKind kind, List<LoginDirectory> directories) {
        List<LoginDirectory> matching = directories.stream()
                .filter(directory -> directory.kind() == kind)
                .toList();
        if (matching.size() != 1) {
            throw new IllegalStateException(
                    "Expected one LoginDirectory for " + kind + ", found " + matching.size());
        }
        return matching.getFirst();
    }
}
