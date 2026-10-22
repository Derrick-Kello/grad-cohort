package edu.university.grps.thesis.formatcheck;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class FormatCheckerTest {

    private static final String W = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"";
    private static final String BODY = "This chapter sets out the research problem, the questions the study answers and the methods "
            + "used to answer them across two seasons of field work.";

    private final FormatChecker checker = new FormatChecker();

    static Map<String, Map<String, Object>> defaultRules() {
        Map<String, Map<String, Object>> rules = new HashMap<>();
        rules.put("FMT-PAGE", Map.of("size", "A4", "widthMm", 210, "heightMm", 297, "toleranceMm", 3));
        rules.put("FMT-MARGIN", Map.of("leftCm", 3.8, "topCm", 2.5, "rightCm", 2.5, "bottomCm", 2.5, "toleranceCm", 0.15));
        rules.put("FMT-FONT", Map.of("family", "Times New Roman", "sizePt", 12, "maxDeviationPct", 5));
        rules.put("FMT-SPACING", Map.of("lineSpacing", 1.5, "tolerance", 0.05, "maxDeviationPct", 10));
        rules.put("FMT-HEAD", Map.of("minRequired", 1));
        rules.put("FMT-PAGENUM", Map.of("preliminary", "lowerRoman", "main", "decimal"));
        rules.put("FMT-SECTIONS", Map.of("sections", List.of("Title page", "Declaration", "Abstract", "Acknowledgements",
                "Table of contents", "Chapters", "References", "Appendices")));
        rules.put("FMT-ABSTRACT", Map.of("maxWords", 300));
        rules.put("FMT-TOC", Map.of("required", true));
        rules.put("FMT-REFS", Map.of("required", true));
        return rules;
    }

    @Test
    void well_formatted_docx_passes_every_rule() throws IOException {
        FormatReport report = checker.check(docx(true), "DOCX", defaultRules());
        assertThat(report.results()).allSatisfy(r -> assertThat(r.passed()).as(r.ruleCode() + ": " + r.detail()).isTrue());
        assertThat(report.passed()).isTrue();
    }

    @Test
    void FR_THS_04_failing_rules_report_detail_and_location() throws IOException {
        FormatReport report = checker.check(docx(false), "DOCX", defaultRules());
        assertThat(report.passed()).isFalse();
        RuleResult margin = find(report, "FMT-MARGIN");
        assertThat(margin.passed()).isFalse();
        assertThat(margin.detail()).isEqualTo("Left margin 2.5 cm, required 3.8 cm.");
        assertThat(margin.location()).isEqualTo("section 1");
        assertThat(find(report, "FMT-FONT").passed()).isFalse();
        assertThat(find(report, "FMT-SPACING").passed()).isFalse();
        assertThat(find(report, "FMT-ABSTRACT").detail()).startsWith("Abstract is 340 words");
        assertThat(find(report, "FMT-TOC").passed()).isFalse();
        assertThat(find(report, "FMT-PAGENUM").passed()).isFalse();
    }

    @Test
    void switched_off_rules_are_skipped() throws IOException {
        Map<String, Map<String, Object>> rules = defaultRules();
        rules.remove("FMT-FONT");
        FormatReport report = checker.check(docx(true), "DOCX", rules);
        assertThat(find(report, "FMT-FONT").skipped()).isTrue();
    }

    @Test
    void BR_10_unreadable_files_fail_the_format_gate() {
        FormatReport report = checker.check("not a document".getBytes(StandardCharsets.UTF_8), "DOCX", defaultRules());
        assertThat(report.passed()).isFalse();
    }

    @Test
    void ranges_are_compact() {
        assertThat(FormatChecker.ranges("pages", List.of(12, 13, 14, 40))).isEqualTo("pages 12-14, 40");
    }

    private static RuleResult find(FormatReport report, String code) {
        return report.results().stream().filter(r -> r.ruleCode().equals(code)).findFirst().orElseThrow();
    }

    // A small Word document; "good" meets the default rule set, otherwise margins, font, spacing and more are wrong.
    static byte[] docx(boolean good) throws IOException {
        int left = good ? 2155 : 1418;
        StringBuilder body = new StringBuilder();
        body.append(p("Sensor networks for rain-fed agriculture", null)).append(p("A thesis submitted to the Graduate School", null));
        body.append(p("Declaration", "Heading1")).append(p(BODY, null));
        body.append(p("Abstract", "Heading1")).append(p("word ".repeat(good ? 180 : 340).trim(), null));
        body.append(p("Acknowledgements", "Heading1")).append(p(BODY, null));
        if (good) {
            body.append(p("Contents", "Heading1"));
            body.append("<w:p><w:pPr><w:pStyle w:val=\"TOC1\"/></w:pPr><w:r><w:fldChar w:fldCharType=\"begin\"/></w:r>"
                    + "<w:r><w:instrText xml:space=\"preserve\"> TOC \\o \"1-3\" </w:instrText></w:r>"
                    + "<w:r><w:fldChar w:fldCharType=\"separate\"/></w:r><w:r><w:t>Chapter 1 Introduction 1</w:t></w:r>"
                    + "<w:r><w:fldChar w:fldCharType=\"end\"/></w:r></w:p>");
            body.append("<w:p><w:pPr>").append(sect(left, "lowerRoman")).append("</w:pPr></w:p>");
        }
        body.append(p("Chapter 1 Introduction", "Heading1")).append(p(BODY, null)).append(p(BODY, null));
        body.append(p("References", "Heading1")).append(p("Owusu, Y. (2024). Irrigation in northern Ghana. J. Agric. 12(3), 44-60.", null));
        body.append(p("Appendices", "Heading1")).append(p(BODY, null));
        body.append(sect(left, null));
        String document = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document " + W + "><w:body>" + body + "</w:body></w:document>";
        String styles = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:styles " + W + "><w:docDefaults><w:rPrDefault><w:rPr>"
                + "<w:rFonts w:ascii=\"" + (good ? "Times New Roman" : "Arial") + "\"/><w:sz w:val=\"" + (good ? 24 : 22) + "\"/></w:rPr>"
                + "</w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:line=\"" + (good ? 360 : 240) + "\" w:lineRule=\"auto\"/></w:pPr>"
                + "</w:pPrDefault></w:docDefaults>"
                + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>"
                + "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/><w:basedOn w:val=\"Normal\"/></w:style>"
                + "<w:style w:type=\"paragraph\" w:styleId=\"TOC1\"><w:name w:val=\"toc 1\"/><w:basedOn w:val=\"Normal\"/></w:style>"
                + "</w:styles>";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "[Content_Types].xml", "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>");
            put(zip, "word/document.xml", document);
            put(zip, "word/styles.xml", styles);
        }
        return out.toByteArray();
    }

    private static String p(String text, String style) {
        String ppr = style == null ? "" : "<w:pPr><w:pStyle w:val=\"" + style + "\"/></w:pPr>";
        return "<w:p>" + ppr + "<w:r><w:t xml:space=\"preserve\">" + text + "</w:t></w:r></w:p>";
    }

    private static String sect(int left, String fmt) {
        return "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1418\" w:right=\"1418\" w:bottom=\"1418\" w:left=\""
                + left + "\" w:gutter=\"0\"/>" + (fmt == null ? "" : "<w:pgNumType w:fmt=\"" + fmt + "\"/>") + "</w:sectPr>";
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
