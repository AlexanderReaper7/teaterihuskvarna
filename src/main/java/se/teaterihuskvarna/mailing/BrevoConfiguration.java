package se.teaterihuskvarna.mailing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/// Wires the [Brevo] the settings name.
@Configuration(proxyBeanMethods = false)
class BrevoConfiguration {

    @Bean
    Brevo brevo(BrevoSettings settings, JdbcClient jdbc) {
        return switch (settings.api()) {
            case HTTP -> new HttpBrevo(settings);
            case FAKE -> new FakeBrevo(jdbc);
        };
    }
}
