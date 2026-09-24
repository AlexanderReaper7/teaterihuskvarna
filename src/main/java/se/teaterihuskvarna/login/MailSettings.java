package se.teaterihuskvarna.login;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.util.UriComponentsBuilder;

/// Where links in mail point and who mail comes from. No defaults: a link built
/// from a guessed address points somewhere else, so a production without these
/// set refuses to start. `application-dev.yaml` sets them locally.
///
/// The site address comes from configuration and never from the request's Host
/// header, which the client controls: a forged header would otherwise put an
/// attacker's host in a real login link.
///
/// @param siteUrl the public address of the site, such as `https://teaterihuskvarna.se`
/// @param from    the sender address on every mail
@Validated
@ConfigurationProperties("teaterihuskvarna.mail")
public record MailSettings(@NotNull URI siteUrl, @NotBlank String from) {

    /// @param path  a path on the site, such as `/logga-in`
    /// @param token the token the page reads from `?token=`, or null for a plain link
    /// @return the full address to put in a mail
    public String link(String path, @Nullable String token) {
        UriComponentsBuilder link = UriComponentsBuilder.fromUri(siteUrl).path(path);
        if (token != null) {
            link.queryParam("token", token);
        }
        return link.build().toUriString();
    }
}
