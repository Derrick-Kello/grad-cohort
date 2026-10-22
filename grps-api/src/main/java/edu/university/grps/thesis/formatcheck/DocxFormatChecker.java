package edu.university.grps.thesis.formatcheck;

import static edu.university.grps.thesis.formatcheck.FormatChecker.fmt;
import static edu.university.grps.thesis.formatcheck.FormatChecker.ranges;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Full format check for Word documents, reading the OOXML parts directly. */
class DocxFormatChecker {

    static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final double TWIPS_PER_CM = 566.929;
    private static final double TWIPS_PER_MM = 56.6929;
    private static final long MAX_PART_BYTES = 200L * 1024 * 1024;

    static final Map<String, Pattern> SECTION_PATTERNS = new LinkedHashMap<>();

    static {
        SECTION_PATTERNS.put("Declaration", Pattern.compile("^(declaration|statement of originality)\\b.*"));
        SECTION_PATTERNS.put("Abstract", Pattern.compile("^abstract$"));
        SECTION_PATTERNS.put("Acknowledgements", Pattern.compile("^acknowledge?ments?$"));
        SECTION_PATTERNS.put("Table of contents", Pattern.compile("^(table of )?contents$"));
        SECTION_PATTERNS.put("Chapters", Pattern.compile("^chapter\\s+(1|one|i)\\b.*"));
        SECTION_PATTERNS.put("References", Pattern.compile("^(references|bibliography|reference list|works cited)$"));
        SECTION_PATTERNS.put("Appendices", Pattern.compile("^appendi(x|ces)\\b.*"));
    }

    // Parsed model ----------------------------------------------------------------

    record Style(String id, String name, String type, String basedOn, String font, Integer halfPoints, Double line,
                 boolean isDefault) {}

    static final class Run {
        String text;
        String font;
        Integer halfPoints;
    }

    static final class Para {
        int index;
        String styleId;
        String styleName;
        String text;
        boolean inToc;
        int headingLevel;
        Double line;
        List<Run> runs = new ArrayList<>();
        Element sectPr;
    }

    record Section(int index, Element sectPr, int lastParagraph) {}

    private final Map<String, Style> styles = new HashMap<>();
    private String defaultParagraphStyle;
    private String defaultFont = "Times New Roman";
    private Integer defaultHalfPoints = 20;
    private Double defaultLine = 1.0;
    private String themeMinor;
    private String themeMajor;
    private boolean tocFieldPresent;

    FormatReport check(byte[] content, FormatChecker.RuleSet rules) {
        Map<String, byte[]> parts;
        Document doc;
        try {
            parts = unzip(content, Set.of("word/document.xml", "word/styles.xml", "word/theme/theme1.xml"));
            if (!parts.containsKey("word/document.xml")) throw new IOException("Not a Word document");
            loadTheme(parts.get("word/theme/theme1.xml"));
            loadStyles(parts.get("word/styles.xml"));
            doc = parse(parts.get("word/document.xml"));
        } catch (Exception e) {
            return new FormatReport(List.of(RuleResult.fail("FMT-PAGE", "The document could not be read: " + e.getMessage(), null)), null);
        }
        Element body = first(doc.getDocumentElement(), "body");
        if (body == null) return new FormatReport(List.of(RuleResult.fail("FMT-PAGE", "The document has no body.", null)), null);
        List<Para> paras = new ArrayList<>();
        collectParagraphs(body, paras, false, new ArrayDeque<>());
        List<Section> sections = sections(body, paras);

        List<RuleResult> out = new ArrayList<>();
        if (rules.active("FMT-PAGE")) out.add(checkPage(sections, rules));
        if (rules.active("FMT-MARGIN")) out.add(checkMargins(sections, rules));
        if (rules.active("FMT-FONT")) out.add(checkFont(paras, rules));
        if (rules.active("FMT-SPACING")) out.add(checkSpacing(paras, rules));
        if (rules.active("FMT-HEAD")) out.add(checkHeadings(paras, rules));
        Map<String, Integer> found = findSections(paras);
        if (rules.active("FMT-PAGENUM")) out.add(checkPageNumbers(sections, found, rules));
        if (rules.active("FMT-SECTIONS")) out.add(checkSections(paras, found, rules));
        if (rules.active("FMT-ABSTRACT")) out.add(checkAbstract(paras, found, rules));
        if (rules.active("FMT-TOC")) out.add(checkToc(found));
        if (rules.active("FMT-REFS")) out.add(checkReferences(paras, found));
        return new FormatReport(out, null);
    }

    // Rules ---------------------------------------------------------------------------

    private RuleResult checkPage(List<Section> sections, FormatChecker.RuleSet rules) {
        double wantW = rules.num("FMT-PAGE", "widthMm", 210);
        double wantH = rules.num("FMT-PAGE", "heightMm", 297);
        double tol = rules.num("FMT-PAGE", "toleranceMm", 3);
        String size = rules.str("FMT-PAGE", "size", "A4");
        List<Integer> bad = new ArrayList<>();
        String seen = null;
        for (Section s : sections) {
            Element pgSz = first(s.sectPr(), "pgSz");
            double w = pgSz == null ? 12240 / TWIPS_PER_MM : attrNum(pgSz, "w", 12240) / TWIPS_PER_MM;
            double h = pgSz == null ? 15840 / TWIPS_PER_MM : attrNum(pgSz, "h", 15840) / TWIPS_PER_MM;
            double shortSide = Math.min(w, h);
            double longSide = Math.max(w, h);
            if (Math.abs(shortSide - Math.min(wantW, wantH)) > tol || Math.abs(longSide - Math.max(wantW, wantH)) > tol) {
                bad.add(s.index() + 1);
                seen = fmt(Math.round(w)) + " x " + fmt(Math.round(h)) + " mm";
            }
        }
        if (bad.isEmpty()) return RuleResult.pass("FMT-PAGE", "Paper size is " + size + " in every section.");
        return RuleResult.fail("FMT-PAGE", "Paper size " + seen + ", required " + size + " (" + fmt(wantW) + " x " + fmt(wantH) + " mm).",
                ranges(bad.size() == 1 ? "section" : "sections", bad));
    }

    private RuleResult checkMargins(List<Section> sections, FormatChecker.RuleSet rules) {
        String code = "FMT-MARGIN";
        double tol = rules.num(code, "toleranceCm", 0.15);
        Map<String, Double> want = new LinkedHashMap<>();
        want.put("left", rules.num(code, "leftCm", 3.8));
        want.put("top", rules.num(code, "topCm", 2.5));
        want.put("right", rules.num(code, "rightCm", 2.5));
        want.put("bottom", rules.num(code, "bottomCm", 2.5));
        Map<String, List<Integer>> badSections = new LinkedHashMap<>();
        Map<String, Double> actual = new HashMap<>();
        for (Section s : sections) {
            Element mar = first(s.sectPr(), "pgMar");
            for (var e : want.entrySet()) {
                double twips = mar == null ? 1440 : Math.abs(attrNum(mar, e.getKey(), 1440));
                if (e.getKey().equals("left") && mar != null) twips += Math.abs(attrNum(mar, "gutter", 0));
                double cm = twips / TWIPS_PER_CM;
                if (cm + tol < e.getValue()) {
                    badSections.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(s.index() + 1);
                    actual.put(e.getKey(), cm);
                }
            }
        }
        if (badSections.isEmpty()) {
            return RuleResult.pass(code, "Margins meet the minimum: left " + fmt(want.get("left")) + " cm, top/right/bottom "
                    + fmt(want.get("top")) + " cm.");
        }
        List<String> parts = new ArrayList<>();
        List<Integer> all = new ArrayList<>();
        for (var e : badSections.entrySet()) {
            parts.add(capitalise(e.getKey()) + " margin " + fmt(round1(actual.get(e.getKey()))) + " cm, required "
                    + fmt(want.get(e.getKey())) + " cm");
            e.getValue().forEach(i -> { if (!all.contains(i)) all.add(i); });
        }
        all.sort(Integer::compare);
        return RuleResult.fail(code, String.join("; ", parts) + ".", ranges(all.size() == 1 ? "section" : "sections", all));
    }

    private RuleResult checkFont(List<Para> paras, FormatChecker.RuleSet rules) {
        String code = "FMT-FONT";
        String family = rules.str(code, "family", "Times New Roman");
        double size = rules.num(code, "sizePt", 12);
        double maxDev = rules.num(code, "maxDeviationPct", 5);
        long total = 0;
        long bad = 0;
        List<Integer> badParas = new ArrayList<>();
        Map<String, Long> offenders = new HashMap<>();
        for (Para p : bodyText(paras)) {
            boolean paraBad = false;
            for (Run r : p.runs) {
                int len = r.text.strip().length();
                if (len == 0) continue;
                total += len;
                boolean fontOk = r.font != null && r.font.equalsIgnoreCase(family);
                boolean sizeOk = r.halfPoints != null && Math.abs(r.halfPoints / 2.0 - size) < 0.01;
                if (!fontOk || !sizeOk) {
                    bad += len;
                    paraBad = true;
                    offenders.merge((r.font == null ? "unknown font" : r.font) + " " + (r.halfPoints == null ? "?" : fmt(r.halfPoints / 2.0)) + " pt", (long) len, Long::sum);
                }
            }
            if (paraBad) badParas.add(p.index + 1);
        }
        if (total == 0) return RuleResult.fail(code, "No body text was found.", null);
        double pct = bad * 100.0 / total;
        if (pct <= maxDev) {
            return RuleResult.pass(code, "Body text is " + family + " " + fmt(size) + " pt (" + fmt(round1(100 - pct)) + "% of characters).");
        }
        String worst = offenders.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("other");
        return RuleResult.fail(code, fmt(round1(pct)) + "% of body text is not " + family + " " + fmt(size) + " pt (mostly " + worst + ").",
                ranges("paragraphs", badParas));
    }

    private RuleResult checkSpacing(List<Para> paras, FormatChecker.RuleSet rules) {
        String code = "FMT-SPACING";
        double want = rules.num(code, "lineSpacing", 1.5);
        double tol = rules.num(code, "tolerance", 0.05);
        double maxDev = rules.num(code, "maxDeviationPct", 10);
        List<Para> body = bodyText(paras);
        if (body.isEmpty()) return RuleResult.fail(code, "No body text was found.", null);
        List<Integer> bad = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (Para p : body) {
            if (p.line == null || Double.isNaN(p.line) || Math.abs(p.line - want) > tol) {
                bad.add(p.index + 1);
                seen.merge(p.line == null || Double.isNaN(p.line) ? "fixed" : fmt(round2(p.line)), 1, Integer::sum);
            }
        }
        double pct = bad.size() * 100.0 / body.size();
        if (pct <= maxDev) return RuleResult.pass(code, "Body line spacing is " + fmt(want) + ".");
        String common = seen.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("?");
        return RuleResult.fail(code, "Line spacing " + common + ", required " + fmt(want) + " (" + bad.size() + " of " + body.size()
                + " body paragraphs).", ranges("paragraphs", bad));
    }

    private RuleResult checkHeadings(List<Para> paras, FormatChecker.RuleSet rules) {
        int[] counts = new int[4];
        for (Para p : paras) if (!p.inToc && p.headingLevel >= 1 && p.headingLevel <= 3) counts[p.headingLevel]++;
        int min = (int) rules.num("FMT-HEAD", "minRequired", 1);
        String detail = "Heading 1: " + counts[1] + ", Heading 2: " + counts[2] + ", Heading 3: " + counts[3] + ".";
        if (counts[1] >= min) return RuleResult.pass("FMT-HEAD", "Word heading styles are used. " + detail);
        return RuleResult.fail("FMT-HEAD", "Chapter titles do not use the Word Heading 1 style. " + detail
                + " Apply Heading 1 to chapter titles so the table of contents can be generated.", null);
    }

    private RuleResult checkPageNumbers(List<Section> sections, Map<String, Integer> found, FormatChecker.RuleSet rules) {
        String code = "FMT-PAGENUM";
        String prelim = rules.str(code, "preliminary", "lowerRoman");
        String main = rules.str(code, "main", "decimal");
        if (sections.size() < 2) {
            return RuleResult.fail(code, "Preliminary pages and chapters are in one section, so they cannot use different page "
                    + "numbering. Insert a section break before Chapter 1.", "section 1");
        }
        String firstFmt = pageFormat(sections.get(0));
        Integer chapterPara = found.get("Chapters");
        Section chapterSection = sections.get(sections.size() - 1);
        if (chapterPara != null) {
            for (Section s : sections) {
                if (chapterPara <= s.lastParagraph()) {
                    chapterSection = s;
                    break;
                }
            }
        }
        String mainFmt = pageFormat(chapterSection);
        List<String> problems = new ArrayList<>();
        if (!firstFmt.equals(prelim)) problems.add("preliminary pages use " + firstFmt + ", required " + prelim);
        if (!mainFmt.equals(main)) problems.add("chapters use " + mainFmt + ", required " + main);
        if (chapterSection.index() == 0) problems.add("Chapter 1 is in the same section as the preliminary pages");
        if (problems.isEmpty()) return RuleResult.pass(code, "Roman numerals for preliminary pages, Arabic from Chapter 1.");
        return RuleResult.fail(code, "Page numbering: " + String.join("; ", problems) + ".",
                "sections 1 and " + (chapterSection.index() + 1));
    }

    private RuleResult checkSections(List<Para> paras, Map<String, Integer> found, FormatChecker.RuleSet rules) {
        List<String> required = rules.list("FMT-SECTIONS", "sections", List.of("Title page", "Declaration", "Abstract",
                "Acknowledgements", "Table of contents", "Chapters", "References", "Appendices"));
        List<String> missing = new ArrayList<>();
        List<String> order = new ArrayList<>();
        int lastIndex = -1;
        String lastName = null;
        for (String name : required) {
            Integer idx = name.equalsIgnoreCase("Title page") ? titlePageIndex(paras, found) : found.get(canonical(name));
            if (idx == null) {
                missing.add(name);
                continue;
            }
            if (idx < lastIndex) order.add(name + " appears before " + lastName);
            else {
                lastIndex = idx;
                lastName = name;
            }
        }
        if (missing.isEmpty() && order.isEmpty()) return RuleResult.pass("FMT-SECTIONS", "All required sections are present and in order.");
        List<String> problems = new ArrayList<>();
        if (!missing.isEmpty()) problems.add("Missing: " + String.join(", ", missing));
        if (!order.isEmpty()) problems.add("Out of order: " + String.join("; ", order));
        return RuleResult.fail("FMT-SECTIONS", String.join(". ", problems) + ".", null);
    }

    private RuleResult checkAbstract(List<Para> paras, Map<String, Integer> found, FormatChecker.RuleSet rules) {
        int max = (int) rules.num("FMT-ABSTRACT", "maxWords", 300);
        Integer start = found.get("Abstract");
        if (start == null) return RuleResult.fail("FMT-ABSTRACT", "No Abstract heading was found.", null);
        int words = 0;
        for (int i = start + 1; i < paras.size(); i++) {
            Para p = paras.get(i);
            if (p.inToc) continue;
            if (isSectionBoundary(p)) break;
            if (!p.text.isBlank()) words += p.text.trim().split("\\s+").length;
        }
        if (words == 0) return RuleResult.fail("FMT-ABSTRACT", "The Abstract section is empty.", "paragraph " + (start + 1));
        if (words <= max) return RuleResult.pass("FMT-ABSTRACT", "Abstract is " + words + " words (limit " + max + ").");
        return RuleResult.fail("FMT-ABSTRACT", "Abstract is " + words + " words, limit " + max + ".", "from paragraph " + (start + 1));
    }

    private RuleResult checkToc(Map<String, Integer> found) {
        if (tocFieldPresent) return RuleResult.pass("FMT-TOC", "A generated table of contents is present.");
        if (found.containsKey("Table of contents")) {
            return RuleResult.fail("FMT-TOC", "A Contents heading exists but the table is typed by hand. Insert it with "
                    + "References > Table of Contents so it is generated from the headings.", "paragraph " + (found.get("Table of contents") + 1));
        }
        return RuleResult.fail("FMT-TOC", "No table of contents was found.", null);
    }

    private RuleResult checkReferences(List<Para> paras, Map<String, Integer> found) {
        Integer start = found.get("References");
        if (start == null) return RuleResult.fail("FMT-REFS", "No References or Bibliography section was found.", null);
        int entries = 0;
        for (int i = start + 1; i < paras.size(); i++) {
            Para p = paras.get(i);
            if (p.inToc) continue;
            if (isSectionBoundary(p)) break;
            if (!p.text.isBlank()) entries++;
        }
        if (entries == 0) return RuleResult.fail("FMT-REFS", "The References section is empty.", "paragraph " + (start + 1));
        return RuleResult.pass("FMT-REFS", "References section has " + entries + " entries.");
    }

    // Section detection -----------------------------------------------------------------

    private Map<String, Integer> findSections(List<Para> paras) {
        Map<String, Integer> found = new HashMap<>();
        for (Para p : paras) {
            if (p.inToc) continue;
            String norm = normalise(p.text);
            if (norm.isEmpty() || (p.headingLevel == 0 && norm.split("\\s+").length > 8)) continue;
            for (var e : SECTION_PATTERNS.entrySet()) {
                if (found.containsKey(e.getKey())) continue;
                if (e.getValue().matcher(norm).matches()) {
                    found.put(e.getKey(), p.index);
                    break;
                }
            }
            if (!found.containsKey("Chapters") && p.headingLevel == 1 && norm.matches("^1(\\.|\\s).*")) found.put("Chapters", p.index);
        }
        // A generated table of contents (and its heading) sits inside the TOC field or content control.
        if (!found.containsKey("Table of contents")) {
            paras.stream().filter(p -> p.inToc).findFirst().ifPresent(p -> found.put("Table of contents", p.index));
        }
        return found;
    }

    private Integer titlePageIndex(List<Para> paras, Map<String, Integer> found) {
        int firstSection = found.values().stream().min(Integer::compare).orElse(Integer.MAX_VALUE);
        for (Para p : paras) {
            if (p.index >= firstSection) break;
            if (!p.inToc && !p.text.isBlank()) return p.index;
        }
        return null;
    }

    private boolean isSectionBoundary(Para p) {
        if (p.headingLevel == 1) return true;
        String norm = normalise(p.text);
        if (norm.isEmpty() || norm.split("\\s+").length > 8) return false;
        return SECTION_PATTERNS.values().stream().anyMatch(pt -> pt.matcher(norm).matches());
    }

    private static String canonical(String name) {
        for (String key : SECTION_PATTERNS.keySet()) if (key.equalsIgnoreCase(name)) return key;
        return name;
    }

    static String normalise(String text) {
        return text.toLowerCase(Locale.ROOT).replace(' ', ' ').replaceAll("[:.\\s]+$", "").replaceAll("\\s+", " ").trim();
    }

    private static List<Para> bodyText(List<Para> paras) {
        List<Para> out = new ArrayList<>();
        for (Para p : paras) {
            if (p.inToc || p.headingLevel > 0 || p.text.isBlank()) continue;
            String style = (p.styleName == null ? "" : p.styleName).toLowerCase(Locale.ROOT);
            if (style.startsWith("toc") || style.contains("caption") || style.equals("title") || style.equals("subtitle")
                    || style.contains("footnote") || style.contains("header") || style.contains("footer")) continue;
            if (p.text.trim().length() < 40) continue;
            out.add(p);
        }
        return out;
    }

    // Parsing ---------------------------------------------------------------------------

    private void collectParagraphs(Element container, List<Para> out, boolean inTocSdt, Deque<boolean[]> fields) {
        for (Node n = container.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element e) || !W.equals(e.getNamespaceURI())) continue;
            switch (e.getLocalName()) {
                case "p" -> out.add(paragraph(e, out.size(), inTocSdt, fields));
                case "sdt" -> {
                    boolean toc = inTocSdt || isTocSdt(e);
                    if (toc) tocFieldPresent = true;
                    Element content = first(e, "sdtContent");
                    if (content != null) collectParagraphs(content, out, toc, fields);
                }
                case "customXml", "ins" -> collectParagraphs(e, out, inTocSdt, fields);
                default -> { }
            }
        }
    }

    private Para paragraph(Element p, int index, boolean inTocSdt, Deque<boolean[]> fields) {
        Para para = new Para();
        para.index = index;
        Element pPr = first(p, "pPr");
        String styleId = pPr == null ? null : attr(first(pPr, "pStyle"), "val");
        if (styleId == null) styleId = defaultParagraphStyle;
        para.styleId = styleId;
        Style style = styleId == null ? null : styles.get(styleId);
        para.styleName = style == null ? styleId : style.name();
        para.headingLevel = headingLevel(styleId, para.styleName);
        para.sectPr = pPr == null ? null : first(pPr, "sectPr");
        para.line = lineSpacing(pPr, styleId);
        boolean tocStyle = para.styleName != null && para.styleName.toLowerCase(Locale.ROOT).matches("^toc( heading| \\d+)?$");
        boolean tocNow = inTocSdt || tocStyle || anyToc(fields);
        StringBuilder text = new StringBuilder();
        NodeList all = p.getElementsByTagNameNS(W, "*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            switch (e.getLocalName()) {
                case "fldChar" -> {
                    String type = attr(e, "fldCharType");
                    if ("begin".equals(type)) fields.push(new boolean[] {false});
                    else if ("end".equals(type) && !fields.isEmpty()) fields.pop();
                }
                case "instrText" -> {
                    if (e.getTextContent().trim().toUpperCase(Locale.ROOT).startsWith("TOC")) {
                        tocFieldPresent = true;
                        if (!fields.isEmpty()) fields.peek()[0] = true;
                        tocNow = true;
                    }
                }
                case "fldSimple" -> {
                    if (attr(e, "instr") != null && attr(e, "instr").trim().toUpperCase(Locale.ROOT).startsWith("TOC")) tocFieldPresent = true;
                }
                case "hyperlink" -> {
                    String anchor = attr(e, "anchor");
                    if (anchor != null && anchor.startsWith("_Toc")) tocNow = true;
                }
                case "r" -> {
                    if (isInsideNestedParagraph(e, p)) break;
                    Run run = run(e, styleId);
                    if (!run.text.isEmpty()) {
                        para.runs.add(run);
                        text.append(run.text);
                    }
                }
                default -> { }
            }
            if (anyToc(fields)) tocNow = true;
        }
        para.text = text.toString();
        para.inToc = tocNow;
        return para;
    }

    private Run run(Element r, String paragraphStyle) {
        Run run = new Run();
        StringBuilder sb = new StringBuilder();
        for (Node n = r.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && W.equals(e.getNamespaceURI())) {
                if (e.getLocalName().equals("t")) sb.append(e.getTextContent());
                else if (e.getLocalName().equals("tab")) sb.append(' ');
            }
        }
        run.text = sb.toString();
        Element rPr = first(r, "rPr");
        String font = rPr == null ? null : fontOf(first(rPr, "rFonts"));
        Integer sz = rPr == null ? null : intAttr(first(rPr, "sz"), "val");
        String charStyle = rPr == null ? null : attr(first(rPr, "rStyle"), "val");
        if (font == null) font = styleFont(charStyle);
        if (sz == null) sz = styleSize(charStyle);
        if (font == null) font = styleFont(paragraphStyle);
        if (sz == null) sz = styleSize(paragraphStyle);
        run.font = font == null ? defaultFont : font;
        run.halfPoints = sz == null ? defaultHalfPoints : sz;
        return run;
    }

    private Double lineSpacing(Element pPr, String styleId) {
        Element spacing = pPr == null ? null : first(pPr, "spacing");
        Double direct = spacingValue(spacing);
        if (direct != null) return direct;
        Set<String> seen = new HashSet<>();
        String id = styleId;
        while (id != null && seen.add(id)) {
            Style s = styles.get(id);
            if (s == null) break;
            if (s.line() != null) return s.line();
            id = s.basedOn();
        }
        return defaultLine;
    }

    private static Double spacingValue(Element spacing) {
        if (spacing == null || attr(spacing, "line") == null) return null;
        double line = attrNum(spacing, "line", 240);
        String rule = attr(spacing, "lineRule");
        if (rule == null || rule.equals("auto")) return line / 240.0;
        return Double.NaN;
    }

    private String styleFont(String styleId) {
        Set<String> seen = new HashSet<>();
        String id = styleId;
        while (id != null && seen.add(id)) {
            Style s = styles.get(id);
            if (s == null) break;
            if (s.font() != null) return s.font();
            id = s.basedOn();
        }
        return null;
    }

    private Integer styleSize(String styleId) {
        Set<String> seen = new HashSet<>();
        String id = styleId;
        while (id != null && seen.add(id)) {
            Style s = styles.get(id);
            if (s == null) break;
            if (s.halfPoints() != null) return s.halfPoints();
            id = s.basedOn();
        }
        return null;
    }

    private String fontOf(Element rFonts) {
        if (rFonts == null) return null;
        String f = attr(rFonts, "ascii");
        if (f == null) f = attr(rFonts, "hAnsi");
        if (f != null) return f;
        String theme = attr(rFonts, "asciiTheme");
        if (theme == null) theme = attr(rFonts, "hAnsiTheme");
        if (theme == null) return null;
        return theme.startsWith("major") ? themeMajor : themeMinor;
    }

    private static int headingLevel(String styleId, String styleName) {
        String name = styleName == null ? "" : styleName.toLowerCase(Locale.ROOT);
        var m = Pattern.compile("^heading\\s*(\\d)$").matcher(name);
        if (m.matches()) return Integer.parseInt(m.group(1));
        if (styleId != null) {
            var m2 = Pattern.compile("^Heading(\\d)$").matcher(styleId);
            if (m2.matches()) return Integer.parseInt(m2.group(1));
        }
        return 0;
    }

    private static boolean isTocSdt(Element sdt) {
        Element pr = first(sdt, "sdtPr");
        if (pr == null) return false;
        NodeList galleries = pr.getElementsByTagNameNS(W, "docPartGallery");
        for (int i = 0; i < galleries.getLength(); i++) {
            String v = attr((Element) galleries.item(i), "val");
            if (v != null && v.toLowerCase(Locale.ROOT).contains("table of contents")) return true;
        }
        return false;
    }

    private static boolean anyToc(Deque<boolean[]> fields) {
        for (boolean[] f : fields) if (f[0]) return true;
        return false;
    }

    private static boolean isInsideNestedParagraph(Element run, Element paragraph) {
        for (Node n = run.getParentNode(); n != null && n != paragraph; n = n.getParentNode()) {
            if (n instanceof Element e && W.equals(e.getNamespaceURI()) && e.getLocalName().equals("p")) return true;
        }
        return false;
    }

    private List<Section> sections(Element body, List<Para> paras) {
        List<Section> out = new ArrayList<>();
        for (Para p : paras) if (p.sectPr != null) out.add(new Section(out.size(), p.sectPr, p.index));
        Element last = first(body, "sectPr");
        if (last != null) out.add(new Section(out.size(), last, Math.max(0, paras.size() - 1)));
        if (out.isEmpty()) out.add(new Section(0, body.getOwnerDocument().createElementNS(W, "sectPr"), Math.max(0, paras.size() - 1)));
        return out;
    }

    private static String pageFormat(Section s) {
        Element num = first(s.sectPr(), "pgNumType");
        String fmt = num == null ? null : attr(num, "fmt");
        return fmt == null ? "decimal" : fmt;
    }

    private void loadStyles(byte[] xml) throws Exception {
        if (xml == null) return;
        Element root = parse(xml).getDocumentElement();
        Element defaults = first(root, "docDefaults");
        if (defaults != null) {
            Element rPr = first(first(defaults, "rPrDefault"), "rPr");
            if (rPr != null) {
                String f = fontOf(first(rPr, "rFonts"));
                if (f != null) defaultFont = f;
                Integer sz = intAttr(first(rPr, "sz"), "val");
                if (sz != null) defaultHalfPoints = sz;
            }
            Element pPr = first(first(defaults, "pPrDefault"), "pPr");
            if (pPr != null) {
                Double line = spacingValue(first(pPr, "spacing"));
                if (line != null) defaultLine = line;
            }
        }
        for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element e) || !"style".equals(e.getLocalName())) continue;
            String id = attr(e, "styleId");
            if (id == null) continue;
            Element rPr = first(e, "rPr");
            Element pPr = first(e, "pPr");
            boolean isDefault = "1".equals(attr(e, "default")) || "true".equals(attr(e, "default"));
            String type = attr(e, "type");
            Style style = new Style(id, attr(first(e, "name"), "val"), type, attr(first(e, "basedOn"), "val"),
                    rPr == null ? null : fontOf(first(rPr, "rFonts")), rPr == null ? null : intAttr(first(rPr, "sz"), "val"),
                    pPr == null ? null : spacingValue(first(pPr, "spacing")), isDefault);
            styles.put(id, style);
            if (isDefault && "paragraph".equals(type)) defaultParagraphStyle = id;
        }
    }

    private void loadTheme(byte[] xml) throws Exception {
        if (xml == null) return;
        Document theme = parse(xml);
        NodeList majors = theme.getElementsByTagNameNS(A, "majorFont");
        NodeList minors = theme.getElementsByTagNameNS(A, "minorFont");
        themeMajor = latin(majors);
        themeMinor = latin(minors);
    }

    private static String latin(NodeList fonts) {
        if (fonts.getLength() == 0) return null;
        NodeList latin = ((Element) fonts.item(0)).getElementsByTagNameNS(A, "latin");
        return latin.getLength() == 0 ? null : ((Element) latin.item(0)).getAttribute("typeface");
    }

    // XML helpers ---------------------------------------------------------------------

    static Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new ByteArrayInputStream(xml));
    }

    static Map<String, byte[]> unzip(byte[] content, Set<String> wanted) throws IOException {
        Map<String, byte[]> out = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!wanted.contains(entry.getName())) continue;
                out.put(entry.getName(), readLimited(zip));
            }
        }
        return out;
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        var buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[64 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(chunk)) > 0) {
            total += n;
            if (total > MAX_PART_BYTES) throw new IOException("Document part too large");
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }

    static Element first(Element parent, String localName) {
        if (parent == null) return null;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && localName.equals(e.getLocalName())) return e;
        }
        return null;
    }

    static String attr(Element e, String localName) {
        if (e == null) return null;
        String v = e.getAttributeNS(W, localName);
        if (v == null || v.isEmpty()) v = e.getAttribute("w:" + localName);
        return v == null || v.isEmpty() ? null : v;
    }

    static double attrNum(Element e, String localName, double fallback) {
        String v = attr(e, localName);
        if (v == null) return fallback;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    static Integer intAttr(Element e, String localName) {
        String v = attr(e, localName);
        if (v == null) return null;
        try {
            return (int) Math.round(Double.parseDouble(v));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
