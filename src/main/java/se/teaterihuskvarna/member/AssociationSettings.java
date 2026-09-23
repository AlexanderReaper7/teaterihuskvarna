package se.teaterihuskvarna.member;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// Facts about the association that the application repeats to members. No
/// defaults: a guessed bankgiro number sends a new member's fee to somebody
/// else, so a production without one refuses to start.
///
/// @param bankgiro the number fees are paid to, from `BANKGIRO`
@Validated
@ConfigurationProperties("teaterihuskvarna.association")
public record AssociationSettings(@NotBlank String bankgiro) {
}
