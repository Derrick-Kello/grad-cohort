package edu.university.grps.student.api;

import edu.university.grps.student.domain.Extension;
import edu.university.grps.student.domain.Supervision;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class StudentDtos {

    private StudentDtos() {}

    public record StudentSummaryDto(UUID id, UUID userId, String name, String number, String email,
                                    UUID programmeId, String programmeCode, String programmeName, String level,
                                    UUID cohortId, String cohortName, LocalDate enrolledOn, int currentSemester,
                                    String status, String statusReason, long approvedMilestones,
                                    int requiredMilestones, LocalDate checkpointDueOn, LocalDate programmeEndOn,
                                    long daysToCheckpoint, long daysToProgrammeEnd, UUID supervisorId,
                                    String supervisorName, String coSupervisorName, boolean userActive,
                                    Instant flaggedAt) {}

    public record SupervisionDto(UUID id, UUID supervisorId, String supervisorName, String type, LocalDate startsOn,
                                 LocalDate endsOn) {
        public static SupervisionDto of(Supervision s) {
            return new SupervisionDto(s.getId(), s.getSupervisor().getId(), s.getSupervisor().getFullName(),
                    s.getType().name(), s.getStartsOn(), s.getEndsOn());
        }
    }

    public record ExtensionDto(UUID id, int months, String reason, String approvedBy, LocalDate approvedOn) {
        public static ExtensionDto of(Extension e) {
            return new ExtensionDto(e.getId(), e.getMonths(), e.getReason(), e.getApprovedBy(), e.getApprovedOn());
        }
    }

    public record StudentDetailDto(StudentSummaryDto summary, List<SupervisionDto> supervisions,
                                   List<ExtensionDto> extensions) {}
}
