package se.teaterihuskvarna.mailing;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// How the application reaches Brevo's API for contacts and mailings. Login
/// mail goes over SMTP and does not read this.
///
/// With `api` set to `http`, [BrevoConfiguration] refuses to start unless the
/// key, members' list, test list and sender are set, since neither the
/// contacts nor a mailing work without them.
///
/// @param api         `http` for the real account, `fake` to keep every call in memory
/// @param apiKey      the API key, from `BREVO_API_KEY`
/// @param apiUrl      the API's address, which only a test changes
/// @param listId      the list every member with an account is on, from `BREVO_LIST_ID`
/// @param testListId  the list an administrator's address joins before a test mail, from `BREVO_TEST_LIST_ID`
/// @param senderName  the name mailings come from, from `BREVO_SENDER_NAME`
/// @param senderEmail the address mailings come from, verified as a sender in Brevo, from `BREVO_SENDER_EMAIL`
@Validated
@ConfigurationProperties("teaterihuskvarna.brevo")
public record BrevoSettings(
        @NotNull Api api,
        @Nullable String apiKey,
        @NotNull URI apiUrl,
        @Nullable Long listId,
        @Nullable Long testListId,
        @Nullable String senderName,
        @Nullable String senderEmail) {

    /// Which [Brevo] runs.
    public enum Api {
        /// Brevo's HTTP API.
        HTTP,
        /// In memory, for development and the tests.
        FAKE
    }
}
