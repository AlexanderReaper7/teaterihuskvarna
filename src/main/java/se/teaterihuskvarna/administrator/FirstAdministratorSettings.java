package se.teaterihuskvarna.administrator;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// The administrator account created at startup when the table is empty. Read
/// once, on a fresh database; after that the values are ignored, and removing
/// them from the configuration changes nothing. [FirstAdministrator] refuses to
/// start when they are needed and missing.
///
/// @param email    the first administrator's address, from `FIRST_ADMINISTRATOR_EMAIL`
/// @param fullName the first administrator's name, from `FIRST_ADMINISTRATOR_NAME`
@ConfigurationProperties("teaterihuskvarna.first-administrator")
public record FirstAdministratorSettings(@Nullable String email, @Nullable String fullName) {
}
