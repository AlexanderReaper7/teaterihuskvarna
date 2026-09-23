package se.teaterihuskvarna.web;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

/// Fixed Swedish copy lives in `messages_sv.properties`, per
/// `docs/decisions/0001-language-policy.md`.
///
/// This bean is declared rather than auto-configured on purpose. Spring's
/// `MessageSourceAutoConfiguration` only contributes a `MessageSource` when a
/// base `messages.properties` exists, and this project has only the Swedish
/// bundle. Relying on the auto-configuration therefore means an empty file whose
/// only job is to satisfy a condition, and a missing bundle that fails at render
/// time instead of at startup.
@Configuration(proxyBeanMethods = false)
public class MessagesConfiguration {

    /// @return the message source the controllers read Swedish copy from
    @Bean
    MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }
}
