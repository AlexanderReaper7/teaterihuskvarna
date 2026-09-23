package se.teaterihuskvarna.development;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// What is running, and where its mail goes.
///
/// @param profiles      the active Spring profiles
/// @param siteUrl       the address login links point at
/// @param schemaVersion the newest Flyway migration applied, or null before any
/// @param commit        the commit the build came from, or null when the build
///                      had no `git.properties`
/// @param builtAt       when the jar was built, or null when the build had no
///                      `build-info.properties`
/// @param mailpitUrl    where Mailpit shows the mails sent
public record RunningEnvironment(
        List<String> profiles,
        String siteUrl,
        @Nullable String schemaVersion,
        @Nullable Commit commit,
        @Nullable Instant builtAt,
        String mailpitUrl) {

    /// Copies the profiles, so the record cannot change after it is built.
    public RunningEnvironment {
        profiles = List.copyOf(profiles);
    }

    /// The commit a build came from, and whether the worktree matched it.
    ///
    /// @param id                 the abbreviated commit id
    /// @param time               when the commit was made, or null
    /// @param uncommittedChanges whether the worktree differed from the commit
    ///                           when the jar was built, untracked files included,
    ///                           so the jar is not exactly that commit
    public record Commit(String id, @Nullable Instant time, boolean uncommittedChanges) {
    }
}
