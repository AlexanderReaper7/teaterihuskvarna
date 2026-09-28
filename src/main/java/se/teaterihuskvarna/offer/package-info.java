/// Offers and registrations: what a member can sign up for (R014), and what an
/// administrator sees and exports of who signed up (R020).
///
/// Everything goes through [OfferService]. Both tables live in PostgreSQL, so
/// that taking a place and checking the capacity happen in one transaction.
package se.teaterihuskvarna.offer;
