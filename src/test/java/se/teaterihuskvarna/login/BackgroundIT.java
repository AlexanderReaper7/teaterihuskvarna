package se.teaterihuskvarna.login;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import se.teaterihuskvarna.IntegrationTestSupport;

/// Proves that work [Background] runs can fail without its exception's text
/// reaching the log. The work depends on an address, so the text of whatever
/// it throws may hold one.
@ExtendWith(OutputCaptureExtension.class)
class BackgroundIT extends IntegrationTestSupport {

    private static final String KARIN = "karin@example.test";

    @Autowired
    private Background background;

    @Test
    void aFailureIsLoggedWithItsClassesAndWithoutItsMessages(CapturedOutput output) {
        background.run(() -> {
            throw new IllegalStateException("no mail for " + KARIN,
                    new IllegalArgumentException("caused by " + KARIN));
        });
        // Waits until the executor has run the work, and so logged it.
        assertNoMail();

        assertThat(output)
                .contains("Background work failed")
                .contains(IllegalStateException.class.getName())
                .contains(IllegalArgumentException.class.getName())
                .contains("at " + BackgroundIT.class.getName())
                .doesNotContain(KARIN);
    }
}
