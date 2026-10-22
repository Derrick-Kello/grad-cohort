package edu.university.grps.deadline.domain;

import edu.university.grps.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Marks a deadline warning or evaluation as done for a student, which keeps the nightly job idempotent. */
@Getter
@Setter
@Entity
@Table(name = "deadline_notice")
public class DeadlineNotice extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "notice_key", nullable = false)
    private String noticeKey;
}
