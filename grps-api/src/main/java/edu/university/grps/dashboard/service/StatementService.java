package edu.university.grps.dashboard.service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import edu.university.grps.audit.AuditService;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.GrpsProperties;
import edu.university.grps.common.Times;
import edu.university.grps.dashboard.domain.ProgressStatement;
import edu.university.grps.dashboard.repository.ProgressStatementRepository;
import edu.university.grps.dashboard.service.DashboardService.AchievementDto;
import edu.university.grps.student.api.StudentDtos.StudentSummaryDto;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.repository.StudentRepositories.StudentRepository;
import edu.university.grps.student.service.StudentAccess;
import edu.university.grps.student.service.StudentService;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-ACH-03: progress statement PDF with all approved items, the generated date and a verification code. */
@Service
public class StatementService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StudentAccess access;
    private final StudentService students;
    private final StudentRepository studentRepository;
    private final DashboardService dashboards;
    private final ProgressStatementRepository statements;
    private final AuditService audit;
    private final GrpsProperties props;
    private final Clock clock;

    public StatementService(StudentAccess access, StudentService students, StudentRepository studentRepository,
                            DashboardService dashboards, ProgressStatementRepository statements, AuditService audit,
                            GrpsProperties props, Clock clock) {
        this.access = access;
        this.students = students;
        this.studentRepository = studentRepository;
        this.dashboards = dashboards;
        this.statements = statements;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    public record Statement(byte[] pdf, String code) {}

    public record VerificationDto(boolean valid, String studentName, String studentNumber, String programme,
                                  Instant generatedAt, int approvedItems) {}

    @Transactional
    public Statement generate() {
        Student s = access.currentStudent();
        StudentSummaryDto summary = students.summary(s);
        List<AchievementDto> items = dashboards.achievementsFor(s);
        String code = newCode();
        ProgressStatement record = new ProgressStatement();
        record.setStudentId(s.getId());
        record.setVerificationCode(code);
        record.setApprovedItems(items.size());
        statements.save(record);
        audit.record("STATEMENT_GENERATED", "ProgressStatement", record.getId(), s.getId(), null, Map.of("code", code, "items", items.size()));
        return new Statement(render(summary, items, code), code);
    }

    @Transactional(readOnly = true)
    public VerificationDto verify(String code) {
        ProgressStatement st = statements.findByVerificationCode(code == null ? "" : code.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Statement"));
        Student s = studentRepository.findWithDetailsById(st.getStudentId()).orElseThrow();
        return new VerificationDto(true, s.getUser().getFullName(), s.getUser().getStaffOrStudentNo(), s.getProgramme().getName(),
                st.getCreatedAt(), st.getApprovedItems());
    }

    private byte[] render(StudentSummaryDto s, List<AchievementDto> items, String code) {
        Document doc = new Document(PageSize.A4, 48, 48, 54, 54);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, out);
        doc.open();
        Color ink = new Color(0x1b, 0x1f, 0x23);
        Color muted = new Color(0x5b, 0x65, 0x70);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, muted);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 10, ink);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, ink);
        doc.add(new Paragraph("Graduate Research Progress System", small));
        Paragraph title = new Paragraph("Statement of research progress", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17, ink));
        title.setSpacingAfter(10);
        doc.add(title);
        PdfPTable facts = new PdfPTable(new float[] {1.3f, 3f});
        facts.setWidthPercentage(100);
        addFact(facts, "Student", s.name() + " (" + s.number() + ")", bold, body);
        addFact(facts, "Programme", s.programmeName() + ", cohort " + s.cohortName(), bold, body);
        addFact(facts, "Enrolled", Times.formatDate(s.enrolledOn()), bold, body);
        addFact(facts, "Progress", s.approvedMilestones() + " of " + s.requiredMilestones() + " presentations approved", bold, body);
        addFact(facts, "Status", DashboardService.statusLabel(edu.university.grps.student.domain.StudentStatus.valueOf(s.status())), bold, body);
        addFact(facts, "Supervisor", s.supervisorName() == null ? "" : s.supervisorName(), bold, body);
        doc.add(facts);
        Paragraph h = new Paragraph("Approved items", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, ink));
        h.setSpacingBefore(14);
        h.setSpacingAfter(6);
        doc.add(h);
        PdfPTable table = new PdfPTable(new float[] {1.2f, 3.6f, 1.6f});
        table.setWidthPercentage(100);
        for (String head : List.of("Date", "Item", "Approved by")) {
            PdfPCell c = new PdfPCell(new Phrase(head, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
            c.setBackgroundColor(new Color(0x1f, 0x4e, 0x79));
            c.setBorder(Rectangle.NO_BORDER);
            c.setPadding(5);
            table.addCell(c);
        }
        for (AchievementDto a : items) {
            table.addCell(cell(a.at() == null ? "" : Times.formatDate(Times.local(a.at()).toLocalDate()), body));
            table.addCell(cell(a.title() + "\n" + a.detail(), body));
            table.addCell(cell(a.approvedBy() == null ? "" : a.approvedBy(), body));
        }
        if (items.isEmpty()) {
            PdfPCell c = cell("No approved items yet.", body);
            c.setColspan(3);
            table.addCell(c);
        }
        doc.add(table);
        Paragraph foot = new Paragraph("Generated " + Times.formatDateTime(clock.instant()) + " (Africa/Accra). Verification code: "
                + code + ". Verify at " + props.frontendUrl() + "/verify?code=" + code, small);
        foot.setSpacingBefore(18);
        doc.add(foot);
        doc.close();
        return out.toByteArray();
    }

    private static void addFact(PdfPTable t, String label, String value, Font bold, Font body) {
        PdfPCell l = new PdfPCell(new Phrase(label, bold));
        PdfPCell v = new PdfPCell(new Phrase(value, body));
        for (PdfPCell c : List.of(l, v)) {
            c.setBorder(Rectangle.NO_BORDER);
            c.setPaddingBottom(4);
            t.addCell(c);
        }
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(new Color(0xe3, 0xe7, 0xeb));
        c.setPadding(5);
        return c;
    }

    private static String newCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i == 5) sb.append('-');
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
