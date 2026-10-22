package edu.university.grps.evidence.domain;

import edu.university.grps.common.BaseEntity;
import edu.university.grps.milestone.domain.StudentMilestone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** A file proving a presentation took place. Replaced files are kept as earlier versions (FR-EVD-04). */
@Getter
@Setter
@Entity
@Table(name = "evidence_file")
public class EvidenceFile extends BaseEntity {

    public enum Kind { SLIDES, REPORT, PANEL_SHEET, PHOTO, OTHER }

    public enum ScanStatus { PENDING_SCAN, CLEAN, INFECTED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_milestone_id")
    private StudentMilestone milestone;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(name = "file_version", nullable = false)
    private int fileVersion = 1;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false)
    private ScanStatus scanStatus;

    public boolean isCurrent() {
        return supersededBy == null;
    }
}
