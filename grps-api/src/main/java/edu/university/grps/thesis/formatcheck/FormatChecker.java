package edu.university.grps.thesis.formatcheck;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Runs the thesis format rules (FR-THS-03..05). DOCX gets the full rule set; PDF can only be checked for
 * page size, margins (approximate), page count and section headings, and the report says what was skipped.
 */
@Component
public class FormatChecker {

    public static final List<String> RULE_ORDER = List.of("FMT-PAGE", "FMT-MARGIN", "FMT-FONT", "FMT-SPACING", "FMT-HEAD",
            "FMT-PAGENUM", "FMT-SECTIONS", "FMT-ABSTRACT", "FMT-TOC", "FMT-REFS");
    static final Set<String> PDF_SUPPORTED = Set.of("FMT-PAGE", "FMT-MARGIN", "FMT-SECTIONS");

    /**
     * @param activeRules rule code to parameters for every active rule; codes not in the map are reported as switched off
     */
    public FormatReport check(byte[] content, String fileType, Map<String, Map<String, Object>> activeRules) {
        boolean pdf = "PDF".equalsIgnoreCase(fileType);
        RuleSet rules = new RuleSet(activeRules);
        FormatReport raw = pdf ? new PdfFormatChecker().check(content, rules) : new DocxFormatChecker().check(content, rules);
        List<RuleResult> ordered = new ArrayList<>();
        for (String code : RULE_ORDER) {
            RuleResult found = raw.results().stream().filter(r -> r.ruleCode().equals(code)).findFirst().orElse(null);
            if (!activeRules.containsKey(code)) {
                ordered.add(RuleResult.skip(code, "Rule switched off for this programme."));
            } else if (found != null) {
                ordered.add(found);
            } else if (pdf && !PDF_SUPPORTED.contains(code)) {
                ordered.add(RuleResult.skip(code, "Skipped: this check needs a DOCX upload."));
            } else {
                ordered.add(RuleResult.skip(code, "Could not be evaluated for this file."));
            }
        }
        return new FormatReport(ordered, raw.pageCount());
    }

    /** Typed access to rule parameters with defaults. */
    static final class RuleSet {
        private final Map<String, Map<String, Object>> rules;

        RuleSet(Map<String, Map<String, Object>> rules) {
            this.rules = rules;
        }

        boolean active(String code) {
            return rules.containsKey(code);
        }

        double num(String code, String key, double fallback) {
            Object v = rules.getOrDefault(code, Map.of()).get(key);
            if (v instanceof Number n) return n.doubleValue();
            if (v instanceof String s) {
                try {
                    return Double.parseDouble(s);
                } catch (NumberFormatException e) {
                    return fallback;
                }
            }
            return fallback;
        }

        String str(String code, String key, String fallback) {
            Object v = rules.getOrDefault(code, Map.of()).get(key);
            return v == null ? fallback : v.toString();
        }

        @SuppressWarnings("unchecked")
        List<String> list(String code, String key, List<String> fallback) {
            Object v = rules.getOrDefault(code, Map.of()).get(key);
            if (v instanceof List<?> l) return (List<String>) l.stream().map(Object::toString).toList();
            return fallback;
        }
    }

    /** "pages 12-40, 45" from a sorted list of page numbers. */
    static String ranges(String noun, List<Integer> numbers) {
        if (numbers.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(noun).append(' ');
        int start = numbers.get(0);
        int prev = start;
        int groups = 0;
        for (int i = 1; i <= numbers.size(); i++) {
            int cur = i < numbers.size() ? numbers.get(i) : Integer.MIN_VALUE;
            if (cur == prev + 1) {
                prev = cur;
                continue;
            }
            if (groups++ > 0) sb.append(", ");
            if (groups > 8) {
                sb.append("and more");
                break;
            }
            sb.append(start == prev ? String.valueOf(start) : start + "-" + prev);
            start = cur;
            prev = cur;
        }
        return sb.toString();
    }

    static String fmt(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
