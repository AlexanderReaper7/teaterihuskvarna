package se.teaterihuskvarna.mailing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Wires the [Brevo] the settings name.
@Configuration(proxyBeanMethods = false)
class BrevoConfiguration {

    @Bean
    Brevo brevo(BrevoSettings settings) {
        return switch (settings.api()) {
            case HTTP -> new HttpBrevo(settings);
            case FAKE -> new FakeBrevo();
        };
    }
}
