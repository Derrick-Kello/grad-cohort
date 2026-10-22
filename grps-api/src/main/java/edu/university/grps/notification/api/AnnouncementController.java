package edu.university.grps.notification.api;

import edu.university.grps.audit.AuditService;
import edu.university.grps.common.ApiException;
import edu.university.grps.notification.domain.NotificationCode;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.repository.StudentRepositories.StudentRepository;
import edu.university.grps.user.domain.RoleCode;
import edu.university.grps.user.service.UserDirectory;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-NTF-05: announcements targeted by role, programme, cohort or individual. */
@RestController
@RequestMapping("/api/v1/admin/announcements")
@PreAuthorize("hasRole('ADMIN')")
public class AnnouncementController {

    private final NotificationService notifications;
    private final UserDirectory directory;
    private final StudentRepository students;
    private final AuditService audit;

    public AnnouncementController(NotificationService notifications, UserDirectory directory, StudentRepository students,
                                  AuditService audit) {
        this.notifications = notifications;
        this.directory = directory;
        this.students = students;
        this.audit = audit;
    }

    public record AnnouncementRequest(String title, String body, List<RoleCode> roles, UUID programmeId, UUID cohortId,
                                      List<UUID> userIds) {}

    @PostMapping
    @Transactional
    public Map<String, Integer> send(@RequestBody AnnouncementRequest request) {
        if (request.title() == null || request.title().isBlank() || request.body() == null || request.body().isBlank()) {
            throw ApiException.validation("Title and message are required.");
        }
        Set<UUID> recipients = new LinkedHashSet<>();
        if (request.roles() != null) request.roles().forEach(r -> recipients.addAll(directory.activeIdsWithRole(r)));
        if (request.programmeId() != null || request.cohortId() != null) {
            for (Student s : students.findForReport(request.programmeId(), request.cohortId())) {
                if (!s.isClosed()) recipients.add(s.getUser().getId());
            }
        }
        if (request.userIds() != null) recipients.addAll(request.userIds());
        if (recipients.isEmpty()) throw ApiException.validation("Choose at least one recipient group.");
        notifications.notify(recipients, NotificationCode.ANN, request.title().trim(), request.body().trim(), "/notifications", false);
        audit.record("ANNOUNCEMENT_SENT", "Notification", null, Map.of("title", request.title().trim(), "recipients", recipients.size()));
        return Map.of("recipients", recipients.size());
    }
}
