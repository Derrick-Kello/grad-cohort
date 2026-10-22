package edu.university.grps.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvTest {

    @Test
    void parses_quoted_fields_and_blank_lines() {
        var rows = Csv.parse("type,email\r\nSTAFF,\"a,b@x.org\"\n\nSTUDENT,\"say \"\"hi\"\"\"\n");
        assertThat(rows).containsExactly(List.of("type", "email"), List.of("STAFF", "a,b@x.org"), List.of("STUDENT", "say \"hi\""));
    }

    @Test
    void export_defuses_spreadsheet_formulas() {
        assertThat(Csv.escape("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(Csv.escape("-12.5")).isEqualTo("-12.5");
    }
}
