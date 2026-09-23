package se.teaterihuskvarna;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// Boots the website and the member register. One application serves the public
/// pages, the member pages and the administration pages: `docs/projektplan.md`.
@SpringBootApplication
public class Application {

    /// @param args passed through to Spring Boot, which reads them as properties
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
