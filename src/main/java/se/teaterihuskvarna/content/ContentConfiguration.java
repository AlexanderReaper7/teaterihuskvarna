package se.teaterihuskvarna.content;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/// Wires the [ContentSource] the settings name, and the cache in front of it.
@Configuration(proxyBeanMethods = false)
class ContentConfiguration {

    @Bean
    ContentSource contentSource(ContentSettings settings, ObjectMapper mapper,
            Clock clock) {
        return switch (settings.source()) {
            case SANITY -> new SanityContentSource(settings, mapper);
            case FIXTURE -> new FixtureContentSource(
                    settings.fixture() == null ? "content/fixture.json" : settings.fixture(), mapper, clock);
        };
    }

    @Bean
    ContentCache contentCache(ContentSource source, ContentSettings settings, Clock clock) {
        return new ContentCache(source, settings.maxAge(), clock);
    }

    @Bean
    Images images(ContentSettings settings) {
        return new Images(settings.projectId(), settings.dataset());
    }
}
