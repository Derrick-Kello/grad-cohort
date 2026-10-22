package edu.university.grps.thesis.domain;

import edu.university.grps.common.BaseEntity;
import edu.university.grps.student.domain.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** One uploaded thesis version. Each upload creates a new version and restarts both gates (FR-THS-12). */
@Getter
@Setter
@Entity
@Table(name = "thesis_submission")
public class ThesisSubmission extends BaseEntity {

    public enum Status {
        UPLOADED, FORMAT_CHECKING, FORMAT_FAILED, FORMAT_PASSED, SCREENING, SCREENING_REJECTED, SCREENING_PASSED,
        ACCEPTED_FOR_EXAMINATION
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(nullable = false)
    private String title;

    @Column(name = "abstract", nullable = false)
    private String abstractText;

    @Column(nullable = false)
    private String keywords;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_type", nullable = false)
    private String fileType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.UPLOADED;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "similarity_pct")
    private BigDecimal similarityPct;

    @Column(name = "ai_pct")
    private BigDecimal aiPct;

    @Column(name = "limit_applied_pct")
    private BigDecimal limitAppliedPct;

    @Column(name = "ai_limit_applied_pct")
    private BigDecimal aiLimitAppliedPct;

    @Column(name = "provider_ref")
    private String providerRef;

    @Column(name = "provider_report_key")
    private String providerReportKey;

    @Column(name = "provider_report_url")
    private String providerReportUrl;

    @Column(name = "screening_manual", nullable = false)
    private boolean screeningManual;

    @Column(name = "override_by")
    private UUID overrideBy;

    @Column(name = "override_reason")
    private String overrideReason;

    @Column(name = "override_at")
    private Instant overrideAt;

    /** A new version can be uploaded only when the latest one was rejected at a gate. */
    public boolean allowsResubmission() {
        return status == Status.FORMAT_FAILED || status == Status.SCREENING_REJECTED;
    }
}
