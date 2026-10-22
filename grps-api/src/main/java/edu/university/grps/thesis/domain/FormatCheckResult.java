package edu.university.grps.thesis.domain;

import edu.university.grps.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "format_check_result")
public class FormatCheckResult extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thesis_submission_id")
    private ThesisSubmission thesis;

    @Column(name = "rule_code", nullable = false)
    private String ruleCode;

    @Column(nullable = false)
    private boolean passed;

    @Column(nullable = false)
    private boolean skipped;

    @Column(nullable = false)
    private String detail;

    private String location;
}
