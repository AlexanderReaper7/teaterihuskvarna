package se.teaterihuskvarna.administrator;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/// What an administrator fills in to add another. The limits are the column
/// widths in `administrator`. The messages are keys in `messages_sv.properties`.
///
/// @param email    the new administrator's address, required
/// @param fullName the new administrator's name, required
public record NewAdministrator(
        @NotBlank(message = "{administrator.email.required}")
        @Email(message = "{administrator.email.invalid}")
        @Size(max = 254, message = "{administrator.email.size}")
        String email,

        @NotBlank(message = "{administrator.fullName.required}")
        @Size(max = 100, message = "{administrator.fullName.size}")
        String fullName) {
}
