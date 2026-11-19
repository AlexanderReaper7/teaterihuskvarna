package se.teaterihuskvarna.content;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/// The `data-sanity` attributes of a preview page. Each tells the Studio's
/// Presentation tool which document field an element shows, so the tool lists
/// the documents on the page, outlines the element on a hover and opens the
/// field in the editor on a click: `docs/decisions/0021-content-from-sanity.md`.
///
/// Outside a preview, or without `SANITY_STUDIO_URL`, every method returns
/// null. A template leaves out an attribute whose value is null, so a
/// visitor's page carries none.
///
/// The value is what `createDataAttribute` in `@sanity/visual-editing-csm`
/// 3.0.19 writes: `id=<id>;type=<type>;path=<path>;base=<Studio address>`.
/// The path is in that package's address form, where `bildgalleri:<key>` is
/// the item of the array `bildgalleri` with that `_key`.
///
/// The Studio address loses any trailing slash. The overlays build the edit
/// link with `createEditUrl` from `@sanity/client`, which throws on one, and
/// the error unmounts the overlays and drops Presentation's connection on the
/// first hover.
public final class EditAttributes {

    private static final EditAttributes NONE = new EditAttributes(null);
    private static final Pattern TRAILING_SLASHES = Pattern.compile("/+$");

    private final @Nullable String base;

    private EditAttributes(@Nullable String base) {
        this.base = base;
    }

    /// @param perspective drafts in a preview, published otherwise
    /// @param studioUrl   the Studio's address, from `SANITY_STUDIO_URL`, or null
    /// @return attributes for a preview page, or ones that are all null
    public static EditAttributes of(Perspective perspective, @Nullable String studioUrl) {
        if (perspective != Perspective.DRAFTS || studioUrl == null || studioUrl.isBlank()) {
            return NONE;
        }
        return new EditAttributes(encodeUriComponent(TRAILING_SLASHES.matcher(studioUrl).replaceFirst("")));
    }

    /// @param document the document the element shows
    /// @param field    the field, such as `title`, in the document's schema
    /// @return the attribute's value, or null outside a preview
    public @Nullable String field(SanityDocument document, String field) {
        return value(document.id(), document.type(), field);
    }

    /// @param document the document the element shows
    /// @param field    an array field, such as `bildgalleri`
    /// @param key      the item's `_key`, or null when it has none
    /// @return the attribute's value, or null outside a preview or without a key
    public @Nullable String item(SanityDocument document, String field, @Nullable String key) {
        return key == null ? null : value(document.id(), document.type(), field + ":" + key);
    }

    /// @param id    the published document's id
    /// @param type  the document's type
    /// @param field a Portable Text field, such as `brodtext`
    /// @return the value for each of the field's blocks, by the block's `_key`, null outside a preview
    Function<@Nullable String, @Nullable String> blocks(String id, String type, String field) {
        return key -> key == null ? null : value(id, type, field + ":" + key);
    }

    private @Nullable String value(String id, String type, String path) {
        if (base == null) {
            return null;
        }
        return "id=" + id + ";type=" + type + ";path=" + path + ";base=" + base;
    }

    /// JavaScript's `encodeURIComponent`, which the Studio decodes the address
    /// with. [URLEncoder] differs in a space and in `!'()~`.
    private static String encodeUriComponent(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%21", "!")
                .replace("%27", "'")
                .replace("%28", "(")
                .replace("%29", ")")
                .replace("%7E", "~");
    }
}
