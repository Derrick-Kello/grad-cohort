package edu.university.grps.student.service;

import edu.university.grps.audit.AuditService;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.Csv;
import edu.university.grps.programme.domain.Cohort;
import edu.university.grps.programme.domain.Programme;
import edu.university.grps.programme.repository.ProgrammeRepositories.CohortRepository;
import edu.university.grps.programme.repository.ProgrammeRepositories.ProgrammeRepository;
import edu.university.grps.security.CurrentUser;
import edu.university.grps.user.domain.AppUser;
import edu.university.grps.user.domain.RoleCode;
import edu.university.grps.user.repository.AppUserRepository;
import edu.university.grps.user.service.UserService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * FR-USR-02: bulk import of staff and students from CSV. Every row is validated first and errors are
 * reported per row; nothing is saved if any row fails.
 *
 * Columns: type, email, number, first_name, last_name, phone, roles, programme_code, cohort, enrolled_on, supervisor.
 * STAFF rows list roles separated by semicolons. STUDENT rows need programme_code, cohort, enrolled_on and a
 * supervisor (email or staff number of an existing supervisor, or of a STAFF row in the same file).
 */
@Service
public class ImportService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final List<String> REQUIRED = List.of("type", "email", "number", "first_name", "last_name");
    private static final int MAX_ROWS = 5000;

    private final AppUserRepository users;
    private final ProgrammeRepository programmes;
    private final CohortRepository cohorts;
    private final UserService userService;
    private final StudentService studentService;
    private final AuditService audit;

    public ImportService(AppUserRepository users, ProgrammeRepository programmes, CohortRepository cohorts,
                         UserService userService, StudentService studentService, AuditService audit) {
        this.users = users;
        this.programmes = programmes;
        this.cohorts = cohorts;
        this.userService = userService;
        this.studentService = studentService;
        this.audit = audit;
    }

    public record RowError(int row, String column, String message) {}

    public record ImportResult(boolean valid, boolean saved, int rows, int staff, int students, List<RowError> errors) {}

    private record Row(int line, Map<String, String> v) {
        String get(String key) {
            return v.getOrDefault(key, "").trim();
        }
    }

    @Transactional
    public ImportResult importCsv(MultipartFile file, boolean validateOnly) {
        String text;
        try {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw ApiException.validation("The file could not be read.");
        }
        List<List<String>> table = Csv.parse(text);
        if (table.isEmpty()) throw ApiException.validation("The file is empty.");
        List<String> header = table.get(0).stream().map(h -> h.trim().toLowerCase(Locale.ROOT).replace(' ', '_')).toList();
        for (String col : REQUIRED) {
            if (!header.contains(col)) throw ApiException.validation("Missing column \"" + col + "\". Required: " + String.join(", ", REQUIRED) + ".");
        }
        if (table.size() - 1 > MAX_ROWS) throw ApiException.validation("Import at most " + MAX_ROWS + " rows at a time.");

        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < table.size(); i++) {
            Map<String, String> values = new HashMap<>();
            List<String> cells = table.get(i);
            for (int c = 0; c < header.size(); c++) values.put(header.get(c), c < cells.size() ? cells.get(c) : "");
            rows.add(new Row(i + 1, values));
        }

        List<RowError> errors = new ArrayList<>();
        Set<String> emails = new HashSet<>();
        Set<String> numbers = new HashSet<>();
        Map<String, Set<RoleCode>> fileSupervisors = new HashMap<>();
        int staffCount = 0;
        int studentCount = 0;

        for (Row r : rows) {
            String type = r.get("type").toUpperCase(Locale.ROOT);
            if (!type.equals("STAFF") && !type.equals("STUDENT")) {
                errors.add(new RowError(r.line(), "type", "Type must be STAFF or STUDENT."));
                continue;
            }
            String email = r.get("email").toLowerCase(Locale.ROOT);
            String number = r.get("number");
            if (!EMAIL.matcher(email).matches()) errors.add(new RowError(r.line(), "email", "Invalid email address."));
            else if (!emails.add(email)) errors.add(new RowError(r.line(), "email", "Email appears more than once in the file."));
            else if (users.existsByEmailIgnoreCase(email)) errors.add(new RowError(r.line(), "email", "An account with this email already exists."));
            if (number.isEmpty()) errors.add(new RowError(r.line(), "number", "Staff or student number is required."));
            else if (!numbers.add(number.toLowerCase(Locale.ROOT))) errors.add(new RowError(r.line(), "number", "Number appears more than once in the file."));
            else if (users.existsByStaffOrStudentNoIgnoreCase(number)) errors.add(new RowError(r.line(), "number", "An account with this number already exists."));
            if (r.get("first_name").isEmpty()) errors.add(new RowError(r.line(), "first_name", "First name is required."));
            if (r.get("last_name").isEmpty()) errors.add(new RowError(r.line(), "last_name", "Last name is required."));

            if (type.equals("STAFF")) {
                staffCount++;
                Set<RoleCode> roleSet = parseRoles(r, errors);
                if (roleSet != null) {
                    fileSupervisors.put(email, roleSet);
                    fileSupervisors.put(number.toLowerCase(Locale.ROOT), roleSet);
                }
            } else {
                studentCount++;
            }
        }

        for (Row r : rows) {
            if (!r.get("type").equalsIgnoreCase("STUDENT")) continue;
            Programme programme = programmes.findByCodeIgnoreCase(r.get("programme_code")).orElse(null);
            if (programme == null) errors.add(new RowError(r.line(), "programme_code", "Unknown programme \"" + r.get("programme_code") + "\"."));
            else if (cohorts.findByProgrammeIdAndNameIgnoreCase(programme.getId(), r.get("cohort")).isEmpty()) {
                errors.add(new RowError(r.line(), "cohort", "Cohort \"" + r.get("cohort") + "\" does not exist for " + programme.getCode() + "."));
            }
            try {
                LocalDate.parse(r.get("enrolled_on"));
            } catch (DateTimeParseException e) {
                errors.add(new RowError(r.line(), "enrolled_on", "Use the date format YYYY-MM-DD."));
            }
            String sup = r.get("supervisor").toLowerCase(Locale.ROOT);
            if (sup.isEmpty()) {
                errors.add(new RowError(r.line(), "supervisor", "Every student needs a supervisor."));
            } else if (fileSupervisors.containsKey(sup)) {
                Set<RoleCode> roleSet = fileSupervisors.get(sup);
                if (!roleSet.contains(RoleCode.SUPERVISOR) || !roleSet.contains(RoleCode.LECTURER)) {
                    errors.add(new RowError(r.line(), "supervisor", "The supervisor's row needs both LECTURER and SUPERVISOR roles."));
                }
            } else {
                Optional<AppUser> existing = users.findByLogin(sup);
                if (existing.isEmpty()) errors.add(new RowError(r.line(), "supervisor", "No supervisor found for \"" + sup + "\"."));
                else if (!existing.get().isActive() || !existing.get().hasRole(RoleCode.LECTURER) || !existing.get().hasRole(RoleCode.SUPERVISOR)) {
                    errors.add(new RowError(r.line(), "supervisor", existing.get().getFullName() + " does not hold the Lecturer and Supervisor roles."));
                }
            }
        }

        if (!errors.isEmpty() || validateOnly) {
            return new ImportResult(errors.isEmpty(), false, rows.size(), staffCount, studentCount, errors);
        }

        for (Row r : rows) {
            if (!r.get("type").equalsIgnoreCase("STAFF")) continue;
            userService.create(new UserService.UserInput(r.get("email"), r.get("number"), r.get("first_name"),
                    r.get("last_name"), r.get("phone"), parseRoles(r, new ArrayList<>()), null));
        }
        users.flush();
        for (Row r : rows) {
            if (!r.get("type").equalsIgnoreCase("STUDENT")) continue;
            Programme programme = programmes.findByCodeIgnoreCase(r.get("programme_code")).orElseThrow();
            Cohort cohort = cohorts.findByProgrammeIdAndNameIgnoreCase(programme.getId(), r.get("cohort")).orElseThrow();
            UUID supervisorId = users.findByLogin(r.get("supervisor")).orElseThrow().getId();
            studentService.enrol(new StudentService.EnrolInput(null, r.get("email"), r.get("number"), r.get("first_name"),
                    r.get("last_name"), r.get("phone"), programme.getId(), cohort.getId(), LocalDate.parse(r.get("enrolled_on")),
                    supervisorId, null));
        }
        audit.record("CSV_IMPORT", "AppUser", null, Map.of("rows", rows.size(), "staff", staffCount, "students", studentCount,
                "by", String.valueOf(CurrentUser.optionalId().orElse(null))));
        return new ImportResult(true, true, rows.size(), staffCount, studentCount, List.of());
    }

    private static Set<RoleCode> parseRoles(Row r, List<RowError> errors) {
        String raw = r.get("roles");
        if (raw.isEmpty()) {
            errors.add(new RowError(r.line(), "roles", "Staff rows need at least one role."));
            return null;
        }
        Set<RoleCode> out = EnumSet.noneOf(RoleCode.class);
        for (String part : raw.split("[;|]")) {
            String code = part.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            if (code.isEmpty()) continue;
            try {
                out.add(RoleCode.valueOf(code));
            } catch (IllegalArgumentException e) {
                errors.add(new RowError(r.line(), "roles", "Unknown role \"" + part.trim() + "\"."));
                return null;
            }
        }
        if (out.contains(RoleCode.STUDENT)) {
            errors.add(new RowError(r.line(), "roles", "Use a STUDENT row to import students."));
            return null;
        }
        if (out.contains(RoleCode.SUPERVISOR) && !out.contains(RoleCode.LECTURER)) {
            errors.add(new RowError(r.line(), "roles", "SUPERVISOR requires LECTURER."));
            return null;
        }
        if ((out.contains(RoleCode.ADMIN) || out.contains(RoleCode.SUPER_ADMIN)) && !CurrentUser.hasRole(RoleCode.SUPER_ADMIN)) {
            errors.add(new RowError(r.line(), "roles", "Only a Super Admin can import admin accounts."));
            return null;
        }
        return out;
    }
}
