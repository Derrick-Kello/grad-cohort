package edu.university.grps.thesis.formatcheck;

import java.util.List;

public record FormatReport(List<RuleResult> results, Integer pageCount) {

    public boolean passed() {
        return results.stream().allMatch(r -> r.skipped() || r.passed());
    }
}
