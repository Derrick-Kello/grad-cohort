package edu.university.grps.deadline.api;

import edu.university.grps.comment.domain.ReviewComment;
import edu.university.grps.comment.service.CommentService;
import edu.university.grps.comment.service.CommentService.CommentDto;
import edu.university.grps.milestone.api.MilestoneDtos.MilestoneDto;
import edu.university.grps.milestone.service.MilestoneService;
import edu.university.grps.student.api.StudentDtos.ExtensionDto;
import edu.university.grps.student.api.StudentDtos.StudentSummaryDto;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.domain.StudentStatus;
import edu.university.grps.student.repository.StudentRepositories.ExtensionRepository;
import edu.university.grps.student.repository.StudentRepositories.StudentRepository;
import edu.university.grps.student.service.StudentAccess;
import edu.university.grps.student.service.StudentService;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class RiskController {

    private final StudentRepository students;
    private final ExtensionRepository extensions;
    private final StudentService studentService;
    private final MilestoneService milestones;
    private final CommentService comments;
    private final StudentAccess access;

    public RiskController(StudentRepository students, ExtensionRepository extensions, StudentService studentService,
                          MilestoneService milestones, CommentService comments, StudentAccess access) {
        this.students = students;
        this.extensions = extensions;
        this.studentService = studentService;
        this.milestones = milestones;
        this.comments = comments;
        this.access = access;
    }

    public record ExpulsionCaseDto(StudentSummaryDto student, List<MilestoneDto> milestones, List<ExtensionDto> extensions,
                                   List<CommentDto> supervisorComments) {}

    /** FR-DLN-11: all AT_RISK and FLAGGED students with days remaining, filterable by programme and cohort. */
    @GetMapping("/at-risk")
    @Transactional(readOnly = true)
    public List<StudentSummaryDto> atRisk(@RequestParam(required = false) UUID programmeId,
                                          @RequestParam(required = false) UUID cohortId) {
        return studentService.summaries(students.findByStatuses(
                EnumSet.of(StudentStatus.AT_RISK, StudentStatus.FLAGGED_FOR_EXPULSION), programmeId, cohortId));
    }

    /** FR-DLN-07: each flagged student's milestones, attempts, extensions and supervisor comments. */
    @GetMapping("/expulsion-queue")
    @Transactional(readOnly = true)
    public List<ExpulsionCaseDto> expulsionQueue() {
        List<Student> flagged = students.findByStatuses(EnumSet.of(StudentStatus.FLAGGED_FOR_EXPULSION), null, null);
        return flagged.stream().map(s -> {
            List<MilestoneDto> ms = milestones.buildViews(s);
            Set<UUID> supervisors = new HashSet<>(access.supervisorIds(s.getId()));
            List<CommentDto> cs = comments.listForTargets(ReviewComment.TargetType.MILESTONE, ms.stream().map(MilestoneDto::id).toList())
                    .stream().filter(c -> supervisors.contains(c.authorId())).toList();
            return new ExpulsionCaseDto(studentService.summary(s), ms,
                    extensions.findByStudentIdOrderByApprovedOnDesc(s.getId()).stream().map(ExtensionDto::of).toList(), cs);
        }).toList();
    }
}
