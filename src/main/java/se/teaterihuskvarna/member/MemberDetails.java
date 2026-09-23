package se.teaterihuskvarna.member;

import org.jspecify.annotations.Nullable;

/// What an adapter is given about a member. Not the entity: `open-in-view` is
/// off, so a `Member` handed to a JTE template or a Jackson serialiser would
/// throw the moment either touched an unloaded association. A record built
/// inside the transaction cannot.
///
/// @param id         the register's identifier for this member
/// @param fullName   the member's name as the association writes it
/// @param email      the address of the member's account, or null when the member has no account
/// @param household  what the covering household is called, or null for none
public record MemberDetails(Long id, String fullName, @Nullable String email, @Nullable String household) {

    static MemberDetails of(Member member, @Nullable Account account) {
        Household household = member.getHousehold();
        return new MemberDetails(
                member.getId(),
                member.getFullName(),
                account == null ? null : account.getEmail(),
                household == null ? null : household.getName());
    }
}
