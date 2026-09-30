package se.teaterihuskvarna.content;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;

/// Builds image addresses from the asset reference Sanity stores, such as
/// `image-Tb9Ew8CXIwaY6R1kjMvI0uRR-2000x3000-jpg`, which names the file and its
/// size. Sanity documents the address as
/// `https://cdn.sanity.io/images/<project>/<dataset>/<id>-<w>x<h>.<ext>`, and
/// its `w` parameter scales the picture down, so no query to Sanity is needed.
final class Images {

    /// Wide enough for the text column on the user's 2560 px view at 1.5 times
    /// zoom, and for a phone's screen at three times its width in CSS pixels.
    static final int WIDTH = 1200;

    private static final Pattern REFERENCE = Pattern.compile("image-([A-Za-z0-9]+)-(\\d+)x(\\d+)-([a-z0-9]+)");

    private final String base;

    /// @param projectId the Sanity project
    /// @param dataset   the dataset the images belong to
    Images(String projectId, String dataset) {
        this.base = "https://cdn.sanity.io/images/" + UriUtils.encodePathSegment(projectId, "UTF-8") + "/"
                + UriUtils.encodePathSegment(dataset, "UTF-8") + "/";
    }

    /// @param image a Sanity image field, with `asset._ref` and maybe `alt`
    /// @return the picture, or null when the field holds no usable reference
    @Nullable Image of(@Nullable JsonNode image) {
        if (image == null) {
            return null;
        }
        String reference = Json.text(image.path("asset"), "_ref");
        if (reference == null) {
            return null;
        }
        Matcher parts = REFERENCE.matcher(reference);
        if (!parts.matches()) {
            return null;
        }
        int width = Integer.parseInt(parts.group(2));
        int height = Integer.parseInt(parts.group(3));
        if (width <= 0 || height <= 0) {
            return null;
        }
        int shownWidth = Math.min(width, WIDTH);
        int shownHeight = (int) Math.round((double) height * shownWidth / width);
        String url = base + parts.group(1) + "-" + width + "x" + height + "." + parts.group(4)
                + "?w=" + shownWidth + "&auto=format";
        String alt = Json.text(image, "alt");
        return new Image(url, alt == null ? "" : alt, shownWidth, shownHeight);
    }
}
