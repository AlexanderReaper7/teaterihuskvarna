package se.teaterihuskvarna.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

/// Code that deletes an account or removes an administrator also removes that
/// login's passkeys and ends its sessions, in the same method.
///
/// Spring's passkey tables name the owner by principal name, such as
/// `administrator:3`, with no foreign key, and a session row names it the
/// same way. Nothing in the database removes either with the login, so one
/// left behind would outlive the login it belongs to.
/// `docs/decisions/0016-passkeys-beside-links.md`.
class LoginRemovalRulesTest {

    private static final String ACCOUNTS = "se.teaterihuskvarna.member.AccountRepository";
    private static final String ADMINISTRATOR = "se.teaterihuskvarna.administrator.Administrator";
    private static final String PASSKEYS = "se.teaterihuskvarna.login.PasskeyService";
    private static final String SESSIONS = "se.teaterihuskvarna.login.Sessions";

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("se.teaterihuskvarna");

    @Test
    void whatRemovesALoginRemovesItsPasskeysAndEndsItsSessions() {
        long removers = PRODUCTION.stream()
                .flatMap(type -> type.getCodeUnits().stream())
                .filter(LoginRemovalRulesTest::removesALogin)
                .count();
        assertThat(removers).as("methods that remove a login, so the rule cannot pass by finding none").isEqualTo(2);

        codeUnits().that(DescribedPredicate.describe("remove a login", LoginRemovalRulesTest::removesALogin))
                .should(call(PASSKEYS, "removeAll"))
                .andShould(call(SESSIONS, "end"))
                .check(PRODUCTION);
    }

    /// Any `delete` of the account repository, and the method that marks an
    /// administrator as removed. The administrator's own class is left out:
    /// it holds the method, and does not decide to call it.
    private static boolean removesALogin(JavaCodeUnit code) {
        return !code.getOwner().getName().equals(ADMINISTRATOR) && code.getMethodCallsFromSelf().stream()
                .anyMatch(call -> calls(call, ACCOUNTS) && call.getName().startsWith("delete")
                        || calls(call, ADMINISTRATOR) && call.getName().equals("remove"));
    }

    private static boolean calls(JavaMethodCall call, String owner) {
        return call.getTargetOwner().getName().equals(owner);
    }

    private static ArchCondition<JavaCodeUnit> call(String owner, String method) {
        String simple = owner.substring(owner.lastIndexOf('.') + 1);
        return new ArchCondition<>("call %s.%s".formatted(simple, method)) {
            @Override
            public void check(JavaCodeUnit code, ConditionEvents events) {
                boolean calls = code.getMethodCallsFromSelf().stream()
                        .anyMatch(call -> calls(call, owner) && call.getName().equals(method));
                events.add(new SimpleConditionEvent(code, calls, "%s %s %s.%s".formatted(code.getFullName(),
                        calls ? "calls" : "does not call", simple, method)));
            }
        };
    }
}
