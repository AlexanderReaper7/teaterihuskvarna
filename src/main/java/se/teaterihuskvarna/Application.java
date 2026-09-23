package se.teaterihuskvarna;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/// Boots the website and the member register. One application serves the public
/// pages, the member pages and the administration pages: `docs/projektplan.md`.
///
/// Scheduling runs the jobs that delete expired login links, expired membership
/// applications and old rate limit rows. The properties scan picks up the
/// settings records, such as `MailSettings`, without listing each one here.
///
/// Boot's `UserDetailsServiceAutoConfiguration` is off. With no
/// `UserDetailsService` bean it would create an in-memory user named `user`
/// with a generated password, a login nobody asked for. Each login kind builds
/// its own lookup in `SecurityConfiguration` instead.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class Application {

    /// @param args passed through to Spring Boot, which reads them as properties
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
