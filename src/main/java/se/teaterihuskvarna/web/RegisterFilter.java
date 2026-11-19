package se.teaterihuskvarna.web;

/// One choice above the member register, such as "Har inte betalat 2026".
///
/// @param label   what the link says
/// @param url     the register with this choice and the current search
/// @param current whether the register shows this choice now
public record RegisterFilter(String label, String url, boolean current) {
}
