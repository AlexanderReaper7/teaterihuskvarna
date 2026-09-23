package se.teaterihuskvarna.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Service;

/// The rules that hold `web` and `api` apart and keep them in step. Reasoning in
/// `docs/decisions/0014-one-service-layer-two-adapters.md`.
class AdapterRulesTest {

    /// Written out rather than as `..web..`, because that pattern also matches
    /// `org.springframework.web.bind.annotation`, and so counts the
    /// `@RestController` annotation on an `api` class as a dependency on `web`.
    /// Seen to do exactly that on 2026-09-22.
    private static final String WEB = "se.teaterihuskvarna.web";
    private static final String API = "se.teaterihuskvarna.api";

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("se.teaterihuskvarna");

    @Test
    void anAdapterCannotReachTheDatabase() {
        noClasses().that().resideInAnyPackage(andBelow(WEB), andBelow(API))
                .should().dependOnClassesThat().areAssignableTo(Repository.class)
                .because("a capability has to be named in a service before an adapter can use it")
                .check(PRODUCTION);
    }

    @Test
    void anAdapterCannotHandleAnEntity() {
        noClasses().that().resideInAnyPackage(andBelow(WEB), andBelow(API))
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .because("open-in-view is off, so an entity outside its transaction throws"
                        + " the moment a template or a serialiser touches a lazy association")
                .check(PRODUCTION);
    }

    @Test
    void theAdaptersDoNotKnowAboutEachOther() {
        noClasses().that().resideInAPackage(andBelow(WEB))
                .should().dependOnClassesThat().resideInAPackage(andBelow(API))
                .because("they are peers over one service layer, not a stack")
                .check(PRODUCTION);
        noClasses().that().resideInAPackage(andBelow(API))
                .should().dependOnClassesThat().resideInAPackage(andBelow(WEB))
                .because("they are peers over one service layer, not a stack")
                .check(PRODUCTION);
    }

    /// The parity rule. Anything a JTE page can do, the REST API can do.
    ///
    /// Stated over service methods rather than over pages, because "render the
    /// start page" is not a capability an endpoint should have an equivalent
    /// of. One direction only: `api` may expose more than `web` does.
    @Test
    void everyCapabilityAPageUsesIsAlsoAnEndpoint() {
        Set<String> usedByPages = serviceMethodsCalledFrom(WEB);
        Set<String> servedByEndpoints = serviceMethodsCalledFrom(API);

        assertThat(servedByEndpoints)
                .as("service methods reachable from a JTE page but from no REST endpoint")
                .containsAll(usedByPages);
    }

    /// `se.teaterihuskvarna.web` and everything under it, in the syntax
    /// `resideInAPackage` expects.
    private static String andBelow(String packageName) {
        return packageName + "..";
    }

    private static Set<String> serviceMethodsCalledFrom(String adapterPackage) {
        Set<String> calls = new TreeSet<>();
        for (JavaClass adapter : PRODUCTION) {
            if (!adapter.getPackageName().startsWith(adapterPackage)) {
                continue;
            }
            adapter.getMethodCallsFromSelf().stream()
                    .filter(call -> call.getTargetOwner().isAnnotatedWith(Service.class))
                    .forEach(call -> calls.add(
                            call.getTargetOwner().getSimpleName() + "." + call.getName()));
        }
        return calls;
    }
}
