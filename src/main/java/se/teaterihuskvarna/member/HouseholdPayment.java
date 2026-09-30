package se.teaterihuskvarna.member;

import java.time.Instant;

/// A household fee as [FeeRepository] reads it: which household it covers,
/// for which year, and when it was paid. A Spring Data projection, so the
/// method names must match the aliases in the queries.
interface HouseholdPayment {

    Long getHouseholdId();

    int getYear();

    Instant getPaidAt();
}
