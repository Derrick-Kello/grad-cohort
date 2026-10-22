package edu.university.grps.publication.domain;

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
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** A publication belongs to the student's record only after a librarian approves it (BR-08). */
@Getter
@Setter
@Entity
@Table(name = "publication")
public class Publication extends BaseEntity {

    public enum Type { JOURNAL_ARTICLE, CONFERENCE_PAPER, BOOK_CHAPTER, OTHER }

    public enum PubStatus { ACCEPTED, PUBLISHED }

    public enum ReviewStatus { DRAFT, SUBMITTED, CHANGES_REQUESTED, APPROVED, REJECTED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(name = "venue_name")
    private String venueName;

    @Column(nullable = false)
    private String authors;

    private Integer year;
    private String volume;
    private String issue;
    private String pages;
    private String doi;
    private String url;

    @Column(name = "issn_isbn")
    private String issnIsbn;

    @Enumerated(EnumType.STRING)
    @Column(name = "pub_status", nullable = false)
    private PubStatus pubStatus;

    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "file_name")
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    private ReviewStatus reviewStatus = ReviewStatus.DRAFT;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "venue_verified", nullable = false)
    private boolean venueVerified;

    @Column(name = "authorship_verified", nullable = false)
    private boolean authorshipVerified;

    @Column(name = "indexing_checked", nullable = false)
    private boolean indexingChecked;

    public boolean isEditableByStudent() {
        return reviewStatus == ReviewStatus.DRAFT || reviewStatus == ReviewStatus.CHANGES_REQUESTED;
    }
}
