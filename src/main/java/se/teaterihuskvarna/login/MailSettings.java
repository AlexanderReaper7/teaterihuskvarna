package se.teaterihuskvarna.login;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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
}
