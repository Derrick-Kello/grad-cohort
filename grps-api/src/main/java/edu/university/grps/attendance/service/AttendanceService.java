package edu.university.grps.attendance.service;

import edu.university.grps.attendance.domain.AttendanceRecord;
import edu.university.grps.attendance.domain.ClassSession;
import edu.university.grps.attendance.domain.Course;
import edu.university.grps.attendance.domain.Eligibility;
import edu.university.grps.attendance.repository.AttendanceRepositories.AttendanceRecordRepository;
import edu.university.grps.attendance.repository.AttendanceRepositories.ClassSessionRepository;
import edu.university.grps.attendance.repository.AttendanceRepositories.CourseRepository;
import edu.university.grps.attendance.repository.AttendanceRepositories.EligibilityRepository;
import edu.university.grps.audit.AuditService;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.Csv;
import edu.university.grps.common.ErrorCode;
import edu.university.grps.common.Times;
import edu.university.grps.notification.domain.NotificationCode;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.programme.domain.AcademicSemester;
import edu.university.grps.programme.repository.ProgrammeRepositories.AcademicSemesterRepository;
import edu.university.grps.security.CurrentUser;
import edu.university.grps.settings.service.SettingsService;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.domain.StudentStatus;
import edu.university.grps.student.repository.StudentRepositories.StudentRepository;
import edu.university.grps.student.service.StudentAccess;
import edu.university.grps.user.domain.AppUser;
import edu.university.grps.user.domain.RoleCode;
import edu.university.grps.user.repository.AppUserRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Lecture attendance and exam eligibility (section 12). */
@Service
@Transactional
public class AttendanceService {

    private final CourseRepository courses;
    private final ClassSessionRepository sessions;
    private final AttendanceRecordRepository records;
    private final EligibilityRepository eligibility;
    private final AcademicSemesterRepository semesters;
    private final AppUserRepository users;
    private final StudentRepository students;
    private final StudentAccess access;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public AttendanceService(CourseRepository courses, ClassSessionRepository sessions, AttendanceRecordRepository records,
                             EligibilityRepository eligibility, AcademicSemesterRepository semesters, AppUserRepository users,
                             StudentRepository students, StudentAccess access, SettingsService settings,
                             NotificationService notifications, AuditService audit, Clock clock) {
        this.courses = courses;
        this.sessions = sessions;
        this.records = records;
        this.eligibility = eligibility;
        this.semesters = semesters;
        this.users = users;
        this.students = students;
        this.access = access;
        this.settings = settings;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    // DTOs ---------------------------------------------------------------------------

    public record PersonDto(UUID id, String name, String number) {}

    public record CourseDto(UUID id, String code, String title, UUID semesterId, String semesterLabel, int creditHours,
                            List<PersonDto> lecturers, int studentCount, int sessionsHeld) {}

    public record SessionDto(UUID id, UUID courseId, LocalDate heldOn, LocalTime startsAt, LocalTime endsAt, String topic,
                             boolean attendanceTaken, int present, int late, int absent, int excused,
                             boolean lecturerCanEdit, boolean future) {}

    public record RosterEntryDto(UUID studentId, String name, String number, String programmeCode, String status,
                                 boolean recorded) {}

    public record RosterDto(SessionDto session, String courseCode, String courseTitle, List<RosterEntryDto> entries,
                            boolean editable, String editNote) {}

    public record EligibilityRowDto(UUID eligibilityId, UUID studentId, String name, String number, String programmeCode,
                                    int sessionsHeld, int sessionsCounted, int sessionsAttended, BigDecimal attendancePct,
                                    String status, String exceptionReason, int canMiss) {}

    public record StudentAttendanceDto(UUID courseId, String code, String title, String semesterLabel, int sessionsHeld,
                                       int sessionsCounted, int sessionsAttended, BigDecimal attendancePct, String status,
                                       String exceptionReason, int canMiss, BigDecimal threshold) {}

    public record CourseInput(String code, String title, UUID semesterId, Integer creditHours, List<UUID> lecturerIds) {}

    public record SessionInput(LocalDate heldOn, LocalTime startsAt, LocalTime endsAt, String topic) {}

    public record RosterEntry(UUID studentId, AttendanceRecord.Status status) {}

    public record RosterInput(List<RosterEntry> entries, String reason) {}

    // Courses (admin, FR-ATT-01) -----------------------------------------------------

    @Transactional(readOnly = true)
    public List<CourseDto> listCourses(UUID semesterId) {
        return courses.search(semesterId).stream().map(this::courseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<CourseDto> myCourses() {
        return courses.findForLecturer(CurrentUser.id()).stream().map(this::courseDto).toList();
    }

    @Transactional(readOnly = true)
    public CourseDto course(UUID id) {
        return courseDto(viewableCourse(id));
    }

    public CourseDto createCourse(CourseInput in) {
        Course c = new Course();
        applyCourse(c, in);
        c = courses.save(c);
        audit.record("COURSE_CREATED", "Course", c.getId(), Map.of("code", c.getCode(), "semester", c.getSemester().label()));
        return courseDto(c);
    }

    public CourseDto updateCourse(UUID id, CourseInput in) {
        Course c = courses.findWithLecturersById(id).orElseThrow(() -> ApiException.notFound("Course"));
        applyCourse(c, in);
        audit.record("COURSE_UPDATED", "Course", c.getId(), Map.of("code", c.getCode(), "lecturers", c.getLecturers().size()));
        return courseDto(c);
    }

    private void applyCourse(Course c, CourseInput in) {
        if (blank(in.code()) || blank(in.title()) || in.semesterId() == null) throw ApiException.validation("Code, title and semester are required.");
        AcademicSemester semester = semesters.findById(in.semesterId()).orElseThrow(() -> ApiException.notFound("Semester"));
        courses.findByCodeIgnoreCaseAndSemesterId(in.code().trim(), semester.getId())
                .filter(other -> !other.getId().equals(c.getId()))
                .ifPresent(other -> { throw new ApiException(ErrorCode.DUPLICATE, in.code().trim() + " already exists in " + semester.label() + "."); });
        c.setCode(in.code().trim().toUpperCase(Locale.ROOT));
        c.setTitle(in.title().trim());
        c.setSemester(semester);
        c.setCreditHours(in.creditHours() == null ? 3 : in.creditHours());
        if (in.lecturerIds() != null) {
            Set<AppUser> lecturers = new HashSet<>();
            for (UUID id : in.lecturerIds()) {
                AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("Lecturer"));
                if (!u.hasRole(RoleCode.LECTURER)) throw ApiException.rule(u.getFullName() + " does not hold the Lecturer role.");
                lecturers.add(u);
            }
            c.getLecturers().clear();
            c.getLecturers().addAll(lecturers);
        }
    }

    @Transactional(readOnly = true)
    public List<PersonDto> enrolments(UUID courseId) {
        Course c = viewableCourse(courseId);
        return c.getStudents().stream().map(s -> new PersonDto(s.getId(), s.getUser().getFullName(), s.getUser().getStaffOrStudentNo()))
                .sorted(Comparator.comparing(PersonDto::name)).toList();
    }

    public List<PersonDto> addEnrolments(UUID courseId, List<UUID> studentIds) {
        Course c = courses.findWithLecturersById(courseId).orElseThrow(() -> ApiException.notFound("Course"));
        int added = 0;
        for (UUID id : studentIds) {
            Student s = students.findById(id).orElseThrow(() -> ApiException.notFound("Student"));
            if (c.getStudents().add(s)) added++;
        }
        audit.record("COURSE_ENROLMENTS_ADDED", "Course", c.getId(), Map.of("added", added));
        courses.flush();
        recalculate(c.getId(), false);
        return enrolments(courseId);
    }

    public List<PersonDto> removeEnrolment(UUID courseId, UUID studentId) {
        Course c = courses.findWithLecturersById(courseId).orElseThrow(() -> ApiException.notFound("Course"));
        c.getStudents().removeIf(s -> s.getId().equals(studentId));
        eligibility.findByCourseIdAndStudentId(courseId, studentId).ifPresent(eligibility::delete);
        audit.record("COURSE_ENROLMENT_REMOVED", "Course", c.getId(), null, null, Map.of("studentId", studentId));
        return enrolments(courseId);
    }

    /** CSV of student numbers (one per line, or a "student_number" column). All rows must match or nothing is saved. */
    public List<PersonDto> importEnrolments(UUID courseId, MultipartFile file) {
        List<List<String>> rows;
        try {
            rows = Csv.parse(new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw ApiException.validation("The file could not be read.");
        }
        List<String> numbers = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            String v = rows.get(i).isEmpty() ? "" : rows.get(i).get(0).trim();
            if (i == 0 && v.toLowerCase(Locale.ROOT).replace(' ', '_').startsWith("student")) continue;
            if (!v.isEmpty()) numbers.add(v);
        }
        List<Map<String, Object>> errors = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < numbers.size(); i++) {
            var user = users.findByLogin(numbers.get(i));
            var student = user.flatMap(u -> students.findByUserId(u.getId()));
            if (student.isEmpty()) errors.add(Map.of("row", i + 1, "message", "No student with number " + numbers.get(i)));
            else ids.add(student.get().getId());
        }
        if (!errors.isEmpty()) throw new ApiException(ErrorCode.IMPORT_FAILED, "Nothing was imported. " + errors.size() + " row(s) failed.",
                Map.of("errors", errors));
        return addEnrolments(courseId, ids);
    }

    // Sessions and roster (lecturer, FR-ATT-02..04) --------------------------------------

    @Transactional(readOnly = true)
    public List<SessionDto> sessions(UUID courseId) {
        viewableCourse(courseId);
        List<ClassSession> list = sessions.findByCourseIdOrderByHeldOnDescStartsAtDesc(courseId);
        Map<UUID, List<AttendanceRecord>> byS = list.isEmpty() ? Map.of()
                : records.findBySessions(list.stream().map(ClassSession::getId).toList()).stream()
                .collect(Collectors.groupingBy(r -> r.getSession().getId()));
        return list.stream().map(s -> sessionDto(s, byS.getOrDefault(s.getId(), List.of()))).toList();
    }

    public SessionDto createSession(UUID courseId, SessionInput in) {
        Course c = lecturerCourse(courseId);
        if (in.heldOn() == null || in.startsAt() == null || in.endsAt() == null) throw ApiException.validation("Date, start and end time are required.");
        if (!in.endsAt().isAfter(in.startsAt())) throw ApiException.validation("The session must end after it starts.");
        AcademicSemester sem = c.getSemester();
        if (in.heldOn().isBefore(sem.getStartsOn()) || in.heldOn().isAfter(sem.getEndsOn())) {
            throw ApiException.validation("The date must fall within " + sem.label() + " (" + Times.formatDate(sem.getStartsOn())
                    + " to " + Times.formatDate(sem.getEndsOn()) + ").");
        }
        ClassSession s = new ClassSession();
        s.setCourse(c);
        s.setHeldOn(in.heldOn());
        s.setStartsAt(in.startsAt());
        s.setEndsAt(in.endsAt());
        s.setTopic(blank(in.topic()) ? null : in.topic().trim());
        s = sessions.save(s);
        audit.record("SESSION_CREATED", "ClassSession", s.getId(), Map.of("course", c.getCode(), "heldOn", in.heldOn()));
        return sessionDto(s, List.of());
    }

    @Transactional(readOnly = true)
    public RosterDto roster(UUID sessionId) {
        ClassSession s = sessions.findWithCourseById(sessionId).orElseThrow(() -> ApiException.notFound("Session"));
        Course c = viewableCourse(s.getCourse().getId());
        List<AttendanceRecord> recs = records.findBySession(sessionId);
        Map<UUID, AttendanceRecord> byStudent = recs.stream().collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r));
        List<RosterEntryDto> entries = c.getStudents().stream()
                .filter(st -> st.getStatus() != StudentStatus.EXPELLED || byStudent.containsKey(st.getId()))
                .map(st -> {
                    AttendanceRecord r = byStudent.get(st.getId());
                    return new RosterEntryDto(st.getId(), st.getUser().getFullName(), st.getUser().getStaffOrStudentNo(),
                            st.getProgramme().getCode(), r == null ? AttendanceRecord.Status.ABSENT.name() : r.getStatus().name(), r != null);
                })
                .sorted(Comparator.comparing(RosterEntryDto::name)).toList();
        SessionDto dto = sessionDto(s, recs);
        String note = null;
        boolean editable;
        if (dto.future()) {
            editable = false;
            note = "Attendance can be marked once the session has started.";
        } else if (dto.lecturerCanEdit()) {
            editable = true;
        } else {
            editable = CurrentUser.isAdmin();
            note = CurrentUser.isAdmin() ? "More than " + settings.integer(SettingsService.LECTURER_EDIT_DAYS)
                    + " days have passed; changes need a reason." : "More than " + settings.integer(SettingsService.LECTURER_EDIT_DAYS)
                    + " days have passed. Ask an admin to change this session.";
        }
        return new RosterDto(dto, c.getCode(), c.getTitle(), entries, editable, note);
    }

    /** Saves the whole roster in one request, then recalculates eligibility for the course. */
    public RosterDto saveRoster(UUID sessionId, RosterInput in) {
        ClassSession s = sessions.findWithCourseById(sessionId).orElseThrow(() -> ApiException.notFound("Session"));
        Course c = lecturerCourse(s.getCourse().getId());
        if (isFuture(s)) throw ApiException.rule("Sessions in the future cannot have attendance marked.");
        boolean pastWindow = !withinLecturerWindow(s);
        if (pastWindow) {
            if (!CurrentUser.isAdmin()) {
                throw new ApiException(ErrorCode.FORBIDDEN, "Attendance can be edited for " + settings.integer(SettingsService.LECTURER_EDIT_DAYS)
                        + " days. Ask an admin to change this session.");
            }
            if (blank(in.reason())) throw ApiException.validation("Give a reason for changing attendance after the edit window.");
        }
        Set<UUID> enrolled = c.getStudents().stream().map(Student::getId).collect(Collectors.toSet());
        Map<UUID, AttendanceRecord> existing = records.findBySession(sessionId).stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r));
        Map<UUID, AttendanceRecord.Status> wanted = new HashMap<>();
        for (RosterEntry e : in.entries() == null ? List.<RosterEntry>of() : in.entries()) {
            if (e.studentId() == null || e.status() == null) throw ApiException.validation("Every roster row needs a student and a status.");
            if (!enrolled.contains(e.studentId())) throw ApiException.validation("A student in the roster is not enrolled in " + c.getCode() + ".");
            wanted.put(e.studentId(), e.status());
        }
        // Unlisted enrolled students default to ABSENT (FR-ATT-03).
        for (UUID id : enrolled) wanted.putIfAbsent(id, existing.containsKey(id) ? existing.get(id).getStatus() : AttendanceRecord.Status.ABSENT);
        Map<String, Object> changes = new HashMap<>();
        for (var entry : wanted.entrySet()) {
            AttendanceRecord r = existing.get(entry.getKey());
            if (r == null) {
                Student st = c.getStudents().stream().filter(x -> x.getId().equals(entry.getKey())).findFirst().orElseThrow();
                if (st.getStatus() == StudentStatus.EXPELLED) continue;
                r = new AttendanceRecord();
                r.setSession(s);
                r.setStudent(st);
            } else if (r.getStatus() == entry.getValue()) {
                continue;
            } else {
                changes.put(entry.getKey().toString(), r.getStatus().name() + "->" + entry.getValue().name());
            }
            r.setStatus(entry.getValue());
            r.setMarkedBy(CurrentUser.id());
            r.setMarkedAt(clock.instant());
            records.save(r);
        }
        boolean first = s.getAttendanceTakenAt() == null;
        if (first) s.setAttendanceTakenAt(clock.instant());
        audit.record(first ? "ATTENDANCE_RECORDED" : "ATTENDANCE_CHANGED", "ClassSession", s.getId(), null,
                changes.isEmpty() ? null : changes,
                pastWindow ? Map.of("course", c.getCode(), "heldOn", s.getHeldOn(), "reason", in.reason())
                        : Map.of("course", c.getCode(), "heldOn", s.getHeldOn(), "students", wanted.size()));
        records.flush();
        recalculate(c.getId(), true);
        return roster(sessionId);
    }

    private boolean isFuture(ClassSession s) {
        LocalDateTime start = LocalDateTime.of(s.getHeldOn(), s.getStartsAt());
        return start.isAfter(LocalDateTime.now(clock.withZone(Times.ZONE)));
    }

    private boolean withinLecturerWindow(ClassSession s) {
        long days = ChronoUnit.DAYS.between(s.getHeldOn(), Times.today(clock));
        return days <= settings.integer(SettingsService.LECTURER_EDIT_DAYS);
    }

    // Eligibility (FR-ATT-05..10) ---------------------------------------------------------

    /** Recalculates stored eligibility for every enrolled student; sends N-14 when thresholds are crossed. */
    public void recalculate(UUID courseId, boolean notify) {
        Course c = courses.findWithLecturersById(courseId).orElseThrow();
        BigDecimal threshold = settings.decimal(SettingsService.ATTENDANCE_THRESHOLD);
        BigDecimal warning = settings.decimal(SettingsService.ATTENDANCE_WARNING);
        Map<UUID, List<AttendanceRecord.Status>> byStudent = statusesAsOf(c.getId(), Times.today(clock));
        List<UUID> lecturerIds = c.getLecturers().stream().map(AppUser::getId).toList();
        for (Student st : c.getStudents()) {
            EligibilityCalculator.Result r = EligibilityCalculator.compute(byStudent.getOrDefault(st.getId(), List.of()), threshold);
            Eligibility e = eligibility.findByCourseIdAndStudentId(c.getId(), st.getId()).orElseGet(() -> {
                Eligibility created = new Eligibility();
                created.setCourse(c);
                created.setStudent(st);
                return created;
            });
            e.setSessionsHeld(r.held());
            e.setSessionsCounted(r.counted());
            e.setSessionsAttended(r.attended());
            e.setAttendancePct(r.pct());
            if (e.getStatus() != Eligibility.Status.EXCEPTION) {
                e.setStatus(r.eligible() ? Eligibility.Status.ELIGIBLE : Eligibility.Status.NOT_ELIGIBLE);
            }
            if (r.pct() != null && notify) {
                List<UUID> recipients = new ArrayList<>(List.of(st.getUser().getId()));
                recipients.addAll(lecturerIds);
                if (r.pct().compareTo(threshold) < 0 && !e.isWarnedBelowThreshold()) {
                    e.setWarnedBelowThreshold(true);
                    e.setWarnedBelowWarning(true);
                    notifications.notify(recipients, NotificationCode.N14, "Attendance below " + threshold.stripTrailingZeros().toPlainString() + "% in " + c.getCode(),
                            st.getUser().getFullName() + "'s attendance in " + c.getCode() + " is " + r.pct() + "%. Below "
                                    + threshold.stripTrailingZeros().toPlainString() + "% the student cannot sit the exam.", "/student/attendance");
                } else if (r.pct().compareTo(warning) < 0 && r.pct().compareTo(threshold) >= 0 && !e.isWarnedBelowWarning()) {
                    e.setWarnedBelowWarning(true);
                    notifications.notify(recipients, NotificationCode.N14, "Attendance below " + warning.stripTrailingZeros().toPlainString() + "% in " + c.getCode(),
                            st.getUser().getFullName() + "'s attendance in " + c.getCode() + " is " + r.pct() + "%. "
                                    + r.canMiss() + " more absence(s) allowed before losing exam eligibility.", "/student/attendance");
                }
            }
            if (r.pct() != null && r.pct().compareTo(warning) >= 0) {
                e.setWarnedBelowWarning(false);
                e.setWarnedBelowThreshold(false);
            } else if (r.pct() != null && r.pct().compareTo(threshold) >= 0) {
                e.setWarnedBelowThreshold(false);
            }
            eligibility.save(e);
        }
    }

    /** EligibilityRecalcJob: full recalculation as a safety net to the on-save update. */
    public int recalculateAll() {
        int n = 0;
        for (Course c : courses.findAll()) {
            recalculate(c.getId(), false);
            n++;
        }
        return n;
    }

    /** FR-ATT-09: every student with percentage and status, as of a chosen date. Expelled students are left out (FR-DLN-09). */
    @Transactional(readOnly = true)
    public List<EligibilityRowDto> eligibilityList(UUID courseId, LocalDate asOf) {
        Course c = viewableCourse(courseId);
        LocalDate date = asOf == null ? Times.today(clock) : asOf;
        BigDecimal threshold = settings.decimal(SettingsService.ATTENDANCE_THRESHOLD);
        Map<UUID, List<AttendanceRecord.Status>> byStudent = statusesAsOf(courseId, date);
        Map<UUID, Eligibility> stored = eligibility.findByCourse(courseId).stream()
                .collect(Collectors.toMap(e -> e.getStudent().getId(), e -> e));
        return c.getStudents().stream()
                .filter(st -> st.getStatus() != StudentStatus.EXPELLED && st.getStatus() != StudentStatus.WITHDRAWN)
                .map(st -> {
                    EligibilityCalculator.Result r = EligibilityCalculator.compute(byStudent.getOrDefault(st.getId(), List.of()), threshold);
                    Eligibility e = stored.get(st.getId());
                    boolean exception = e != null && e.getStatus() == Eligibility.Status.EXCEPTION;
                    String status = exception ? "ELIGIBLE (EXCEPTION)" : r.eligible() ? "ELIGIBLE" : "NOT_ELIGIBLE";
                    return new EligibilityRowDto(e == null ? null : e.getId(), st.getId(), st.getUser().getFullName(),
                            st.getUser().getStaffOrStudentNo(), st.getProgramme().getCode(), r.held(), r.counted(), r.attended(),
                            r.pct(), status, exception ? e.getExceptionReason() : null, r.canMiss());
                })
                .sorted(Comparator.comparing(EligibilityRowDto::name)).toList();
    }

    /** FR-ATT-10: shown as ELIGIBLE (EXCEPTION) and audited. */
    public EligibilityRowDto grantException(UUID eligibilityId, String reason) {
        if (blank(reason)) throw ApiException.validation("A reason is required, for example documented medical leave.");
        Eligibility e = eligibility.findWithDetailsById(eligibilityId).orElseThrow(() -> ApiException.notFound("Eligibility"));
        Eligibility.Status old = e.getStatus();
        e.setStatus(Eligibility.Status.EXCEPTION);
        e.setExceptionReason(reason.trim());
        audit.record("ELIGIBILITY_EXCEPTION", "Eligibility", e.getId(), e.getStudent().getId(), Map.of("status", old.name()),
                Map.of("status", "EXCEPTION", "reason", reason.trim(), "course", e.getCourse().getCode()));
        return eligibilityList(e.getCourse().getId(), null).stream().filter(r -> r.studentId().equals(e.getStudent().getId()))
                .findFirst().orElseThrow();
    }

    public EligibilityRowDto revokeException(UUID eligibilityId) {
        Eligibility e = eligibility.findWithDetailsById(eligibilityId).orElseThrow(() -> ApiException.notFound("Eligibility"));
        if (e.getStatus() != Eligibility.Status.EXCEPTION) throw ApiException.invalidState("No exception is in place.");
        e.setStatus(Eligibility.Status.NOT_ELIGIBLE);
        e.setExceptionReason(null);
        audit.record("ELIGIBILITY_EXCEPTION_REVOKED", "Eligibility", e.getId(), e.getStudent().getId(), Map.of("status", "EXCEPTION"), Map.of());
        eligibility.flush();
        recalculate(e.getCourse().getId(), false);
        return eligibilityList(e.getCourse().getId(), null).stream().filter(r -> r.studentId().equals(e.getStudent().getId()))
                .findFirst().orElseThrow();
    }

    /** FR-ATT-07: per course, sessions held, attended, percentage, status and how many more can be missed. */
    @Transactional(readOnly = true)
    public List<StudentAttendanceDto> forStudent(Student s) {
        BigDecimal threshold = settings.decimal(SettingsService.ATTENDANCE_THRESHOLD);
        Map<UUID, Eligibility> stored = eligibility.findByStudent(s.getId()).stream()
                .collect(Collectors.toMap(e -> e.getCourse().getId(), e -> e));
        List<StudentAttendanceDto> out = new ArrayList<>();
        for (Course c : courses.findForStudent(s.getId())) {
            EligibilityCalculator.Result r = EligibilityCalculator.compute(
                    statusesAsOf(c.getId(), Times.today(clock)).getOrDefault(s.getId(), List.of()), threshold);
            Eligibility e = stored.get(c.getId());
            boolean exception = e != null && e.getStatus() == Eligibility.Status.EXCEPTION;
            out.add(new StudentAttendanceDto(c.getId(), c.getCode(), c.getTitle(), c.getSemester().label(), r.held(), r.counted(),
                    r.attended(), r.pct(), exception ? "ELIGIBLE (EXCEPTION)" : r.eligible() ? "ELIGIBLE" : "NOT_ELIGIBLE",
                    exception ? e.getExceptionReason() : null, r.canMiss(), threshold));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<StudentAttendanceDto> mine() {
        return forStudent(access.currentStudent());
    }

    @Transactional(readOnly = true)
    public List<StudentAttendanceDto> forStudentId(UUID studentId) {
        return forStudent(access.requireViewable(studentId));
    }

    private Map<UUID, List<AttendanceRecord.Status>> statusesAsOf(UUID courseId, LocalDate asOf) {
        List<ClassSession> held = sessions.findHeld(courseId, asOf);
        if (held.isEmpty()) return Map.of();
        Map<UUID, List<AttendanceRecord.Status>> out = new HashMap<>();
        for (AttendanceRecord r : records.findBySessions(held.stream().map(ClassSession::getId).toList())) {
            out.computeIfAbsent(r.getStudent().getId(), k -> new ArrayList<>()).add(r.getStatus());
        }
        return out;
    }

    // Access helpers ------------------------------------------------------------------------

    /** Admins see every course; lecturers only their own (SEC-04). */
    private Course viewableCourse(UUID id) {
        Course c = courses.findWithLecturersById(id).orElseThrow(() -> ApiException.notFound("Course"));
        if (CurrentUser.isAdmin() || CurrentUser.hasRole(RoleCode.SECRETARY)) return c;
        UUID me = CurrentUser.id();
        if (c.getLecturers().stream().anyMatch(l -> l.getId().equals(me))) return c;
        throw ApiException.notFound("Course");
    }

    private Course lecturerCourse(UUID id) {
        Course c = courses.findWithLecturersById(id).orElseThrow(() -> ApiException.notFound("Course"));
        if (CurrentUser.isAdmin()) return c;
        UUID me = CurrentUser.id();
        if (CurrentUser.hasRole(RoleCode.LECTURER) && c.getLecturers().stream().anyMatch(l -> l.getId().equals(me))) return c;
        throw ApiException.notFound("Course");
    }

    private CourseDto courseDto(Course c) {
        int held = sessions.findHeld(c.getId(), Times.today(clock)).size();
        return new CourseDto(c.getId(), c.getCode(), c.getTitle(), c.getSemester().getId(), c.getSemester().label(), c.getCreditHours(),
                c.getLecturers().stream().map(l -> new PersonDto(l.getId(), l.getFullName(), l.getStaffOrStudentNo()))
                        .sorted(Comparator.comparing(PersonDto::name)).toList(),
                c.getStudents().size(), held);
    }

    private SessionDto sessionDto(ClassSession s, List<AttendanceRecord> recs) {
        Map<AttendanceRecord.Status, Long> counts = recs.stream().collect(Collectors.groupingBy(AttendanceRecord::getStatus, Collectors.counting()));
        return new SessionDto(s.getId(), s.getCourse().getId(), s.getHeldOn(), s.getStartsAt(), s.getEndsAt(), s.getTopic(),
                s.getAttendanceTakenAt() != null, counts.getOrDefault(AttendanceRecord.Status.PRESENT, 0L).intValue(),
                counts.getOrDefault(AttendanceRecord.Status.LATE, 0L).intValue(), counts.getOrDefault(AttendanceRecord.Status.ABSENT, 0L).intValue(),
                counts.getOrDefault(AttendanceRecord.Status.EXCUSED, 0L).intValue(), !isFuture(s) && withinLecturerWindow(s), isFuture(s));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
