package edu.university.grps.thesis.formatcheck;

import static edu.university.grps.thesis.formatcheck.FormatChecker.fmt;
import static edu.university.grps.thesis.formatcheck.FormatChecker.ranges;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/** PDF checks: page size, approximate margins from text positions, page count and section headings. */
class PdfFormatChecker {

    private static final double PT_PER_MM = 72 / 25.4;
    private static final double PT_PER_CM = 72 / 2.54;

    /** Text extents per page, collected while stripping. */
    static final class Extents {
        float minX = Float.MAX_VALUE, maxX = -1, minY = Float.MAX_VALUE, maxY = -1;
        boolean hasText;
    }

    FormatReport check(byte[] content, FormatChecker.RuleSet rules) {
        try (PDDocument doc = Loader.loadPDF(content)) {
            int pages = doc.getNumberOfPages();
            List<RuleResult> out = new ArrayList<>();
            if (rules.active("FMT-PAGE")) out.add(checkPage(doc, rules));
            Map<Integer, Extents> extents = new HashMap<>();
            List<String> lines = new ArrayList<>();
            collect(doc, extents, lines);
            if (rules.active("FMT-MARGIN")) out.add(checkMargins(doc, extents, rules));
            if (rules.active("FMT-SECTIONS")) out.add(checkSections(lines, rules));
            return new FormatReport(out, pages);
        } catch (IOException e) {
            return new FormatReport(List.of(RuleResult.fail("FMT-PAGE", "The PDF could not be read: " + e.getMessage(), null)), null);
        }
    }

    private RuleResult checkPage(PDDocument doc, FormatChecker.RuleSet rules) {
        double wantW = rules.num("FMT-PAGE", "widthMm", 210);
        double wantH = rules.num("FMT-PAGE", "heightMm", 297);
        double tol = rules.num("FMT-PAGE", "toleranceMm", 3);
        String size = rules.str("FMT-PAGE", "size", "A4");
        List<Integer> bad = new ArrayList<>();
        String seen = null;
        int i = 0;
        for (PDPage page : doc.getPages()) {
            i++;
            PDRectangle box = page.getMediaBox();
            double w = box.getWidth() / PT_PER_MM;
            double h = box.getHeight() / PT_PER_MM;
            if (Math.abs(Math.min(w, h) - Math.min(wantW, wantH)) > tol || Math.abs(Math.max(w, h) - Math.max(wantW, wantH)) > tol) {
                bad.add(i);
                seen = fmt(Math.round(w)) + " x " + fmt(Math.round(h)) + " mm";
            }
        }
        if (bad.isEmpty()) return RuleResult.pass("FMT-PAGE", "Paper size is " + size + " on all " + i + " pages.");
        return RuleResult.fail("FMT-PAGE", "Paper size " + seen + ", required " + size + ".", ranges("pages", bad));
    }

    private RuleResult checkMargins(PDDocument doc, Map<Integer, Extents> extents, FormatChecker.RuleSet rules) {
        String code = "FMT-MARGIN";
        double tol = rules.num(code, "toleranceCm", 0.15) + 0.1;
        Map<String, Double> want = new LinkedHashMap<>();
        want.put("left", rules.num(code, "leftCm", 3.8));
        want.put("top", rules.num(code, "topCm", 2.5));
        want.put("right", rules.num(code, "rightCm", 2.5));
        want.put("bottom", rules.num(code, "bottomCm", 2.5));
        Map<String, List<Integer>> bad = new LinkedHashMap<>();
        Map<String, Double> worst = new HashMap<>();
        for (var entry : extents.entrySet()) {
            Extents e = entry.getValue();
            if (!e.hasText) continue;
            PDRectangle box = doc.getPage(entry.getKey() - 1).getMediaBox();
            Map<String, Double> actual = Map.of(
                    "left", e.minX / PT_PER_CM,
                    "right", (box.getWidth() - e.maxX) / PT_PER_CM,
                    "top", e.minY / PT_PER_CM,
                    "bottom", (box.getHeight() - e.maxY) / PT_PER_CM);
            for (var w : want.entrySet()) {
                double a = actual.get(w.getKey());
                // Page numbers often sit in the bottom margin; allow footers down to 1.2 cm.
                double required = w.getKey().equals("bottom") ? Math.min(w.getValue(), 1.2) : w.getValue();
                if (a + tol < required) {
                    bad.computeIfAbsent(w.getKey(), k -> new ArrayList<>()).add(entry.getKey());
                    worst.merge(w.getKey(), a, Math::min);
                }
            }
        }
        if (bad.isEmpty()) return RuleResult.pass(code, "Text stays within the required margins (approximate check on PDF).");
        List<String> parts = new ArrayList<>();
        List<Integer> pages = new ArrayList<>();
        for (var b : bad.entrySet()) {
            parts.add(Character.toUpperCase(b.getKey().charAt(0)) + b.getKey().substring(1) + " margin about "
                    + fmt(Math.round(worst.get(b.getKey()) * 10) / 10.0) + " cm, required " + fmt(want.get(b.getKey())) + " cm");
            b.getValue().forEach(p -> { if (!pages.contains(p)) pages.add(p); });
        }
        pages.sort(Integer::compare);
        return RuleResult.fail(code, String.join("; ", parts) + " (approximate, measured from text).", ranges("pages", pages));
    }

    private RuleResult checkSections(List<String> lines, FormatChecker.RuleSet rules) {
        List<String> required = rules.list("FMT-SECTIONS", "sections", List.of("Title page", "Declaration", "Abstract",
                "Acknowledgements", "Table of contents", "Chapters", "References", "Appendices"));
        Map<String, Integer> found = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String norm = DocxFormatChecker.normalise(lines.get(i));
            if (norm.isEmpty() || norm.split("\\s+").length > 8) continue;
            for (var e : DocxFormatChecker.SECTION_PATTERNS.entrySet()) {
                Pattern p = e.getValue();
                if (!found.containsKey(e.getKey()) && p.matcher(norm).matches()) {
                    found.put(e.getKey(), i);
                    break;
                }
            }
        }
        List<String> missing = new ArrayList<>();
        List<String> order = new ArrayList<>();
        int last = -1;
        String lastName = null;
        for (String name : required) {
            Integer idx = name.equalsIgnoreCase("Title page") ? (lines.isEmpty() ? null : 0) : found.get(name);
            if (idx == null) {
                missing.add(name);
                continue;
            }
            if (idx < last) order.add(name + " appears before " + lastName);
            else {
                last = idx;
                lastName = name;
            }
        }
        if (missing.isEmpty() && order.isEmpty()) return RuleResult.pass("FMT-SECTIONS", "All required section headings were found in order.");
        List<String> problems = new ArrayList<>();
        if (!missing.isEmpty()) problems.add("Missing: " + String.join(", ", missing));
        if (!order.isEmpty()) problems.add("Out of order: " + String.join("; ", order));
        return RuleResult.fail("FMT-SECTIONS", String.join(". ", problems) + ".", null);
    }

    private static void collect(PDDocument doc, Map<Integer, Extents> extents, List<String> lines) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> positions) throws IOException {
                Extents e = extents.computeIfAbsent(getCurrentPageNo(), k -> new Extents());
                for (TextPosition t : positions) {
                    if (t.getUnicode() == null || t.getUnicode().isBlank()) continue;
                    e.hasText = true;
                    float top = t.getYDirAdj() - t.getHeightDir();
                    e.minX = Math.min(e.minX, t.getXDirAdj());
                    e.maxX = Math.max(e.maxX, t.getXDirAdj() + t.getWidthDirAdj());
                    e.minY = Math.min(e.minY, top);
                    e.maxY = Math.max(e.maxY, t.getYDirAdj());
                }
                super.writeString(text, positions);
            }
        };
        stripper.setSortByPosition(true);
        String text = stripper.getText(doc);
        for (String line : text.split("\\r?\\n")) lines.add(line.trim());
    }
}
