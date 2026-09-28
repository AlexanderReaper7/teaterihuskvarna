package se.teaterihuskvarna.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// Facts about the association that the application repeats to members. The
/// bankgiro has no default: a guessed number sends a member's fee to somebody
/// else, so a production without one refuses to start. The fee amounts default
/// to the ones in `docs/projektplan-original.md`, 50 kr for one member and
/// 100 kr for a household.
///
/// @param bankgiro             the number fees are paid to, from `BANKGIRO`
/// @param feeIndividualOre     the yearly fee for one member, in öre
/// @param feeHouseholdOre      the yearly fee for a household, in öre
/// @param invitationLifetime   how long a link inviting a household member to create an account works
@Validated
@ConfigurationProperties("teaterihuskvarna.association")
public record AssociationSettings(
        @NotBlank String bankgiro,
        @DefaultValue("5000") @PositiveOrZero int feeIndividualOre,
        @DefaultValue("10000") @PositiveOrZero int feeHouseholdOre,
        @DefaultValue("7d") Duration invitationLifetime) {

    /// @param kind what the fee covers
    /// @return the configured amount for it, in öre
    public int feeOre(FeeKind kind) {
        return switch (kind) {
            case INDIVIDUAL -> feeIndividualOre;
            case HOUSEHOLD -> feeHouseholdOre;
        };
    }
}
