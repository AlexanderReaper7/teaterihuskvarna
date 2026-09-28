package se.teaterihuskvarna.member;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// Whether a member's fee for one year is paid, and by what.
///
/// Paid means a payment of the member's own for the year, of either kind, or
/// a [FeeKind#HOUSEHOLD] payment for the year by someone in the member's
/// household now. The member's own payment wins when both exist. The rule is
/// the user's, of 2026-09-28.
///
/// @param year             the year
/// @param paidAt           when the covering payment was marked, or null when unpaid
/// @param kind             what the covering payment was, or null when unpaid
/// @param throughHousehold true when another household member's household payment is what covers it
/// @param payment          how to pay, when unpaid; null when paid
public record FeeStatus(
        int year,
        @Nullable Instant paidAt,
        @Nullable FeeKind kind,
        boolean throughHousehold,
        @Nullable PaymentInstruction payment) {

    /// @return true when the year is paid
    public boolean paid() {
        return paidAt != null;
    }
}
