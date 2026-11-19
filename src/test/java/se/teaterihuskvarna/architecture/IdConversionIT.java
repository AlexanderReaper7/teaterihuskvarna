package se.teaterihuskvarna.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;
import se.teaterihuskvarna.IntegrationTestSupport;
import se.teaterihuskvarna.member.MemberId;

/// Every id type reads a request's text the way a `Long` does: an empty
/// value is no id, a number is the id, and anything else is refused.
///
/// `HouseholdOwnershipIT` posts the empty choice through both adapters, for
/// a member's id. This test is for the id type nobody has posted yet: Spring
/// would read it with its `valueOf`, which rejects an empty value, and the
/// form that offers "nobody" would answer with an error.
class IdConversionIT extends IntegrationTestSupport {

    @Autowired
    @Qualifier("mvcConversionService")
    private ConversionService requests;

    @Test
    void everyIdTypeReadsAnEmptyValueAsNoId() {
        List<Class<?>> types = IdTypes.in(new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("se.teaterihuskvarna"));
        assertThat(types).contains(MemberId.class).hasSizeGreaterThanOrEqualTo(3);

        for (Class<?> type : types) {
            assertThat(requests.convert("", type)).as("an empty %s", type.getSimpleName()).isNull();
            assertThat(requests.convert("  ", type)).as("a blank %s", type.getSimpleName()).isNull();
            assertThat(requests.convert("42", type)).as("a %s", type.getSimpleName())
                    .isInstanceOf(type).hasToString("42");
            assertThatThrownBy(() -> requests.convert("abc", type)).as("a %s that is no number", type.getSimpleName())
                    .isInstanceOf(ConversionFailedException.class);
        }
    }
}
