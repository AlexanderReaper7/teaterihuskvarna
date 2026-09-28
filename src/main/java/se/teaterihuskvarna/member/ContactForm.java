package se.teaterihuskvarna.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/// What a member may change about themselves on `/medlem/kontaktuppgifter`
/// (R012): everything but the address, which is their login. The limits are
/// the column widths in `member`, and the messages the ones the Bli medlem form
/// uses for the same fields.
///
/// @param fullName   the member's name, required
/// @param phone      a phone number, or null or blank for none
/// @param address    a street address, or null or blank for none
/// @param postalCode a postal code, or null or blank for none
/// @param city       a city, or null or blank for none
public record ContactForm(
        @NotBlank(message = "{application.fullName.required}")
        @Size(max = 100, message = "{application.fullName.size}")
        String fullName,

        @Size(max = 32, message = "{application.phone.size}")
        @Nullable String phone,

        @Size(max = 200, message = "{application.address.size}")
        @Nullable String address,

        @Size(max = 10, message = "{application.postalCode.size}")
        @Nullable String postalCode,

        @Size(max = 100, message = "{application.city.size}")
        @Nullable String city) {

    /// @param member the member as they are now
    /// @return a form holding their current values
    public static ContactForm of(MemberDetails member) {
        return new ContactForm(member.fullName(), member.phone(), member.address(), member.postalCode(),
                member.city());
    }

    ContactDetails contact() {
        return new ContactDetails(Blank.toNull(phone), Blank.toNull(address), Blank.toNull(postalCode),
                Blank.toNull(city));
    }
}
