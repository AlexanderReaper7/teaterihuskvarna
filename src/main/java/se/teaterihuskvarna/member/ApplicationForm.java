package se.teaterihuskvarna.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/// What a visitor fills in on the Bli medlem form. The limits are the column
/// widths in `membership_application` and `member`, so a value that passes here
/// also fits in the database.
///
/// The messages are keys in `messages_sv.properties`, which Spring Boot's
/// validator reads through the application's `MessageSource`.
///
/// @param fullName   the applicant's name, required
/// @param email      the address the confirmation link goes to, required
/// @param phone      a phone number, or null or blank for none
/// @param address    a street address, or null or blank for none
/// @param postalCode a postal code, or null or blank for none
/// @param city       a city, or null or blank for none
public record ApplicationForm(
        @NotBlank(message = "{application.fullName.required}")
        @Size(max = 100, message = "{application.fullName.size}")
        String fullName,

        @NotBlank(message = "{application.email.required}")
        @Email(message = "{application.email.invalid}")
        @Size(max = 254, message = "{application.email.size}")
        String email,

        @Size(max = 32, message = "{application.phone.size}")
        @Nullable String phone,

        @Size(max = 200, message = "{application.address.size}")
        @Nullable String address,

        @Size(max = 10, message = "{application.postalCode.size}")
        @Nullable String postalCode,

        @Size(max = 100, message = "{application.city.size}")
        @Nullable String city) {
}
