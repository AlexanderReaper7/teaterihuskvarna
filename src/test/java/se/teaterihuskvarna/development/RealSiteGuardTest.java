package se.teaterihuskvarna.development;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/// [RealSiteGuard] in a context that holds nothing else, so each case is one
/// profile and one `SITE_URL`. `spring.profiles.active` is the property
/// `SPRING_PROFILES_ACTIVE` in `compose.dev.yaml` becomes.
///
/// That the whole application registers the guard under the profile, and
/// starts with it, is in [DevelopmentIndexIT].
class RealSiteGuardTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(RealSiteGuard.class);

    @Test
    void theDevProfileWithASiteUrlRefusesToStartAndSaysWhatToRemove() {
        context.withPropertyValues("spring.profiles.active=dev", "SITE_URL=https://teaterihuskvarna.se")
                .run(started -> assertThat(started).hasFailed().getFailure().rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("The dev profile is active")
                        .hasMessageContaining("SITE_URL is set, to https://teaterihuskvarna.se")
                        .hasMessageContaining("COMPOSE_FILE")
                        .hasMessageContaining(".env.production.example"));
    }

    @Test
    void theDevProfileAloneStarts() {
        context.withPropertyValues("spring.profiles.active=dev")
                .run(started -> assertThat(started).hasNotFailed().hasSingleBean(RealSiteGuard.class));
    }

    /// `compose.yaml` passes `SITE_URL` to the container even when `.env`
    /// leaves it empty, so the dev stack runs with the variable set to nothing.
    @Test
    void theDevProfileWithAnEmptySiteUrlStarts() {
        context.withPropertyValues("spring.profiles.active=dev", "SITE_URL=")
                .run(started -> assertThat(started).hasNotFailed().hasSingleBean(RealSiteGuard.class));
    }

    @Test
    void productionWithASiteUrlStartsWithoutTheGuard() {
        context.withPropertyValues("SITE_URL=https://teaterihuskvarna.se")
                .run(started -> assertThat(started).hasNotFailed().doesNotHaveBean(RealSiteGuard.class));
    }
}
