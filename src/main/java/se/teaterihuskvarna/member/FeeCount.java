package se.teaterihuskvarna.member;

/// How many members have paid this year's fee, for the administrators' start
/// page (R019).
///
/// @param year   the fee year
/// @param paid   the members whose fee is paid, themselves or through their household
/// @param unpaid the members whose fee is not
public record FeeCount(int year, long paid, long unpaid) {
}
