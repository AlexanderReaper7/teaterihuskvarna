package se.teaterihuskvarna;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

/// The copy is read the way a logger prints it: as a stack trace.
class WithoutMessageTest {

    private static final String ADDRESS = "karin@example.test";

    @Test
    void theCopyNamesEveryClassAndHoldsNoMessage() {
        IllegalStateException failure = new IllegalStateException("could not mail " + ADDRESS,
                new IllegalArgumentException("refused " + ADDRESS, new ArithmeticException(ADDRESS)));
        failure.addSuppressed(new UnsupportedOperationException("also " + ADDRESS,
                new ArrayStoreException(ADDRESS)));

        String printed = printed(WithoutMessage.of(failure));

        assertThat(printed)
                .startsWith("se.teaterihuskvarna.WithoutMessage: java.lang.IllegalStateException\n")
                .contains("Caused by: se.teaterihuskvarna.WithoutMessage: java.lang.IllegalArgumentException\n")
                .contains("Caused by: se.teaterihuskvarna.WithoutMessage: java.lang.ArithmeticException\n")
                .contains("Suppressed: se.teaterihuskvarna.WithoutMessage: java.lang.UnsupportedOperationException\n")
                .contains("Caused by: se.teaterihuskvarna.WithoutMessage: java.lang.ArrayStoreException\n")
                .doesNotContain(ADDRESS)
                .doesNotContain("could not mail")
                .doesNotContain("refused")
                .doesNotContain("also");
    }

    @Test
    void theCopyKeepsTheFramesOfTheOriginal() {
        IllegalArgumentException cause = new IllegalArgumentException(ADDRESS);
        IllegalStateException failure = new IllegalStateException(ADDRESS, cause);

        WithoutMessage copy = WithoutMessage.of(failure);

        assertThat(copy.getStackTrace()).containsExactly(failure.getStackTrace());
        assertThat(copy.getStackTrace()[0].getMethodName()).isEqualTo("theCopyKeepsTheFramesOfTheOriginal");
        assertThat(requireNonNull(copy.getCause()).getStackTrace()).containsExactly(cause.getStackTrace());
    }

    /// `initCause` lets two exceptions name each other. Following that for ever
    /// would turn a logged failure into a stack overflow.
    @Test
    void aCauseThatLoopsIsCopiedOnce() {
        IllegalStateException first = new IllegalStateException(ADDRESS);
        IllegalArgumentException second = new IllegalArgumentException(ADDRESS, first);
        first.initCause(second);

        WithoutMessage copy = WithoutMessage.of(first);

        assertThat(requireNonNull(copy.getCause()).getCause()).isSameAs(copy);
        assertThat(printed(copy)).doesNotContain(ADDRESS);
    }

    private static String printed(Throwable failure) {
        StringWriter text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        return text.toString();
    }
}
