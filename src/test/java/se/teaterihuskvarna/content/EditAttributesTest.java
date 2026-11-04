package se.teaterihuskvarna.content;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/// The Studio's overlays read a `data-sanity` value only in the format
/// `@sanity/visual-editing-csm` writes. Each expected value below is what that
/// package's `createDataAttribute` 3.0.19 returned for the same document, field
/// and Studio address, run in node on 2026-11-04.
class EditAttributesTest {

    private static final SanityDocument NEWS = document("nyhet-ny-webbplats", "nyhet");
    private static final SanityDocument EVENT = document("evenemang-mitt-i-veckan", "evenemang");

    @Test
    void aFieldIsWrittenAsSanityWritesIt() {
        EditAttributes edit = EditAttributes.of(Perspective.DRAFTS, "https://studio.example.test/studio");

        assertThat(edit.field(NEWS, "title"))
                .isEqualTo("id=nyhet-ny-webbplats;type=nyhet;path=title"
                        + ";base=https%3A%2F%2Fstudio.example.test%2Fstudio");
    }

    /// `createEditUrl` in `@sanity/client` 8.14.1, which the overlays call on a
    /// hover, throws on a Studio address that ends in a slash.
    @Test
    void aTrailingSlashIsDropped() {
        EditAttributes edit = EditAttributes.of(Perspective.DRAFTS, "https://studio.example.test/studio//");

        assertThat(edit.field(NEWS, "title"))
                .isEqualTo("id=nyhet-ny-webbplats;type=nyhet;path=title"
                        + ";base=https%3A%2F%2Fstudio.example.test%2Fstudio");
    }

    @Test
    void anArrayItemIsAddressedByItsKey() {
        EditAttributes edit = EditAttributes.of(Perspective.DRAFTS, "https://teaterihuskvarna.sanity.studio");

        assertThat(edit.item(EVENT, "bildgalleri", "g1"))
                .isEqualTo("id=evenemang-mitt-i-veckan;type=evenemang;path=bildgalleri:g1"
                        + ";base=https%3A%2F%2Fteaterihuskvarna.sanity.studio");
        assertThat(edit.item(EVENT, "bildgalleri", null)).isNull();
    }

    @Test
    void theStudioAddressIsEncodedAsJavaScriptEncodesIt() {
        EditAttributes edit = EditAttributes.of(Perspective.DRAFTS, "https://example.test/(sv)~!'*");

        assertThat(edit.blocks("sida-kontakt", "sida", "innehall").apply("p"))
                .isEqualTo("id=sida-kontakt;type=sida;path=innehall:p"
                        + ";base=https%3A%2F%2Fexample.test%2F(sv)~!'*");
    }

    @Test
    void aVisitorsPageGetsNone() {
        EditAttributes published = EditAttributes.of(Perspective.PUBLISHED, "https://studio.example.test/studio/");
        EditAttributes noStudio = EditAttributes.of(Perspective.DRAFTS, " ");

        assertThat(published.field(NEWS, "title")).isNull();
        assertThat(published.blocks("sida-kontakt", "sida", "innehall").apply("p")).isNull();
        assertThat(noStudio.field(NEWS, "title")).isNull();
    }

    private static SanityDocument document(String id, String type) {
        return new SanityDocument() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String type() {
                return type;
            }
        };
    }
}
