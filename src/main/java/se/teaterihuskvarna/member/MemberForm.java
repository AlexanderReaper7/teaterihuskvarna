package se.teaterihuskvarna.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/// What an administrator fills in to add a member or edit one (R018, R019).
///
/// An address gives the member an account they can log in with. Without one
/// the member is in the register, and in a household if one is chosen, but
/// cannot log in until an invitation is accepted. The household is how an
/// administrator adds a member to a household or takes them out of one.
///
/// @param fullName    the member's name, required
/// @param email       the account's address, or null or blank for no account
/// @param phone       a phone number, or null or blank for none
/// @param address     a street address, or null or blank for none
/// @param postalCode  a postal code, or null or blank for none
/// @param city        a city, or null or blank for none
/// @param householdId the household to be in, or null for none
public record MemberForm(
        @NotBlank(message = "{administrator.fullName.required}")
        @Size(max = 100, message = "{administrator.fullName.size}")
        String fullName,

        @Email(message = "{administrator.email.invalid}")
        @Size(max = 254, message = "{administrator.email.size}")
        @Nullable String email,

        @Size(max = 32, message = "{application.phone.size}")
        @Nullable String phone,

        @Size(max = 200, message = "{application.address.size}")
        @Nullable String address,

        @Size(max = 10, message = "{application.postalCode.size}")
        @Nullable String postalCode,

        @Size(max = 100, message = "{application.city.size}")
        @Nullable String city,

        @Nullable Long householdId) {

    /// @return an empty form, for adding a member
    public static MemberForm empty() {
        return new MemberForm("", "", "", "", "", "", null);
    }

    /// @param member the member as they are now
    /// @return a form holding their current values, for editing
    public static MemberForm of(MemberDetails member) {
        return new MemberForm(member.fullName(), member.email(), member.phone(), member.address(),
                member.postalCode(), member.city(), member.householdId());
    }

    ContactDetails contact() {
        return new ContactDetails(Blank.toNull(phone), Blank.toNull(address), Blank.toNull(postalCode),
                Blank.toNull(city));
    }
}
