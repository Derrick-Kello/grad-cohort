package edu.university.grps.programme.domain;

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
@Table(name = "milestone_template")
public class MilestoneTemplate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_id")
    private Programme programme;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "is_final_pre_defense", nullable = false)
    private boolean finalPreDefense;

    @Column(name = "evidence_required", nullable = false)
    private boolean evidenceRequired;
}
