package se.teaterihuskvarna.member;

/// Which members a register search lists, by this year's fee (R019).
public enum FeeFilter {
    /// Everyone.
    ALL,
    /// Those whose fee is paid this year, by a payment of their own or their household's.
    PAID,
    /// Those whose fee is not paid this year.
    UNPAID;

    boolean admits(FeeStatus status) {
        return this == ALL || status.paid() == (this == PAID);
    }
}
