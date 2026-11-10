package se.teaterihuskvarna;

import gg.jte.generated.precompiled.StaticTemplates;
import gg.jte.generated.precompiled.Templates;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// The templates, as the build generates them from `src/main/jte`: one
/// method on `Templates` for each page and each mail.
///
/// `StaticTemplates` calls the compiled template classes directly. Its
/// sibling `DynamicTemplates` looks a template up by its file name at run
/// time, to reload it while developing, and nothing here uses it: the
/// application runs from an image in which the templates are compiled.
@Configuration(proxyBeanMethods = false)
class TemplatesConfiguration {

    /// @return the templates
    @Bean
    Templates templates() {
        return new StaticTemplates();
    }
}
