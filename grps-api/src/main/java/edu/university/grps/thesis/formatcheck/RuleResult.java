package edu.university.grps.thesis.formatcheck;

/** One format rule outcome with a human-readable detail and where in the document it applies. */
public record RuleResult(String ruleCode, boolean passed, boolean skipped, String detail, String location) {

    public static RuleResult pass(String code, String detail) {
        return new RuleResult(code, true, false, detail, null);
    }

    public static RuleResult fail(String code, String detail, String location) {
        return new RuleResult(code, false, false, detail, location);
    }

    public static RuleResult skip(String code, String detail) {
        return new RuleResult(code, true, true, detail, null);
    }
}
