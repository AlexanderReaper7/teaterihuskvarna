package se.teaterihuskvarna.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvTest {

    @Test
    void startsWithTheByteOrderMarkAndEndsEveryRowWithCrlf() {
        String csv = Csv.write(List.of("Namn", "Ort"), List.of(List.of("Åsa Öberg", "Huskvarna")));

        assertThat(csv).isEqualTo("﻿Namn;Ort\r\nÅsa Öberg;Huskvarna\r\n");
    }

    @Test
    void writesANullCellEmpty() {
        String csv = Csv.write(List.of("a", "b"), List.of(Arrays.asList(null, "x")));

        assertThat(csv).endsWith("\r\n;x\r\n");
    }

    @Test
    void quotesACellHoldingTheSeparatorAQuoteOrALineBreak() {
        String csv = Csv.write(List.of("a"), List.of(
                List.of("Storgatan 1; lgh 2"),
                List.of("Sara \"Sassa\" Lind"),
                List.of("rad ett\nrad två")));

        assertThat(csv).isEqualTo("﻿a\r\n"
                + "\"Storgatan 1; lgh 2\"\r\n"
                + "\"Sara \"\"Sassa\"\" Lind\"\r\n"
                + "\"rad ett\nrad två\"\r\n");
    }

    @Test
    void prefixesEveryCellAFormulaCouldStartWith() {
        String csv = Csv.write(List.of("a"), List.of(
                List.of("=HYPERLINK(\"http://evil.test\")"),
                List.of("+46 70"),
                List.of("-1"),
                List.of("@SUM(A1)"),
                List.of("\tx"),
                List.of("\rx")));

        assertThat(csv).isEqualTo("﻿a\r\n"
                + "\"'=HYPERLINK(\"\"http://evil.test\"\")\"\r\n"
                + "'+46 70\r\n"
                + "'-1\r\n"
                + "'@SUM(A1)\r\n"
                + "'\tx\r\n"
                + "\"'\rx\"\r\n");
    }

    @Test
    void leavesAFormulaCharacterInsideACellAlone() {
        String csv = Csv.write(List.of("a"), List.of(List.of("a=b-c")));

        assertThat(csv).endsWith("\r\na=b-c\r\n");
    }
}
