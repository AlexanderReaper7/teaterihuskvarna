package se.teaterihuskvarna.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/// Every exclusion in `spotbugs-exclude.xml` has a comment right above it.
///
/// The comment says what the detector protects against and why that cannot
/// reach the excluded code, so the next reader can check the claim. This can
/// only see that a comment is there. What it says is for the review.
class SpotBugsExclusionsTest {

    private static final Pattern MATCH = Pattern.compile("<Match>");

    @Test
    void everyExclusionHasACommentAboveIt() throws IOException {
        String filter = Files.readString(Path.of("spotbugs-exclude.xml"));
        Matcher match = MATCH.matcher(filter);
        List<Integer> bare = new ArrayList<>();
        int exclusions = 0;
        while (match.find()) {
            exclusions++;
            String above = filter.substring(0, match.start());
            if (!above.stripTrailing().endsWith("-->")) {
                bare.add((int) above.lines().count());
            }
        }

        assertThat(bare).as("lines of spotbugs-exclude.xml with a <Match> and no comment right above it").isEmpty();
        assertThat(exclusions).as("exclusions read, so the test cannot pass by finding none").isPositive();
    }
}
