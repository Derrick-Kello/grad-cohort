package edu.university.grps.support;

import edu.university.grps.common.BaseEntity;
import edu.university.grps.milestone.domain.MilestoneStatus;
import edu.university.grps.milestone.domain.StudentMilestone;
import edu.university.grps.programme.domain.Cohort;
import edu.university.grps.programme.domain.Programme;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.domain.StudentStatus;
import edu.university.grps.user.domain.AppUser;
import edu.university.grps.user.domain.Role;
import edu.university.grps.user.domain.RoleCode;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Builders for entities and the security context in unit tests. */
public final class Fixtures {

    private Fixtures() {}

    public static <T extends BaseEntity> T withId(T entity) {
        try {
            Field f = BaseEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, UUID.randomUUID());
            Field v = BaseEntity.class.getDeclaredField("version");
            v.setAccessible(true);
            v.set(entity, 0L);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Programme masters() {
        Programme p = withId(new Programme());
        p.setCode("MASTERS");
        p.setName("Masters by Research");
        p.setLevel(Programme.Level.MASTERS);
        p.setDurationMonths(24);
        p.setSemesters(4);
        p.setRequiredMilestones(5);
        p.setCheckpointMonths(18);
        p.setCheckpointMilestones(4);
        p.setEvidenceRequired(true);
        p.setFirstMilestoneSemester(2);
        return p;
    }

    public static AppUser user(String first, RoleCode... roles) {
        AppUser u = withId(new AppUser());
        u.setFirstName(first);
        u.setLastName("Test");
        u.setEmail(first.toLowerCase() + "@test.local");
        u.setStaffOrStudentNo(first.toUpperCase() + "-1");
        u.setActive(true);
        for (RoleCode code : roles) {
            Role r = withId(new Role());
            r.setCode(code);
            u.getRoles().add(r);
        }
        return u;
    }

    public static Student student(StudentStatus status) {
        Student s = withId(new Student());
        s.setUser(user("Ama", RoleCode.STUDENT));
        s.setProgramme(masters());
        Cohort c = withId(new Cohort());
        c.setName("2025 Intake");
        c.setProgramme(s.getProgramme());
        s.setCohort(c);
        s.setEnrolledOn(LocalDate.of(2025, 9, 1));
        s.setCheckpointDueOn(LocalDate.of(2027, 3, 1));
        s.setProgrammeEndOn(LocalDate.of(2027, 9, 1));
        s.setStatus(status);
        return s;
    }

    public static StudentMilestone milestone(Student s, int seq, MilestoneStatus status) {
        StudentMilestone m = withId(new StudentMilestone());
        m.setStudent(s);
        m.setSequenceNo(seq);
        m.setTitle("Presentation title " + seq);
        m.setFinalPreDefense(seq == 5);
        m.setEvidenceRequired(true);
        m.setStatus(status);
        return m;
    }

    public static void signIn(UUID userId, RoleCode... roles) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(userId.toString())
                .claim("roles", List.of(roles).stream().map(Enum::name).toList()).build();
        var authorities = List.of(roles).stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }

    public static void signOut() {
        SecurityContextHolder.clearContext();
    }
}
