package se.teaterihuskvarna.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// Where the public content comes from: `docs/decisions/0006-sanity-headless-cms.md`.
///
/// No defaults for the project or dataset, so a production without them refuses
/// to start rather than showing an empty site. `application-dev.yaml` reads
/// fixtures instead unless `CONTENT_SOURCE=sanity`.
///
/// @param source        `sanity` for the real dataset, `fixture` for [#fixture]
/// @param projectId     the Sanity project, from `SANITY_PROJECT_ID`
/// @param dataset       the dataset, from `SANITY_DATASET_NAME`
/// @param token         a read token, from `SANITY_API_KEY`: required for a private dataset and for drafts
/// @param webhookSecret the secret Sanity signs its webhook with, from `SANITY_WEBHOOK_SECRET`
/// @param maxAge        how long a fetched document list is shown before it is fetched again.
///                      R008 allows a minute at most, and the webhook usually clears it sooner
/// @param fixture       the classpath file the `fixture` source reads
/// @param studioUrl     the Studio's origin, such as `https://teaterihuskvarna.sanity.studio`, from
///                      `SANITY_STUDIO_URL`. Public pages allow only it to show them in a frame, for
///                      the Presentation preview. Null or blank allows no other site
@Validated
@ConfigurationProperties("teaterihuskvarna.content")
public record ContentSettings(
        @NotNull Source source,
        @NotBlank String projectId,
        @NotBlank String dataset,
        @Nullable String token,
        @Nullable String webhookSecret,
        @NotNull @DurationMin(seconds = 1) @DurationMax(seconds = 60) Duration maxAge,
        @Nullable String fixture,
        @Nullable @Pattern(regexp = "(https?://[^/?#\\s]+)?") String studioUrl) {

    /// Which [ContentSource] runs.
    public enum Source {
        /// Sanity's query API.
        SANITY,
        /// A JSON file in the jar, for development without a Sanity project and for the e2e suite.
        FIXTURE
    }
}
