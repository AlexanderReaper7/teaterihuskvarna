package se.teaterihuskvarna.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/// A stored member gains a household through `FeeLedger.join` and no other way.
///
/// A household fee paid by a member in no household names no household, and
/// `join` gives it the one the member gains. Code that put the member there
/// itself would leave the fee behind, and the household would show as unpaid
/// although one of its members had paid for it: finding C1 of the review of
/// 2026-11-10, which a comment was all that guarded against.
class HouseholdRulesTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("se.teaterihuskvarna");

    @Test
    void onlyTheFeeLedgerPutsAStoredMemberInAHousehold() {
        noClasses().that().doNotHaveFullyQualifiedName("se.teaterihuskvarna.member.FeeLedger")
                .should().callMethod("se.teaterihuskvarna.member.Member", "enter",
                        "se.teaterihuskvarna.member.Household")
                .because("FeeLedger.join brings the member's household fee along")
                .check(PRODUCTION);
    }
}
