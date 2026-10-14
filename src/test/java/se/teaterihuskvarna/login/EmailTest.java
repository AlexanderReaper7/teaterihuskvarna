package se.teaterihuskvarna.login;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailTest {

    @Test
    void removesSpacesAtTheEnds() {
        assertThat(new Email(" anna@example.se ").value()).isEqualTo("anna@example.se");
    }
}