package se.teaterihuskvarna.export;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// Writes CSV for Excel as it opens files in Sweden: a UTF-8 byte order mark so
/// that å, ä and ö survive, `;` between cells because `,` is the decimal
/// separator there, and CRLF after every row.
///
/// A cell is quoted when it holds `;`, `"`, CR or LF, with `"` doubled inside.
/// A cell starting with `=`, `+`, `-`, `@`, tab or CR gets a `'` in front, so a
/// spreadsheet shows it as text instead of running it as a formula (OWASP, "CSV
/// Injection"). A name like `=HYPERLINK(...)` typed into a form would otherwise
/// run on the administrator's machine.
public final class Csv {

    private static final char BOM = '\uFEFF';

    private Csv() {
    }

    /// @param header the column names
    /// @param rows   the rows, each as long as the header; a null cell is written empty
    /// @return the file's text, starting with the byte order mark
    public static String write(List<String> header, List<List<String>> rows) {
        StringBuilder out = new StringBuilder().append(BOM);
        row(out, header);
        for (List<String> row : rows) {
            row(out, row);
        }
        return out.toString();
    }

    private static void row(StringBuilder out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(';');
            }
            out.append(cell(cells.get(i)));
        }
        out.append("\r\n");
    }

    private static String cell(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String text = value;
        char first = text.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            text = "'" + text;
        }
        if (text.indexOf(';') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\r') >= 0
                || text.indexOf('\n') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
