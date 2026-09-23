package se.teaterihuskvarna.member;

/// What an adapter is given about a member. Not the entity: `open-in-view` is
/// off, so a `Member` handed to a JTE template or a Jackson serialiser would
/// throw the moment either touched an unloaded association. A record built
/// inside the transaction cannot.
///
/// @param id         the register's identifier for this member
/// @param fullName   the member's name as the association writes it
/// @param email      the address login links and mailings go to
/// @param household  what the covering household is called, or null for none
public record MemberDetails(Long id, String fullName, String email, String household) {

    static MemberDetails of(Member member) {
        Household household = member.getHousehold();
        return new MemberDetails(
                member.getId(),
                member.getFullName(),
                member.getEmail(),
                household == null ? null : household.getName());
    }
}
