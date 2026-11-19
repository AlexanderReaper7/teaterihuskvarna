package se.teaterihuskvarna.login;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/// Tests how LifeTimes turns a duration into swedish words.
class LifetimesTest {

    private final ResourceBundleMessageSource messages = createMessages();

    private static ResourceBundleMessageSource createMessages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }

    @Test
    void oneMinute() {
        assertEquals("en minut", Lifetimes.describe(messages, Duration.ofMinutes(1)));
    }

    @Test
    void fifteenMinute() {
        assertEquals("15 minuter", Lifetimes.describe(messages, Duration.ofMinutes(15)));
    }

    @Test
    void ninetyMinutesStayMinutes() {
        assertEquals("90 minuter", Lifetimes.describe(messages, Duration.ofMinutes(90)));
    }

    @Test
    void oneHour() {
        assertEquals("en timme", Lifetimes.describe(messages, Duration.ofHours(1)));
    }

    @Test
    void threeHours() {
        assertEquals("3 timmar", Lifetimes.describe(messages, Duration.ofHours(3)));
    }

    @Test
    void twentyFiveHoursStayHours() {
        assertEquals("25 timmar", Lifetimes.describe(messages, Duration.ofHours(25)));
    }

    @Test
    void twentyFourHoursIsOneDay() {
        assertEquals("ett dygn", Lifetimes.describe(messages, Duration.ofHours(24)));
    }

    @Test
    void sevenDays() {
        assertEquals("7 dygn", Lifetimes.describe(messages, Duration.ofDays(7)));
    }
}
