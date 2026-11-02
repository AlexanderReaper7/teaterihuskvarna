package se.teaterihuskvarna.web;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import se.teaterihuskvarna.content.Image;
import se.teaterihuskvarna.login.MailSettings;

/// Uses the page's existing content for sharing, without a second CMS field to maintain.
@Component
public final class PageMetadataFactory {

    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final int DESCRIPTION_LIMIT = 180;

    private final MailSettings mail;
    private final Copy copy;

    PageMetadataFactory(MailSettings mail, Copy copy) {
        this.mail = mail;
        this.copy = copy;
    }

    /// @param path the public page's path
    /// @param title its heading
    /// @param text its summary or safe body HTML
    /// @param image its main image, or null for the logo
    /// @param article whether it is a news article
    /// @return descriptions and absolute URLs for the public page
    public PageMetadata create(String path, String title, @Nullable String text, @Nullable Image image,
            boolean article) {
        String description = text == null ? "" : SPACE.matcher(
                HtmlUtils.htmlUnescape(TAGS.matcher(text).replaceAll(" "))).replaceAll(" ").strip();
        if (description.isEmpty()) {
            description = copy.text("metadata.defaultDescription");
        }
        if (description.length() > DESCRIPTION_LIMIT) {
            int end = description.lastIndexOf(' ', DESCRIPTION_LIMIT);
            description = description.substring(0, end > 0 ? end : DESCRIPTION_LIMIT) + "…";
        }
        return new PageMetadata(title, description, mail.link(path, null),
                image == null ? mail.link("/img/logo-negative.png", null) : image.url(),
                image == null ? copy.text("site.association") : image.alt(), article ? "article" : "website");
    }

    /// @param path a fixed public page's path
    /// @param titleKey the message key for its heading
    /// @param descriptionKey the message key for its description
    /// @return metadata for a public list or the membership application
    public PageMetadata fixed(String path, String titleKey, String descriptionKey) {
        return create(path, copy.text(titleKey), copy.text(descriptionKey), null, false);
    }
}
