package se.teaterihuskvarna.content;

/// A series of events, such as Kaffe med drömmar, which the calendar filters
/// by: R002. Editors add series in the Studio, so the list is not fixed.
///
/// @param id    the published document's id
/// @param slug  the series in the calendar's address, `/kalender?serie=<slug>`
/// @param title its name
public record Series(String id, String slug, String title) implements SanityDocument {

    @Override
    public String type() {
        return "serie";
    }
}
