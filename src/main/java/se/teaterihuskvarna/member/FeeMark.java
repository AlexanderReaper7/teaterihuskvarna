package se.teaterihuskvarna.member;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.jspecify.annotations.Nullable;

/// What an administrator fills in to mark this year's fee paid (R019).
///
/// @param kind      whether the payment covers the member or their household
/// @param amountOre what was paid, in öre, or null for the configured amount for the kind
public record FeeMark(
        @NotNull(message = "{fee.kind.required}")
        @Nullable FeeKind kind,

        @PositiveOrZero(message = "{fee.amount.invalid}")
        @Max(value = 10_000_000, message = "{fee.amount.invalid}")
        @Nullable Integer amountOre) {
}
