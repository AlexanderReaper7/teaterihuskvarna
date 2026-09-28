package se.teaterihuskvarna.member;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// What an adapter is given about a member. Not the entity: `open-in-view` is
/// off, so a `Member` handed to a JTE template or a Jackson serialiser would
/// throw the moment either touched an unloaded association. A record built
/// inside the transaction cannot.
///
/// @param id          the register's identifier for this member
/// @param fullName    the member's name as the association writes it
/// @param email       the address of the member's account, or null when the member has no account
/// @param phone       a phone number, or null
/// @param address     a street address, or null
/// @param postalCode  a postal code, or null
/// @param city        a city, or null
/// @param householdId the household the member is in, or null for none
/// @param household   what that household is called, or null for none
/// @param memberSince when the member was added to the register
/// @param fee         this year's fee status, the year taken in Sweden
public record MemberDetails(
        Long id,
        String fullName,
        @Nullable String email,
        @Nullable String phone,
        @Nullable String address,
        @Nullable String postalCode,
        @Nullable String city,
        @Nullable Long householdId,
        @Nullable String household,
        Instant memberSince,
        FeeStatus fee) {

    static MemberDetails of(Member member, @Nullable Account account, FeeStatus fee) {
        Household household = member.getHousehold();
        ContactDetails contact = member.getContact();
        return new MemberDetails(
                member.getId(),
                member.getFullName(),
                account == null ? null : account.getEmail(),
                contact.phone(),
                contact.address(),
                contact.postalCode(),
                contact.city(),
                household == null ? null : household.getId(),
                household == null ? null : household.getName(),
                member.getCreatedAt(),
                fee);
    }
}
