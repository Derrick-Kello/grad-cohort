package edu.university.grps.milestone.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MilestoneDtos {

    private MilestoneDtos() {}

    public record PanelMemberDto(UUID userId, String name, String externalName, String role) {}

    public record AttemptDto(UUID id, UUID milestoneId, int attemptNo, Instant scheduledStart, Instant scheduledEnd,
                             UUID venueId, String venueName, String onlineLink, String outcome, BigDecimal score,
                             String panelRemarks, String recordedByName, Instant recordedAt, String cancelledReason,
                             Instant cancelledAt, List<PanelMemberDto> panel, String state) {}

    public record MilestoneDto(UUID id, UUID studentId, int sequenceNo, String label, String title,
                               boolean finalPreDefense, boolean evidenceRequired, String status, String lockedReason,
                               Instant availableAt, Instant approvedAt, String approvedByName, long evidenceCount,
                               int failedAttempts, int maxAttempts, AttemptDto currentAttempt, List<AttemptDto> attempts) {}

    /** A row in the scheduling board, secretary calendar and outcome list. */
    public record PresentationRowDto(UUID attemptId, UUID milestoneId, UUID studentId, String studentName,
                                     String studentNumber, String programmeCode, int sequenceNo, String milestoneTitle,
                                     boolean finalPreDefense, int attemptNo, Instant scheduledStart, Instant scheduledEnd,
                                     String venueName, String onlineLink, String outcome, String state,
                                     List<PanelMemberDto> panel) {}

    public record SchedulableDto(UUID milestoneId, UUID studentId, String studentName, String studentNumber,
                                 String programmeCode, String cohortName, int sequenceNo, String title,
                                 boolean finalPreDefense, int nextAttemptNo, Instant availableAt, long daysAvailable,
                                 String studentStatus) {}

    public record VerificationItemDto(UUID milestoneId, UUID studentId, String studentName, String studentNumber,
                                      String programmeCode, int sequenceNo, String title, boolean evidenceRequired,
                                      String status, Instant outcomeAt, long evidenceCount, long daysWaiting,
                                      UUID attemptId, BigDecimal score, String panelRemarks) {}

    public record ScheduleResult(AttemptDto attempt, List<String> warnings) {}
}
