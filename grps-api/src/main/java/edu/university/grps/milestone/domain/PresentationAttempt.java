package edu.university.grps.milestone.domain;

import edu.university.grps.common.BaseEntity;
import edu.university.grps.programme.domain.Venue;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** One sitting of a milestone. A failed or not-held milestone gets a new attempt; old ones stay visible. */
@Getter
@Setter
@Entity
@Table(name = "presentation_attempt")
public class PresentationAttempt extends BaseEntity {

    public enum Outcome { PASSED, FAILED, NOT_HELD }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_milestone_id")
    private StudentMilestone milestone;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "scheduled_start", nullable = false)
    private Instant scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private Instant scheduledEnd;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    private Venue venue;

    @Column(name = "online_link")
    private String onlineLink;

    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    private BigDecimal score;

    @Column(name = "panel_remarks")
    private String panelRemarks;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "recorded_at")
    private Instant recordedAt;

    @Column(name = "cancelled_reason")
    private String cancelledReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "reminder_48_sent", nullable = false)
    private boolean reminder48Sent;

    @Column(name = "reminder_24_sent", nullable = false)
    private boolean reminder24Sent;

    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("role asc")
    private List<PanelMember> panel = new ArrayList<>();

    public boolean isCancelled() {
        return cancelledAt != null;
    }

    /** Scheduled, not cancelled, outcome not yet recorded. */
    public boolean isOpen() {
        return !isCancelled() && outcome == null;
    }
}
