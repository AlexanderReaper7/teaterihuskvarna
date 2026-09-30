package se.teaterihuskvarna.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/// The address to invite a household member at (R019). The account they
/// create gets this address.
///
/// @param email the address the invitation goes to
public record InvitationRequest(
        @NotBlank(message = "{administrator.email.required}")
        @Email(message = "{administrator.email.invalid}")
        @Size(max = 254, message = "{administrator.email.size}")
        String email) {
}
