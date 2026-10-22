package edu.university.grps.dev;

import edu.university.grps.attendance.domain.AttendanceRecord;
import edu.university.grps.attendance.domain.ClassSession;
import edu.university.grps.attendance.domain.Course;
import edu.university.grps.attendance.repository.AttendanceRepositories.AttendanceRecordRepository;
import edu.university.grps.attendance.repository.AttendanceRepositories.ClassSessionRepository;
import edu.university.grps.attendance.repository.AttendanceRepositories.CourseRepository;
import edu.university.grps.attendance.service.AttendanceService;
import edu.university.grps.common.Times;
import edu.university.grps.deadline.service.DeadlineService;
import edu.university.grps.milestone.domain.MilestoneStatus;
import edu.university.grps.milestone.domain.PanelMember;
import edu.university.grps.milestone.domain.PresentationAttempt;
import edu.university.grps.milestone.domain.StudentMilestone;
import edu.university.grps.milestone.repository.MilestoneRepositories.PresentationAttemptRepository;
import edu.university.grps.milestone.repository.MilestoneRepositories.StudentMilestoneRepository;
import edu.university.grps.programme.domain.AcademicSemester;
import edu.university.grps.programme.domain.Cohort;
import edu.university.grps.programme.domain.MilestoneTemplate;
import edu.university.grps.programme.domain.Programme;
import edu.university.grps.programme.domain.Venue;
import edu.university.grps.programme.repository.ProgrammeRepositories.AcademicSemesterRepository;
import edu.university.grps.programme.repository.ProgrammeRepositories.CohortRepository;
import edu.university.grps.programme.repository.ProgrammeRepositories.MilestoneTemplateRepository;
import edu.university.grps.programme.repository.ProgrammeRepositories.ProgrammeRepository;
import edu.university.grps.programme.repository.ProgrammeRepositories.VenueRepository;
import edu.university.grps.publication.domain.Publication;
import edu.university.grps.publication.repository.PublicationRepository;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.domain.StudentStatus;
import edu.university.grps.student.domain.Supervision;
import edu.university.grps.student.repository.StudentRepositories.StudentRepository;
import edu.university.grps.student.repository.StudentRepositories.SupervisionRepository;
import edu.university.grps.student.service.DueDates;
import edu.university.grps.user.domain.AppUser;
import edu.university.grps.user.domain.RoleCode;
import edu.university.grps.user.repository.AppUserRepository;
import edu.university.grps.user.repository.RoleRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds a demo graduate school on the local profile when the database has no users: one account per role and
 * students at different stages (on track, awaiting verification, at risk, flagged, ready for thesis, new).
 * Every demo account uses the password "Graduate2026".
 */
@Component
@Profile({"local", "demo"})
public class DemoDataSeeder implements ApplicationRunner {

    public static final String PASSWORD = "Graduate2026";
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final ProgrammeRepository programmes;
    private final MilestoneTemplateRepository templates;
    private final AcademicSemesterRepository semesters;
    private final CohortRepository cohorts;
    private final VenueRepository venues;
    private final StudentRepository students;
    private final SupervisionRepository supervisions;
    private final StudentMilestoneRepository milestones;
    private final PresentationAttemptRepository attempts;
    private final PublicationRepository publications;
    private final CourseRepository courses;
    private final ClassSessionRepository sessions;
    private final AttendanceRecordRepository records;
    private final AttendanceService attendance;
    private final DeadlineService deadlines;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private final Clock clock;

    private String hash;

    public DemoDataSeeder(AppUserRepository users, RoleRepository roles, ProgrammeRepository programmes,
                          MilestoneTemplateRepository templates, AcademicSemesterRepository semesters, CohortRepository cohorts,
                          VenueRepository venues, StudentRepository students, SupervisionRepository supervisions,
                          StudentMilestoneRepository milestones, PresentationAttemptRepository attempts,
                          PublicationRepository publications, CourseRepository courses, ClassSessionRepository sessions,
                          AttendanceRecordRepository records, AttendanceService attendance, DeadlineService deadlines,
                          PasswordEncoder encoder, TransactionTemplate tx, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.programmes = programmes;
        this.templates = templates;
        this.semesters = semesters;
        this.cohorts = cohorts;
        this.venues = venues;
        this.students = students;
        this.supervisions = supervisions;
        this.milestones = milestones;
        this.attempts = attempts;
        this.publications = publications;
        this.courses = courses;
        this.sessions = sessions;
        this.records = records;
        this.attendance = attendance;
        this.deadlines = deadlines;
        this.encoder = encoder;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        log.info("Seeding local demo data (password for every account: {})", PASSWORD);
        hash = encoder.encode(PASSWORD);
        tx.executeWithoutResult(s -> seed());
        deadlines.evaluateAll();
        log.info("Demo data ready. Sign in as admin@grps.local, secretary@grps.local, librarian@grps.local, "
                + "lecturer@grps.local (also a supervisor) or student@grps.local.");
    }

    private void seed() {
        LocalDate today = Times.today(clock);
        user("superadmin@grps.local", "SA-0001", "Selorm", "Agbeko", RoleCode.SUPER_ADMIN, RoleCode.ADMIN);
        AppUser admin = user("admin@grps.local", "GS-1001", "Akosua", "Mensah", RoleCode.ADMIN);
        user("secretary@grps.local", "GS-1002", "Kwame", "Boateng", RoleCode.SECRETARY);
        user("librarian@grps.local", "LIB-2001", "Efua", "Asante", RoleCode.LIBRARIAN);
        AppUser lecturer = user("lecturer@grps.local", "STF-3001", "Yaw", "Owusu", RoleCode.LECTURER, RoleCode.SUPERVISOR);
        AppUser lecturer2 = user("abena.darko@grps.local", "STF-3002", "Abena", "Darko", RoleCode.LECTURER, RoleCode.SUPERVISOR);
        AppUser lecturer3 = user("kojo.amponsah@grps.local", "STF-3003", "Kojo", "Amponsah", RoleCode.LECTURER);

        // Academic calendar around today's date.
        int year = today.getMonthValue() >= 8 ? today.getYear() : today.getYear() - 1;
        List<AcademicSemester> sems = new ArrayList<>();
        for (int y = year - 2; y <= year; y++) {
            sems.add(semester(y + "/" + (y + 1), 1, LocalDate.of(y, 8, 15), LocalDate.of(y + 1, 1, 31)));
            sems.add(semester(y + "/" + (y + 1), 2, LocalDate.of(y + 1, 2, 1), LocalDate.of(y + 1, 7, 31)));
        }
        AcademicSemester current = sems.stream().filter(s -> s.contains(today)).findFirst().orElse(sems.get(sems.size() - 2));

        Programme masters = programmes.findByCodeIgnoreCase("MASTERS").orElseThrow();
        Programme phd = programmes.findByCodeIgnoreCase("PHD").orElseThrow();
        Cohort m24 = cohort(masters, (year - 2) + " Intake", sems.get(0));
        Cohort m25 = cohort(masters, (year - 1) + " Intake", sems.get(2));
        Cohort m26 = cohort(masters, year + " Intake", sems.get(4));
        Cohort p25 = cohort(phd, (year - 1) + " Intake", sems.get(2));
        Cohort p26 = cohort(phd, year + " Intake", sems.get(4));
        List<Venue> venueList = venues.findAllByOrderByName();
        Venue roomA = venueList.stream().filter(v -> v.getName().endsWith("Room A")).findFirst().orElse(venueList.get(0));

        // On track Masters student with a passed presentation waiting for evidence.
        Student ama = student("student@grps.local", "PG-24001", "Ama", "Serwaa", masters, m25, today.minusMonths(13).minusDays(10), lecturer);
        approve(ama, 2, admin, roomA, lecturer2);
        passedAwaiting(ama, 3, roomA, lecturer2, today.minusDays(3));

        // PhD student past the 18-month checkpoint with three approvals: at risk.
        Student kwabena = student("kwabena.osei@grps.local", "PG-23011", "Kwabena", "Osei", phd, p25, today.minusMonths(20).minusDays(5), lecturer2);
        approve(kwabena, 3, admin, roomA, lecturer);

        // Masters student past programme end with one milestone outstanding: flagged.
        Student esi = student("esi.badu@grps.local", "PG-22007", "Esi", "Badu", masters, m24, today.minusMonths(25).minusDays(12), lecturer);
        approve(esi, 4, admin, roomA, lecturer2);
        failed(esi, 5, roomA, lecturer2, today.minusMonths(2), "Results chapter incomplete; the panel asked for a full analysis.");

        // Masters student with every presentation approved: thesis submission open.
        Student adwoa = student("adwoa.nyarko@grps.local", "PG-23004", "Adwoa", "Nyarko", masters, m24, today.minusMonths(22), lecturer2);
        approve(adwoa, 5, admin, roomA, lecturer);

        // PhD student in semester 2 whose first presentation was yesterday and needs an outcome.
        Student kweku = student("kweku.mensah@grps.local", "PG-25020", "Kweku", "Mensah", phd, p26, today.minusMonths(9), lecturer);
        openFirst(kweku);
        scheduled(kweku, 1, roomA, List.of(lecturer2, lecturer3), today.minusDays(1).atTime(10, 0));

        // New Masters student in semester 1: everything locked.
        Student akua = student("akua.agyeman@grps.local", "PG-26031", "Akua", "Agyeman", masters, m26, today.minusMonths(2), lecturer2);

        // A second PhD student with milestone 1 open and scheduled next week.
        Student nana = student("nana.ofori@grps.local", "PG-25022", "Nana", "Ofori", phd, p26, today.minusMonths(8), lecturer2);
        openFirst(nana);
        scheduled(nana, 1, roomA, List.of(lecturer, lecturer3), today.plusDays(6).atTime(9, 30));

        // Publications: one approved, one in the librarian queue, one draft.
        publication(ama, "Soil moisture sensing with low-cost capacitive probes in the Guinea savanna",
                Publication.Type.JOURNAL_ARTICLE, "West African Journal of Applied Ecology", "10.4314/wajae.v31i2.5",
                Publication.ReviewStatus.APPROVED, today.minusMonths(4), current);
        publication(ama, "Edge inference for smallholder irrigation scheduling", Publication.Type.CONFERENCE_PAPER,
                "IEEE AFRICON", "10.1109/AFRICON.2026.1234567", Publication.ReviewStatus.SUBMITTED, today.minusDays(4), current);
        publication(ama, "A review of sensor networks for rain-fed agriculture", Publication.Type.OTHER, null, null,
                Publication.ReviewStatus.DRAFT, null, current);
        publication(kwabena, "Microfinance access and household resilience in northern Ghana", Publication.Type.JOURNAL_ARTICLE,
                "Ghana Journal of Development Studies", "10.4314/gjds.v22i1.3", Publication.ReviewStatus.SUBMITTED,
                today.minusDays(9), current);

        // Courses this semester with ten past sessions; Akua attends six (60%, not eligible).
        Course methods = course("GRS 701", "Advanced Research Methods", current, Set.of(lecturer), Set.of(ama, kweku, akua, nana));
        Course stats = course("GRS 703", "Applied Statistics for Research", current, Set.of(lecturer3, lecturer), Set.of(ama, akua, nana));
        attendanceHistory(methods, current, today, 10, akua, 6);
        attendanceHistory(stats, current, today, 6, akua, 4);
        attendance.recalculate(methods.getId(), false);
        attendance.recalculate(stats.getId(), false);
    }

    // Builders ------------------------------------------------------------------------

    private AppUser user(String email, String number, String first, String last, RoleCode... roleCodes) {
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setStaffOrStudentNo(number);
        u.setFirstName(first);
        u.setLastName(last);
        u.setPasswordHash(hash);
        u.setActive(true);
        Set<edu.university.grps.user.domain.Role> set = new HashSet<>();
        for (RoleCode r : roleCodes) set.add(roles.findByCode(r).orElseThrow());
        u.setRoles(set);
        return users.save(u);
    }

    private AcademicSemester semester(String year, int number, LocalDate start, LocalDate end) {
        AcademicSemester s = new AcademicSemester();
        s.setAcademicYear(year);
        s.setNumber(number);
        s.setStartsOn(start);
        s.setEndsOn(end);
        return semesters.save(s);
    }

    private Cohort cohort(Programme p, String name, AcademicSemester intake) {
        Cohort c = new Cohort();
        c.setProgramme(p);
        c.setName(name);
        c.setIntakeSemester(intake);
        return cohorts.save(c);
    }

    private Student student(String email, String number, String first, String last, Programme p, Cohort cohort,
                            LocalDate enrolled, AppUser supervisor) {
        AppUser u = user(email, number, first, last, RoleCode.STUDENT);
        Student s = new Student();
        s.setUser(u);
        s.setProgramme(p);
        s.setCohort(cohort);
        s.setEnrolledOn(enrolled);
        DueDates due = DueDates.compute(p, enrolled, 0);
        s.setCheckpointDueOn(due.checkpointDue());
        s.setProgrammeEndOn(due.programmeEnd());
        s.setCurrentSemester(DueDates.semesterOn(p, enrolled, Times.today(clock)));
        s.setStatus(StudentStatus.ACTIVE);
        s = students.save(s);
        Supervision sv = new Supervision();
        sv.setStudent(s);
        sv.setSupervisor(supervisor);
        sv.setType(Supervision.Type.PRIMARY);
        sv.setStartsOn(enrolled);
        supervisions.save(sv);
        for (MilestoneTemplate t : templates.findByProgrammeIdOrderBySequenceNo(p.getId())) {
            if (t.getSequenceNo() > p.getRequiredMilestones()) continue;
            StudentMilestone m = new StudentMilestone();
            m.setStudent(s);
            m.setTemplate(t);
            m.setSequenceNo(t.getSequenceNo());
            m.setTitle(t.getTitle());
            m.setFinalPreDefense(t.isFinalPreDefense());
            m.setEvidenceRequired(t.isEvidenceRequired() || p.isEvidenceRequired());
            m.setStatus(MilestoneStatus.LOCKED);
            milestones.save(m);
        }
        milestones.flush();
        return s;
    }

    private void openFirst(Student s) {
        StudentMilestone m = milestones.findByStudentIdAndSequenceNo(s.getId(), 1).orElseThrow();
        m.setStatus(MilestoneStatus.AVAILABLE);
        m.setAvailableAt(clock.instant().minusSeconds(86400L * 60));
    }

    /** Approves milestones 1..count with past attempts spread across the enrolment period; opens the next one. */
    private void approve(Student s, int count, AppUser admin, Venue venue, AppUser panel) {
        LocalDate start = s.getEnrolledOn().plusMonths(6);
        long span = Math.max(30, java.time.temporal.ChronoUnit.DAYS.between(start, Times.today(clock).minusDays(20)));
        for (int i = 1; i <= count; i++) {
            StudentMilestone m = milestones.findByStudentIdAndSequenceNo(s.getId(), i).orElseThrow();
            LocalDate day = start.plusDays(span * (i - 1) / Math.max(1, count));
            PresentationAttempt a = attempt(m, 1, venue, List.of(panel), day.atTime(10, 0));
            a.setOutcome(PresentationAttempt.Outcome.PASSED);
            a.setScore(BigDecimal.valueOf(68 + i * 3));
            a.setPanelRemarks("Clear presentation; address the panel's minor comments in the next chapter.");
            a.setRecordedAt(a.getScheduledEnd());
            m.setStatus(MilestoneStatus.APPROVED);
            m.setAvailableAt(a.getScheduledStart().minusSeconds(86400L * 20));
            m.setApprovedBy(admin.getId());
            m.setApprovedAt(a.getScheduledEnd().plusSeconds(86400L * 3));
        }
        milestones.findByStudentIdAndSequenceNo(s.getId(), count + 1).ifPresent(next -> {
            next.setStatus(MilestoneStatus.AVAILABLE);
            next.setAvailableAt(clock.instant().minusSeconds(86400L * 14));
        });
    }

    private void passedAwaiting(Student s, int seq, Venue venue, AppUser panel, LocalDate day) {
        StudentMilestone m = milestones.findByStudentIdAndSequenceNo(s.getId(), seq).orElseThrow();
        PresentationAttempt a = attempt(m, 1, venue, List.of(panel), day.atTime(11, 0));
        a.setOutcome(PresentationAttempt.Outcome.PASSED);
        a.setScore(BigDecimal.valueOf(74));
        a.setPanelRemarks("Good progress on data collection.");
        a.setRecordedAt(a.getScheduledEnd());
        m.setStatus(MilestoneStatus.AWAITING_VERIFICATION);
    }

    private void failed(Student s, int seq, Venue venue, AppUser panel, LocalDate day, String remarks) {
        StudentMilestone m = milestones.findByStudentIdAndSequenceNo(s.getId(), seq).orElseThrow();
        PresentationAttempt a = attempt(m, 1, venue, List.of(panel), day.atTime(14, 0));
        a.setOutcome(PresentationAttempt.Outcome.FAILED);
        a.setPanelRemarks(remarks);
        a.setRecordedAt(a.getScheduledEnd());
        m.setStatus(MilestoneStatus.AVAILABLE);
    }

    private void scheduled(Student s, int seq, Venue venue, List<AppUser> panel, java.time.LocalDateTime at) {
        StudentMilestone m = milestones.findByStudentIdAndSequenceNo(s.getId(), seq).orElseThrow();
        attempt(m, 1, venue, panel, at);
        m.setStatus(MilestoneStatus.SCHEDULED);
    }

    private PresentationAttempt attempt(StudentMilestone m, int no, Venue venue, List<AppUser> panel, java.time.LocalDateTime at) {
        Instant start = ZonedDateTime.of(at, Times.ZONE).toInstant();
        PresentationAttempt a = new PresentationAttempt();
        a.setMilestone(m);
        a.setAttemptNo(no);
        a.setScheduledStart(start);
        a.setScheduledEnd(start.plusSeconds(45 * 60));
        a.setVenue(venue);
        boolean chair = true;
        for (AppUser u : panel) {
            PanelMember pm = new PanelMember();
            pm.setAttempt(a);
            pm.setUserId(u.getId());
            pm.setRole(chair ? PanelMember.Role.CHAIR : PanelMember.Role.MEMBER);
            chair = false;
            a.getPanel().add(pm);
        }
        PanelMember external = new PanelMember();
        external.setAttempt(a);
        external.setExternalName("Prof. A. Mensah (external)");
        external.setRole(PanelMember.Role.MEMBER);
        a.getPanel().add(external);
        return attempts.save(a);
    }

    private void publication(Student s, String title, Publication.Type type, String venue, String doi,
                             Publication.ReviewStatus status, LocalDate submitted, AcademicSemester current) {
        Publication p = new Publication();
        p.setStudent(s);
        p.setTitle(title);
        p.setType(type);
        p.setVenueName(venue);
        p.setAuthors(s.getUser().getFullName() + ", Y. Owusu");
        p.setYear(current.getStartsOn().getYear());
        p.setDoi(doi);
        p.setPubStatus(Publication.PubStatus.PUBLISHED);
        p.setReviewStatus(status);
        if (submitted != null) p.setSubmittedAt(ZonedDateTime.of(submitted.atTime(9, 0), Times.ZONE).toInstant());
        if (status == Publication.ReviewStatus.APPROVED) {
            p.setReviewedAt(p.getSubmittedAt().plusSeconds(86400L * 5));
            p.setReviewedBy(users.findByEmailIgnoreCase("librarian@grps.local").orElseThrow().getId());
            p.setVenueVerified(true);
            p.setAuthorshipVerified(true);
            p.setIndexingChecked(true);
        }
        publications.save(p);
    }

    private Course course(String code, String title, AcademicSemester semester, Set<AppUser> lecturers, Set<Student> enrolled) {
        Course c = new Course();
        c.setCode(code);
        c.setTitle(title);
        c.setSemester(semester);
        c.setCreditHours(3);
        c.getLecturers().addAll(lecturers);
        c.getStudents().addAll(enrolled);
        return courses.save(c);
    }

    /** Weekly past sessions; the named student attends {@code attended} of them, others miss one or two. */
    private void attendanceHistory(Course c, AcademicSemester sem, LocalDate today, int count, Student weak, int attended) {
        LocalDate first = today.minusWeeks(count);
        if (first.isBefore(sem.getStartsOn())) first = sem.getStartsOn();
        for (int i = 0; i < count; i++) {
            LocalDate day = first.plusWeeks(i);
            if (!day.isBefore(today)) break;
            ClassSession s = new ClassSession();
            s.setCourse(c);
            s.setHeldOn(day);
            s.setStartsAt(LocalTime.of(9, 0));
            s.setEndsAt(LocalTime.of(11, 0));
            s.setTopic("Week " + (i + 1));
            s.setAttendanceTakenAt(ZonedDateTime.of(day.atTime(11, 5), Times.ZONE).toInstant());
            s = sessions.save(s);
            int idx = 0;
            for (Student st : c.getStudents()) {
                AttendanceRecord r = new AttendanceRecord();
                r.setSession(s);
                r.setStudent(st);
                AttendanceRecord.Status status;
                if (st.getId().equals(weak.getId())) status = i < attended ? AttendanceRecord.Status.PRESENT : AttendanceRecord.Status.ABSENT;
                else if ((i + idx) % 7 == 3) status = AttendanceRecord.Status.LATE;
                else if ((i + idx) % 9 == 5) status = AttendanceRecord.Status.ABSENT;
                else status = AttendanceRecord.Status.PRESENT;
                r.setStatus(status);
                r.setMarkedAt(s.getAttendanceTakenAt());
                records.save(r);
                idx++;
            }
        }
    }
}
