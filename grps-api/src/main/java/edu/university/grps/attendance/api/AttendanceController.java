package edu.university.grps.attendance.api;

import edu.university.grps.attendance.service.AttendanceService;
import edu.university.grps.attendance.service.AttendanceService.CourseDto;
import edu.university.grps.attendance.service.AttendanceService.CourseInput;
import edu.university.grps.attendance.service.AttendanceService.EligibilityRowDto;
import edu.university.grps.attendance.service.AttendanceService.PersonDto;
import edu.university.grps.attendance.service.AttendanceService.RosterDto;
import edu.university.grps.attendance.service.AttendanceService.RosterInput;
import edu.university.grps.attendance.service.AttendanceService.SessionDto;
import edu.university.grps.attendance.service.AttendanceService.SessionInput;
import edu.university.grps.attendance.service.AttendanceService.StudentAttendanceDto;
import edu.university.grps.common.Exports;
import edu.university.grps.common.Times;
import edu.university.grps.settings.service.SettingsService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class AttendanceController {

    private final AttendanceService service;
    private final SettingsService settings;
    private final Clock clock;

    public AttendanceController(AttendanceService service, SettingsService settings, Clock clock) {
        this.service = service;
        this.settings = settings;
        this.clock = clock;
    }

    public record IdsRequest(List<UUID> studentIds) {}

    public record ReasonRequest(String reason) {}

    // Admin course management

    @GetMapping("/admin/courses")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CourseDto> courses(@RequestParam(required = false) UUID semesterId) {
        return service.listCourses(semesterId);
    }

    @PostMapping("/admin/courses")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseDto createCourse(@RequestBody CourseInput input) {
        return service.createCourse(input);
    }

    @PutMapping("/admin/courses/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CourseDto updateCourse(@PathVariable UUID id, @RequestBody CourseInput input) {
        return service.updateCourse(id, input);
    }

    @GetMapping("/admin/courses/{id}/enrolments")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    public List<PersonDto> enrolments(@PathVariable UUID id) {
        return service.enrolments(id);
    }

    @PostMapping("/admin/courses/{id}/enrolments")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PersonDto> addEnrolments(@PathVariable UUID id, @RequestBody IdsRequest request) {
        return service.addEnrolments(id, request.studentIds());
    }

    @PostMapping(path = "/admin/courses/{id}/enrolments/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public List<PersonDto> importEnrolments(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return service.importEnrolments(id, file);
    }

    @DeleteMapping("/admin/courses/{id}/enrolments/{studentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PersonDto> removeEnrolment(@PathVariable UUID id, @PathVariable UUID studentId) {
        return service.removeEnrolment(id, studentId);
    }

    @PostMapping("/admin/eligibility/{id}/exception")
    @PreAuthorize("hasRole('ADMIN')")
    public EligibilityRowDto grantException(@PathVariable UUID id, @RequestBody ReasonRequest request) {
        return service.grantException(id, request.reason());
    }

    @DeleteMapping("/admin/eligibility/{id}/exception")
    @PreAuthorize("hasRole('ADMIN')")
    public EligibilityRowDto revokeException(@PathVariable UUID id) {
        return service.revokeException(id);
    }

    // Lecturer

    @GetMapping("/lecturer/courses")
    @PreAuthorize("hasRole('LECTURER')")
    public List<CourseDto> myCourses() {
        return service.myCourses();
    }

    @GetMapping("/lecturer/courses/{id}")
    @PreAuthorize("hasAnyRole('LECTURER','ADMIN')")
    public CourseDto course(@PathVariable UUID id) {
        return service.course(id);
    }

    @GetMapping("/lecturer/courses/{id}/sessions")
    @PreAuthorize("hasAnyRole('LECTURER','ADMIN')")
    public List<SessionDto> sessions(@PathVariable UUID id) {
        return service.sessions(id);
    }

    @PostMapping("/lecturer/courses/{id}/sessions")
    @PreAuthorize("hasAnyRole('LECTURER','ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionDto createSession(@PathVariable UUID id, @RequestBody SessionInput input) {
        return service.createSession(id, input);
    }

    @GetMapping("/lecturer/sessions/{id}/attendance")
    @PreAuthorize("hasAnyRole('LECTURER','ADMIN')")
    public RosterDto roster(@PathVariable UUID id) {
        return service.roster(id);
    }

    @PutMapping("/lecturer/sessions/{id}/attendance")
    @PreAuthorize("hasAnyRole('LECTURER','ADMIN')")
    public RosterDto saveRoster(@PathVariable UUID id, @RequestBody RosterInput input) {
        return service.saveRoster(id, input);
    }

    /** FR-ATT-09: JSON by default; CSV or PDF with {@code format=csv|pdf} or the Accept header. */
    @GetMapping("/lecturer/courses/{id}/eligibility")
    @PreAuthorize("hasAnyRole('LECTURER','ADMIN')")
    public ResponseEntity<?> eligibility(@PathVariable UUID id,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
                                         @RequestParam(required = false) String format,
                                         @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        List<EligibilityRowDto> rows = service.eligibilityList(id, asOf);
        Exports.Format f = Exports.resolve(format, accept);
        if (f == Exports.Format.JSON) return ResponseEntity.ok(rows);
        CourseDto course = service.course(id);
        LocalDate date = asOf == null ? Times.today(clock) : asOf;
        List<List<String>> table = rows.stream().map(r -> List.of(r.number(), r.name(), r.programmeCode(),
                String.valueOf(r.sessionsCounted()), String.valueOf(r.sessionsAttended()),
                r.attendancePct() == null ? "" : r.attendancePct().toPlainString() + "%", r.status(),
                r.exceptionReason() == null ? "" : r.exceptionReason())).toList();
        return Exports.file(f, "eligibility-" + course.code() + "-" + date, "Exam eligibility: " + course.code() + " " + course.title(),
                List.of(course.semesterLabel() + ". Attendance as of " + Times.formatDate(date) + ". Threshold "
                        + settings.decimal(SettingsService.ATTENDANCE_THRESHOLD).stripTrailingZeros().toPlainString() + "%.",
                        "Generated " + Times.formatDateTime(clock.instant()) + "."),
                List.of("Number", "Name", "Programme", "Sessions counted", "Attended", "Attendance", "Status", "Exception reason"), table);
    }

    // Student and supervisor

    @GetMapping("/students/me/attendance")
    @PreAuthorize("hasRole('STUDENT')")
    public List<StudentAttendanceDto> mine() {
        return service.mine();
    }

    @GetMapping("/students/{studentId}/attendance")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','SUPERVISOR')")
    public List<StudentAttendanceDto> forStudent(@PathVariable UUID studentId) {
        return service.forStudentId(studentId);
    }
}
