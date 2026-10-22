package edu.university.grps.dashboard.domain;

import edu.university.grps.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** A generated progress statement; its verification code lets a third party confirm it (FR-ACH-03). */
@Getter
@Setter
@Entity
@Table(name = "progress_statement")
public class ProgressStatement extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "verification_code", nullable = false, unique = true)
    private String verificationCode;

    @Column(name = "approved_items", nullable = false)
    private int approvedItems;
}
