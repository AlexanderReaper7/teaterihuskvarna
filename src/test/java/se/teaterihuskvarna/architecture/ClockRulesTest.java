package se.teaterihuskvarna.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitCallTarget;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.time.Clock;
import java.util.Date;
import org.junit.jupiter.api.Test;

/// Production code reads the time from the application's `Clock` bean and from
/// nowhere else, so a test can stop the clock at midnight or on New Year's
/// night. Finding C8 of the review of 2026-11-10 was a fee year read from the
/// system clock, which no test could reach.
///
/// The rule sees Java only. A query that says `now()` reads the database's
/// clock, and the outbox and the login tokens do.
class ClockRulesTest {

    private static final String CLOCK_BEAN = "se.teaterihuskvarna.ClockConfiguration";

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("se.teaterihuskvarna");

    @Test
    void onlyTheClockBeanReadsTheSystemClock() {
        noClasses().that().doNotHaveFullyQualifiedName(CLOCK_BEAN)
                .should().callCodeUnitWhere(readsTheSystemClock())
                .because("a class that needs the time takes the Clock bean and asks it, as in clock.instant()"
                        + " or LocalDate.now(clock), and an entity is handed the moment by the service that"
                        + " creates or changes it")
                .check(PRODUCTION);
    }

    /// `Instant.now()`, `LocalDate.now(zone)` and the like, which is every
    /// `now` in `java.time` that is not given a `Clock`; the `Clock.system`
    /// factories; `System.currentTimeMillis()`; and `new Date()`.
    private static DescribedPredicate<JavaCall<?>> readsTheSystemClock() {
        return new DescribedPredicate<>("reads the system clock") {
            @Override
            public boolean test(JavaCall<?> call) {
                CodeUnitCallTarget target = call.getTarget();
                JavaClass owner = target.getOwner();
                String name = target.getName();
                if (owner.getPackageName().equals("java.time")) {
                    boolean givenAClock = target.getRawParameterTypes().stream()
                            .anyMatch(parameter -> parameter.isEquivalentTo(Clock.class));
                    return "now".equals(name) && !givenAClock
                            || owner.isEquivalentTo(Clock.class) && name.startsWith("system");
                }
                if (owner.isEquivalentTo(System.class)) {
                    return "currentTimeMillis".equals(name);
                }
                return owner.isEquivalentTo(Date.class) && "<init>".equals(name)
                        && target.getRawParameterTypes().isEmpty();
            }
        };
    }
}
