package se.teaterihuskvarna.member;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.jspecify.annotations.Nullable;

/// How to reach a member other than by email, which is on the [Account]
/// because a member added to a household may have none. [Member] and
/// [MembershipApplication] both embed it, in columns of their own tables. Every
/// part is optional.
///
/// Column lengths mirror `V1__member_register.sql` and `V2__login.sql` by hand:
/// `docs/decisions/0012-jpa-over-a-schema-flyway-owns.md`.
///
/// @param phone      a phone number, or null
/// @param address    a street address, or null
/// @param postalCode a postal code, or null
/// @param city       a city, or null
@Embeddable
public record ContactDetails(
        @Column(name = "phone", length = 32) @Nullable String phone,
        @Column(name = "address", length = 200) @Nullable String address,
        @Column(name = "postal_code", length = 10) @Nullable String postalCode,
        @Column(name = "city", length = 100) @Nullable String city) {

    /// No way to reach them but email. Hibernate reads an embeddable whose
    /// columns are all null as null, and the entities return this instead.
    public static final ContactDetails NONE = new ContactDetails(null, null, null, null);
}
