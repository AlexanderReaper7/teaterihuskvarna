package se.teaterihuskvarna.export;

import java.util.List;

/// CSV text that Excel opens as Swedish users expect: UTF-8 with a byte order
/// mark, so å, ä and ö survive, `;` between cells, because Excel in a Swedish
/// locale splits on `;` and not on `,`, and CRLF after every row.
///
/// A cell is quoted when it holds `;`, `"`, CR or LF, with `"` doubled inside.
/// A cell that starts with `=`, `+`, `-`, `@`, a tab or CR gets a `'` in front,
/// so a spreadsheet shows it as text rather than running it as a formula. A
/// member's name or phone number is typed by someone else, and `=HYPERLINK(...)`
/// in one would otherwise become a live link in the administrator's sheet
/// (OWASP, "CSV Injection").
public final class Csv {

    /// U+FEFF, which UTF-8 encodes as EF BB BF.
    private static final char BOM = '﻿';
    private static final String SEPARATOR = ";";
    private static final String LINE_END = "\r\n";

    private Csv() {
    }

    /// @param header the column names, written as the first row
    /// @param rows   one list of cells per row
    /// @return the whole file as text, starting with the byte order mark
    public static String write(List<String> header, List<List<String>> rows) {
        StringBuilder text = new StringBuilder().append(BOM);
        row(text, header);
        for (List<String> row : rows) {
            row(text, row);
        }
        return text.toString();
    }

    private static void row(StringBuilder text, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                text.append(SEPARATOR);
            }
            text.append(cell(cells.get(i)));
        }
        text.append(LINE_END);
    }

    private static String cell(String value) {
        String guarded = value;
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            guarded = "'" + value;
        }
        boolean quote = guarded.contains(SEPARATOR) || guarded.contains("\"") || guarded.contains("\r")
                || guarded.contains("\n");
        return quote ? "\"" + guarded.replace("\"", "\"\"") + "\"" : guarded;
    }
}
