package se.teaterihuskvarna.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvTest {

    @Test
    void startsWithTheByteOrderMarkAndEndsEveryRowWithCrlf() {
        String text = Csv.write(List.of("Namn", "Ort"), List.of(List.of("Åsa Öberg", "Huskvarna")));

        assertThat(text).isEqualTo("﻿Namn;Ort\r\nÅsa Öberg;Huskvarna\r\n");
    }

    @Test
    void anEmptyTableIsTheHeaderAlone() {
        assertThat(Csv.write(List.of("A"), List.of())).isEqualTo("﻿A\r\n");
    }

    @Test
    void quotesACellWithASeparatorAQuoteOrALineBreak() {
        String text = Csv.write(List.of("x"), List.of(
                List.of("a;b"),
                List.of("say \"hi\""),
                List.of("two\nlines"),
                List.of("cr\rhere")));

        assertThat(text).isEqualTo("﻿x\r\n\"a;b\"\r\n\"say \"\"hi\"\"\"\r\n\"two\nlines\"\r\n\"cr\rhere\"\r\n");
    }

    @Test
    void prefixesEveryFormulaStartWithAnApostrophe() {
        String text = Csv.write(List.of("x"), List.of(
                List.of("=HYPERLINK(\"http://example.test\")"),
                List.of("+46"),
                List.of("-1"),
                List.of("@SUM(A1)"),
                List.of("\tTab"),
                List.of("\rCr")));

        assertThat(text).isEqualTo("﻿x\r\n"
                + "\"'=HYPERLINK(\"\"http://example.test\"\")\"\r\n"
                + "'+46\r\n"
                + "'-1\r\n"
                + "'@SUM(A1)\r\n"
                + "'\tTab\r\n"
                + "\"'\rCr\"\r\n");
    }

    @Test
    void leavesAFormulaCharacterInsideACellAlone() {
        assertThat(Csv.write(List.of("x"), List.of(List.of("a=b", ""))))
                .isEqualTo("﻿x\r\na=b;\r\n");
    }
}
