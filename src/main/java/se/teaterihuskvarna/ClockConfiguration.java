package se.teaterihuskvarna;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// The clock that decides what "today" and "this year" are, so a test can
/// replace it. Code that asks `Instant.now()` directly cannot be tested at a
/// day's boundary.
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

    /// @return the system clock in UTC; code that needs Sweden's date converts
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
