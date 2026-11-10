package se.teaterihuskvarna.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import se.teaterihuskvarna.login.AccountId;
import se.teaterihuskvarna.login.AdministratorId;
import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.SignedIn;
import se.teaterihuskvarna.member.MemberId;

/// A member's id, an account's and an administrator's each have a type:
/// `MemberId`, `AccountId` and `AdministratorId`. The compiler then refuses
/// one where another belongs. These rules keep a bare number from coming
/// back in beside them.
///
/// The compiler has help from three Error Prone checks, turned on in
/// `pom.xml`. `equals`, `Map.get` and `contains` take any object, so an id
/// compared with a number compiles and is never equal, and `==` on two ids
/// compares the objects. The checks refuse those, in tests too.
class IdRulesTest {

    private static final String LOGIN = "se.teaterihuskvarna.login";

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("se.teaterihuskvarna");

    private static final List<Class<?>> ID_TYPES = IdTypes.in(PRODUCTION);

    /// `memberId`, `ownerMemberId`, `accountIds` and the like: a name that ends in the name of an id
    /// type. The types are found and not listed, so a `HouseholdId` would bring every
    /// `long householdId` under the rule.
    private static final Pattern NAMED_AS_AN_ID = Pattern.compile("(?i).*(%s)s?".formatted(
            ID_TYPES.stream().map(Class::getSimpleName).collect(Collectors.joining("|"))));

    /// The three the rules were written for. If the search for id types stopped finding them, every
    /// rule below would pass by reading nothing.
    @Test
    void theIdTypesAreFound() {
        assertThat(ID_TYPES).contains(MemberId.class, AccountId.class, AdministratorId.class);
    }

    /// A parameter, a field or a record component that is named as one of the
    /// three ids and is a `long`, a `Long` or a collection of them. A new
    /// method that takes `long memberId` compiles, and every caller would
    /// have to unwrap its id to call it, which is where two ids get mixed up
    /// again.
    ///
    /// An entity keeps the number its column holds, and a repository takes
    /// the number its query compares with, so neither is read here. Their
    /// packages hide both from the adapters.
    @Test
    void nothingNamedAsAnIdOfAPersonIsABareNumber() {
        Set<String> wrong = new TreeSet<>();
        int parameters = 0;
        for (JavaClass imported : PRODUCTION) {
            if (imported.isAnnotatedWith(Entity.class) || imported.isAssignableTo(Repository.class)) {
                continue;
            }
            Class<?> type = imported.reflect();
            for (Field field : type.getDeclaredFields()) {
                if (!field.isSynthetic() && isABareId(field.getName(), field.getGenericType())) {
                    wrong.add("%s.%s".formatted(type.getSimpleName(), field.getName()));
                }
            }
            List<Executable> called = new ArrayList<>(List.of(type.getDeclaredMethods()));
            called.addAll(List.of(type.getDeclaredConstructors()));
            for (Executable method : called) {
                if (method.isSynthetic()) {
                    continue;
                }
                for (Parameter parameter : method.getParameters()) {
                    if (parameter.isSynthetic() || parameter.isImplicit()) {
                        continue;
                    }
                    assertThat(parameter.isNamePresent())
                            .as("%s.%s is compiled with its parameter names", type.getSimpleName(), method.getName())
                            .isTrue();
                    parameters++;
                    if (isABareId(parameter.getName(), parameter.getParameterizedType())) {
                        wrong.add("%s.%s(%s)".formatted(type.getSimpleName(), method.getName(), parameter.getName()));
                    }
                }
            }
        }

        assertThat(wrong).as("named as a member's, an account's or an administrator's id, and a bare number")
                .isEmpty();
        assertThat(parameters).as("parameters read, so the test cannot pass by finding none").isGreaterThan(500);
    }

    /// `SignedIn` holds its id as a number, for the session's sake, and gives
    /// it out as an `AccountId` or an `AdministratorId`. Reading the number,
    /// or pairing a number with a kind by hand, is how the logged-in
    /// account's id was once passed as a member's.
    @Test
    void onlyTheLoginPackageReadsOrWritesTheNumberInALogin() {
        noClasses().that().resideOutsideOfPackage(LOGIN)
                .should().callMethod(SignedIn.class, "id")
                .orShould().callConstructor(SignedIn.class, LoginKind.class, long.class, String.class, String.class)
                .check(PRODUCTION);
    }

    /// An id is made from a number where the number is known to be that kind
    /// of id: in the entity whose column holds it, in `SignedIn`, whose kind
    /// says which it is, and in the type's own `valueOf`, which reads what a
    /// request or an outbox row carries. Anywhere else, `new MemberId(n)`
    /// would turn any number at hand into a member's id, the account's
    /// included, and the compiler would have nothing left to refuse.
    @Test
    void anIdIsMadeFromANumberOnlyWhereTheNumberIsKnown() {
        Set<String> wrong = new TreeSet<>();
        int made = 0;
        for (JavaClass imported : PRODUCTION) {
            for (JavaConstructorCall call : imported.getConstructorCallsFromSelf()) {
                JavaClass target = call.getTargetOwner();
                if (ID_TYPES.stream().noneMatch(target::isEquivalentTo)) {
                    continue;
                }
                made++;
                JavaClass maker = call.getOriginOwner();
                boolean known = maker.isAnnotatedWith(Entity.class) || maker.isEquivalentTo(SignedIn.class)
                        || maker.equals(target);
                if (!known) {
                    wrong.add("%s makes a %s at line %d".formatted(maker.getSimpleName(), target.getSimpleName(),
                            call.getLineNumber()));
                }
            }
        }

        assertThat(wrong).isEmpty();
        assertThat(made).as("ids made, so the test cannot pass by finding none").isGreaterThan(10);
    }

    private static boolean isABareId(String name, Type type) {
        String written = type.getTypeName();
        return NAMED_AS_AN_ID.matcher(name).matches()
                && (written.equals("long") || written.contains("java.lang.Long"));
    }
}
