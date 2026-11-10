package se.teaterihuskvarna.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/// A card in a list of cards is drawn by `tag/card.jte` and by nothing else.
///
/// The tag puts everything but the picture in one box, and `site.css` lays a
/// card out as those two parts: the picture's row and the box's row. A card
/// written out in a page has no box, so its title would land in the picture's
/// row and the rest on top of each other below it. Nothing but a look at the
/// page would show that, so this reads the templates.
class CardsTest {

    private static final Path TEMPLATES = Path.of("src/main/jte");
    private static final Path TAG = TEMPLATES.resolve("tag/card.jte");

    /// A class attribute with the word `card` in it, which `cards` and
    /// `card-text` are not.
    private static final Pattern CARD = Pattern.compile("class=\"(?:[^\"]*\\s)?card(?:\\s[^\"]*)?\"");
    private static final String CALL = "@template.tag.card(";

    @Test
    void onlyTheCardTagDrawsACard() throws IOException {
        Set<String> wrong = new TreeSet<>();
        int calls = 0;
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String source = Files.readString(file);
                calls += source.split(Pattern.quote(CALL), -1).length - 1;
                if (!file.equals(TAG) && CARD.matcher(source).find()) {
                    wrong.add(file + " writes a card itself, without " + CALL + "..)");
                }
            }
        }

        assertThat(wrong).isEmpty();
        assertThat(CARD.matcher(Files.readString(TAG)).find()).as("the tag draws the card").isTrue();
        assertThat(calls).as("cards read, so the test cannot pass by finding none").isGreaterThan(4);
    }
}
