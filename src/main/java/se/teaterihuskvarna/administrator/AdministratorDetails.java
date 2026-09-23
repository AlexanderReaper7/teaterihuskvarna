package se.teaterihuskvarna.administrator;

import java.time.Instant;

/// What an adapter is given about an administrator account. A record built
/// inside the transaction rather than the entity, for the reason in
/// `docs/decisions/0014-one-service-layer-two-adapters.md`.
///
/// @param id        the administrator's id, the one `SignedIn.id()` carries
/// @param email     the address login links go to
/// @param fullName  the administrator's name
/// @param createdAt when the account was added, or re-added after a removal
public record AdministratorDetails(long id, String email, String fullName, Instant createdAt) {

    static AdministratorDetails of(Administrator administrator) {
        return new AdministratorDetails(
                administrator.getId(),
                administrator.getEmail(),
                administrator.getFullName(),
                administrator.getCreatedAt());
    }
}
