package se.teaterihuskvarna.content;

/// A series of events, such as Kaffe med drömmar, which the calendar filters
/// by: R002. Editors add series in the Studio, so the list is not fixed.
///
/// @param slug  the series in the calendar's address, `/kalender?serie=<slug>`
/// @param title its name
public record Series(String slug, String title) {
}
