package edu.university.grps.thesis.service;

import edu.university.grps.audit.AuditService;
import edu.university.grps.common.ApiException;
import edu.university.grps.programme.repository.ProgrammeRepositories.ProgrammeRepository;
import edu.university.grps.thesis.domain.FormatRule;
import edu.university.grps.thesis.formatcheck.FormatChecker;
import edu.university.grps.thesis.repository.ThesisRepositories.FormatRuleRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-THS-05: the format rule set is configurable per programme. */
@Service
@Transactional
public class FormatRuleService {

    private final FormatRuleRepository rules;
    private final ProgrammeRepository programmes;
    private final AuditService audit;

    public FormatRuleService(FormatRuleRepository rules, ProgrammeRepository programmes, AuditService audit) {
        this.rules = rules;
        this.programmes = programmes;
        this.audit = audit;
    }

    public record RuleDto(UUID id, String ruleCode, Map<String, Object> parameters, boolean active) {
        static RuleDto of(FormatRule r) {
            return new RuleDto(r.getId(), r.getRuleCode(), r.getParameters(), r.isActive());
        }
    }

    public record RuleInput(String ruleCode, Map<String, Object> parameters, boolean active) {}

    @Transactional(readOnly = true)
    public List<RuleDto> list(UUID programmeId) {
        return rules.findByProgramme(programmeId).stream()
                .sorted((a, b) -> Integer.compare(FormatChecker.RULE_ORDER.indexOf(a.getRuleCode()), FormatChecker.RULE_ORDER.indexOf(b.getRuleCode())))
                .map(RuleDto::of).toList();
    }

    public List<RuleDto> update(UUID programmeId, List<RuleInput> inputs) {
        var programme = programmes.findById(programmeId).orElseThrow(() -> ApiException.notFound("Programme"));
        List<FormatRule> existing = rules.findByProgramme(programmeId);
        for (RuleInput in : inputs) {
            if (!FormatChecker.RULE_ORDER.contains(in.ruleCode())) throw ApiException.validation("Unknown rule " + in.ruleCode() + ".");
            FormatRule rule = existing.stream().filter(r -> r.getRuleCode().equals(in.ruleCode())).findFirst().orElseGet(() -> {
                FormatRule r = new FormatRule();
                r.setProgramme(programme);
                r.setRuleCode(in.ruleCode());
                return r;
            });
            Map<String, Object> before = rule.getParameters();
            rule.setParameters(in.parameters() == null ? Map.of() : in.parameters());
            rule.setActive(in.active());
            rule = rules.save(rule);
            audit.record("FORMAT_RULE_UPDATED", "FormatRule", rule.getId(), null,
                    before == null ? null : Map.of("parameters", before.toString()),
                    Map.of("rule", in.ruleCode(), "active", in.active(), "parameters", String.valueOf(in.parameters())));
        }
        return list(programmeId);
    }
}
