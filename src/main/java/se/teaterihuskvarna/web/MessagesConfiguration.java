package se.teaterihuskvarna.web;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import se.teaterihuskvarna.Swedish;

/// Fixed Swedish copy lives in `messages_sv.properties`, per
/// `docs/decisions/0001-language-policy.md`.
///
/// This bean is declared rather than auto-configured on purpose. Spring's
/// `MessageSourceAutoConfiguration` only contributes a `MessageSource` when a
/// base `messages.properties` exists, and this project has only the Swedish
/// bundle. Relying on the auto-configuration therefore means an empty file whose
/// only job is to satisfy a condition, and a missing bundle that fails at render
/// time instead of at startup.
///
/// There is no validator bean here, on purpose. Boot 4.1.1's
/// `ValidationAutoConfiguration.defaultValidator` already builds a
/// `LocalValidatorFactoryBean` whose interpolator looks `{application.fullName.required}`
/// up in the application context's `MessageSource`, which is this bean, and method
/// validation on `@Validated` services uses that same validator. Declaring a
/// `LocalValidatorFactoryBean` here would switch the auto-configured one off, since
/// it is `@ConditionalOnMissingBean`, and drop Boot's `ValidationConfigurationCustomizer`
/// hook with it. Read from the 4.1.1 bytecode on 2026-09-23, not yet seen in a running test.
@Configuration(proxyBeanMethods = false)
public class MessagesConfiguration {

    /// The default locale matters for validation messages. The validator resolves them
    /// in the locale of the request, so a browser asking for English would find no
    /// `messages_en` bundle, no base bundle either, and show the raw
    /// `{application.fullName.required}`. With Swedish as the default, every locale
    /// falls back to the one bundle there is.
    ///
    /// @return the message source the templates and the validator read Swedish copy from
    @Bean
    MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(Swedish.LOCALE);
        return source;
    }

    /// Declared here rather than as a `@Component` so that a `@WebMvcTest` which
    /// imports this class gets it too; the web slice does not scan components.
    ///
    /// @param messageSource the Swedish copy
    /// @return the copy every template receives
    @Bean
    Copy copy(MessageSource messageSource) {
        return new Copy(messageSource);
    }
}
