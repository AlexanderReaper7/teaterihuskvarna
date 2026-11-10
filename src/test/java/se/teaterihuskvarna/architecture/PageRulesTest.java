package se.teaterihuskvarna.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import gg.jte.TemplateEngine;
import gg.jte.generated.precompiled.DynamicTemplates;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/// A page is shown by calling its method on `Templates`, which the build
/// generates from the template. The compiler then checks the three things a
/// view's name and a model left to chance: that the page exists, that every
/// value it declares is passed, and that each has the type it declares.
///
/// The old way still compiles in Spring: a handler that returns a `String`
/// and fills a `Model`. These rules refuse it, so the checked way is the only
/// one. Nothing would draw such a page either, since no view here has a name.
class PageRulesTest {

    private static final String WEB = "se.teaterihuskvarna.web";

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("se.teaterihuskvarna");

    /// A model is a map from a name to anything, and what a redirect carries
    /// is another. A name spelled one way by the handler and another way by
    /// the template was a page with a hole in it. `Feedback` and `Redirect`
    /// own the two names that are left.
    @Test
    void nothingInThePagesFillsAModel() {
        noClasses().that().resideInAPackage(WEB)
                .should().dependOnClassesThat().areAssignableTo(Model.class)
                .orShould().dependOnClassesThat().areAssignableTo(ModelMap.class)
                .orShould().dependOnClassesThat().areAssignableTo(RedirectAttributes.class)
                .because("a page gets what it shows as the arguments of its method on Templates,"
                        + " and a redirect carries its notice through Redirect")
                .check(PRODUCTION);
        noClasses().that().resideInAPackage(WEB).and().doNotHaveSimpleName("ErrorPage")
                .should().dependOnClassesThat().areAssignableTo(ModelAndView.class)
                .because("only Boot's ErrorViewResolver asks for a ModelAndView, and ErrorPage answers it")
                .check(PRODUCTION);
    }

    /// A handler answers with a `View`, which is an `HtmlPage` or a
    /// `Redirect`, with a `ResponseEntity` for a file, or with nothing after
    /// writing the response itself. A `String` would be a view's name unless
    /// the method is a `@ResponseBody`, as the sitemap is.
    @Test
    void aHandlerAnswersWithAPageARedirectOrAFile() {
        Set<String> wrong = new TreeSet<>();
        int handlers = 0;
        for (JavaClass type : PRODUCTION) {
            if (!type.getPackageName().equals(WEB)) {
                continue;
            }
            for (JavaMethod method : type.getMethods()) {
                if (!method.isMetaAnnotatedWith(RequestMapping.class)) {
                    continue;
                }
                handlers++;
                JavaClass answer = method.getRawReturnType();
                boolean checked = answer.isAssignableTo(View.class) || answer.isAssignableTo(ResponseEntity.class)
                        || answer.isEquivalentTo(void.class) || method.isAnnotatedWith(ResponseBody.class);
                if (!checked) {
                    wrong.add("%s.%s returns %s".formatted(type.getSimpleName(), method.getName(),
                            answer.getSimpleName()));
                }
            }
        }

        assertThat(wrong).as("handlers that answer with something the compiler cannot check").isEmpty();
        assertThat(handlers).as("handlers read, so the test cannot pass by finding none").isGreaterThan(80);
    }

    /// `@ModelAttribute` on a method puts what it returns in the model of
    /// every handler in the class, under a name. On a parameter it only says
    /// that a form is read into the object, which is fine.
    @Test
    void noMethodPutsAValueInEveryModel() {
        Set<String> wrong = new TreeSet<>();
        for (JavaClass type : PRODUCTION) {
            for (JavaMethod method : type.getMethods()) {
                if (method.isAnnotatedWith(ModelAttribute.class)
                        && !method.getRawReturnType().isEquivalentTo(void.class)) {
                    wrong.add("%s.%s".formatted(type.getSimpleName(), method.getName()));
                }
            }
        }

        assertThat(wrong).isEmpty();
    }

    /// `TemplateEngine.render("mail/mailing.jte", Map.of(..))` and
    /// `DynamicTemplates` both find a template by its file name while the
    /// application runs, so a renamed template fails there and not in the
    /// build.
    @Test
    void nothingLooksATemplateUpByName() {
        noClasses().should().dependOnClassesThat().areAssignableTo(TemplateEngine.class)
                .orShould().dependOnClassesThat().areAssignableTo(DynamicTemplates.class)
                .because("StaticTemplates calls the compiled template, which exists or the build fails")
                .check(PRODUCTION);
    }
}
