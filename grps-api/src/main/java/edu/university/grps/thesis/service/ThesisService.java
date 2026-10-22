package edu.university.grps.thesis.service;

import edu.university.grps.audit.AuditService;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.ErrorCode;
import edu.university.grps.milestone.domain.MilestoneStatus;
import edu.university.grps.milestone.domain.StudentMilestone;
import edu.university.grps.milestone.repository.MilestoneRepositories.StudentMilestoneRepository;
import edu.university.grps.notification.domain.NotificationCode;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.publication.service.PublicationService;
import edu.university.grps.security.CurrentUser;
import edu.university.grps.settings.service.SettingsService;
import edu.university.grps.storage.FileStore;
import edu.university.grps.storage.FileTypes;
import edu.university.grps.storage.SignedLinks;
import edu.university.grps.storage.UploadChecks;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.domain.StudentStatus;
import edu.university.grps.student.service.StudentAccess;
import edu.university.grps.thesis.domain.FormatCheckResult;
import edu.university.grps.thesis.domain.ThesisSubmission;
import edu.university.grps.thesis.domain.ThesisSubmission.Status;
import edu.university.grps.thesis.repository.ThesisRepositories.FormatCheckResultRepository;
import edu.university.grps.thesis.repository.ThesisRepositories.ThesisSubmissionRepository;
import edu.university.grps.user.service.UserDirectory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Thesis submission (section 11). Every version passes two automatic gates in order, format then
 * similarity / AI screening, before anyone else acts on it.
 */
@Service
@Transactional
public class ThesisService {

    public record ThesisUploadedEvent(UUID thesisId) {}

    public record ThesisDto(UUID id, UUID studentId, String studentName, String studentNumber, String programmeCode,
                            int versionNo, String title, String abstractText, String keywords, String fileName,
                            String fileType, long sizeBytes, String status, Integer pageCount, BigDecimal similarityPct,
                            BigDecimal aiPct, BigDecimal similarityLimit, BigDecimal aiLimit, boolean screeningManual,
                            boolean hasProviderReport, String providerReportUrl, String overrideByName,
                            String overrideReason, Instant overrideAt, Instant submittedAt, Instant updatedAt,
                            long formatFailures, long formatSkipped) {}

    public record ThesisOverviewDto(boolean locked, String lockedReason, boolean canSubmit, String blockedReason,
                                    BigDecimal similarityLimit, BigDecimal aiLimit, long maxMegabytes,
                                    List<ThesisDto> versions) {}

    public record FormatResultDto(String ruleCode, boolean passed, boolean skipped, String detail, String location) {}

    public record FormatReportDto(UUID thesisId, int versionNo, String status, String fileType, Integer pageCount,
                                  List<FormatResultDto> results) {}

    private final ThesisSubmissionRepository theses;
    private final FormatCheckResultRepository results;
    private final StudentMilestoneRepository milestones;
    private final PublicationService publications;
    private final StudentAccess access;
    private final UploadChecks uploads;
    private final FileStore store;
    private final SignedLinks links;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final UserDirectory directory;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ThesisService(ThesisSubmissionRepository theses, FormatCheckResultRepository results,
                         StudentMilestoneRepository milestones, PublicationService publications, StudentAccess access,
                         UploadChecks uploads, FileStore store, SignedLinks links, SettingsService settings,
                         NotificationService notifications, UserDirectory directory, AuditService audit,
                         ApplicationEventPublisher events, Clock clock) {
        this.theses = theses;
        this.results = results;
        this.milestones = milestones;
        this.publications = publications;
        this.access = access;
        this.uploads = uploads;
        this.store = store;
        this.links = links;
        this.settings = settings;
        this.notifications = notifications;
        this.directory = directory;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    // Student --------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ThesisOverviewDto overview() {
        Student s = access.currentStudent();
        return overviewFor(s);
    }

    @Transactional(readOnly = true)
    public ThesisOverviewDto overviewFor(UUID studentId) {
        return overviewFor(access.requireViewable(studentId));
    }

    private ThesisOverviewDto overviewFor(Student s) {
        String lockedReason = lockReason(s);
        List<ThesisSubmission> versions = theses.findByStudentIdOrderByVersionNoDesc(s.getId());
        String blocked = null;
        if (lockedReason == null) {
            if (s.isClosed()) blocked = "Your record is " + s.getStatus().name().toLowerCase().replace('_', ' ') + ".";
            else if (!versions.isEmpty() && !versions.get(0).allowsResubmission()) {
                blocked = versions.get(0).getStatus() == Status.ACCEPTED_FOR_EXAMINATION
                        ? "Your thesis has been accepted for examination."
                        : "Version " + versions.get(0).getVersionNo() + " is still being checked.";
            } else {
                String pubs = publicationShortfall(s);
                if (pubs != null) blocked = pubs;
            }
        }
        return new ThesisOverviewDto(lockedReason != null, lockedReason, lockedReason == null && blocked == null, blocked,
                settings.decimal(SettingsService.SIMILARITY_LIMIT), settings.decimal(SettingsService.AI_LIMIT),
                settings.integer(SettingsService.THESIS_MAX_MB), versions.stream().map(t -> toDto(t, s)).toList());
    }

    /** BR-09: thesis submission requires the final pre-defense to be APPROVED. */
    private String lockReason(Student s) {
        StudentMilestone fin = milestones.findByStudentIdOrderBySequenceNo(s.getId()).stream()
                .filter(StudentMilestone::isFinalPreDefense).findFirst().orElse(null);
        if (fin == null) return "No final pre-defense milestone is set up for this programme.";
        if (fin.getStatus() != MilestoneStatus.APPROVED) {
            return "Thesis submission opens once " + fin.label() + " (final pre-defense) is approved.";
        }
        return null;
    }

    private String publicationShortfall(Student s) {
        int required = s.getProgramme().getRequiredPublications();
        if (required <= 0) return null;
        long approved = publications.approvedCount(s.getId());
        return approved >= required ? null
                : "Your programme needs " + required + " library-approved publication(s) before thesis submission; you have " + approved + ".";
    }

    /** FR-THS-02: DOCX or PDF up to 50 MB; title, abstract and keywords required; each upload is a new version. */
    public ThesisDto submit(MultipartFile file, String title, String abstractText, String keywords) {
        Student s = access.currentStudent();
        String locked = lockReason(s);
        if (locked != null) throw new ApiException(ErrorCode.THESIS_LOCKED, locked);
        access.requireWritable(s);
        String pubs = publicationShortfall(s);
        if (pubs != null) throw ApiException.rule(pubs);
        ThesisSubmission latest = theses.findFirstByStudentIdOrderByVersionNoDesc(s.getId()).orElse(null);
        if (latest != null && !latest.allowsResubmission()) {
            throw ApiException.invalidState(latest.getStatus() == Status.ACCEPTED_FOR_EXAMINATION
                    ? "Your thesis is already accepted for examination."
                    : "Version " + latest.getVersionNo() + " is still being checked. Wait for the result before uploading again.");
        }
        if (blank(title) || blank(abstractText) || blank(keywords)) throw ApiException.validation("Title, abstract and keywords are required.");
        UploadChecks.Checked checked = uploads.check(file, EnumSet.of(FileTypes.DOCX, FileTypes.PDF),
                settings.megabytes(SettingsService.THESIS_MAX_MB));
        if (checked.pendingScan()) throw ApiException.invalidState("Virus scanning is unavailable right now. Try again shortly.");
        int version = theses.maxVersion(s.getId()) + 1;
        String key = "theses/" + s.getId() + "/v" + version + "-" + UUID.randomUUID() + "." + checked.type().extension();
        store.put(key, checked.bytes(), checked.type().mime());
        ThesisSubmission t = new ThesisSubmission();
        t.setStudent(s);
        t.setVersionNo(version);
        t.setTitle(title.trim());
        t.setAbstractText(abstractText.trim());
        t.setKeywords(keywords.trim());
        t.setStorageKey(key);
        t.setFileName(checked.fileName());
        t.setFileType(checked.type().name());
        t.setSizeBytes(checked.bytes().length);
        t.setStatus(Status.UPLOADED);
        t = theses.save(t);
        audit.record("THESIS_UPLOADED", "ThesisSubmission", t.getId(), s.getId(), null,
                Map.of("version", version, "fileName", t.getFileName(), "fileType", t.getFileType(), "sha256", checked.sha256()));
        events.publishEvent(new ThesisUploadedEvent(t.getId()));
        return toDto(t, s);
    }

    // Viewers ----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ThesisDto get(UUID id) {
        ThesisSubmission t = viewable(id);
        return toDto(t, t.getStudent());
    }

    @Transactional(readOnly = true)
    public FormatReportDto formatReport(UUID id) {
        ThesisSubmission t = viewable(id);
        List<FormatResultDto> list = results.findByThesis(id).stream()
                .sorted((a, b) -> Integer.compare(order(a.getRuleCode()), order(b.getRuleCode())))
                .map(r -> new FormatResultDto(r.getRuleCode(), r.isPassed(), r.isSkipped(), r.getDetail(), r.getLocation())).toList();
        return new FormatReportDto(t.getId(), t.getVersionNo(), t.getStatus().name(), t.getFileType(), t.getPageCount(), list);
    }

    private static int order(String code) {
        int i = edu.university.grps.thesis.formatcheck.FormatChecker.RULE_ORDER.indexOf(code);
        return i < 0 ? 99 : i;
    }

    @Transactional(readOnly = true)
    public SignedLinks.Link fileLink(UUID id) {
        ThesisSubmission t = viewable(id);
        return links.create(t.getStorageKey(), t.getFileName(), FileTypes.valueOf(t.getFileType()).mime(), false);
    }

    @Transactional(readOnly = true)
    public SignedLinks.Link providerReportLink(UUID id) {
        ThesisSubmission t = viewable(id);
        if (t.getProviderReportKey() == null) throw ApiException.notFound("Provider report");
        return links.create(t.getProviderReportKey(), "screening-report-v" + t.getVersionNo() + ".pdf", FileTypes.PDF.mime(), true);
    }

    private ThesisSubmission viewable(UUID id) {
        ThesisSubmission t = theses.findWithStudentById(id).orElseThrow(() -> ApiException.notFound("Thesis"));
        if (!access.canView(t.getStudent())) throw ApiException.notFound("Thesis");
        return t;
    }

    @Transactional(readOnly = true)
    public List<ThesisDto> adminList(Status status) {
        return theses.findAllWithStudents().stream()
                .filter(t -> status == null || t.getStatus() == status)
                .map(t -> toDto(t, t.getStudent())).toList();
    }

    // Admin actions ------------------------------------------------------------------

    /** FR-THS-09: used when the provider API is unavailable; the same limits apply. */
    public ThesisDto manualScreening(UUID id, BigDecimal similarity, BigDecimal ai, MultipartFile providerReport) {
        ThesisSubmission t = theses.findWithStudentById(id).orElseThrow(() -> ApiException.notFound("Thesis"));
        if (t.getStatus() != Status.SCREENING && t.getStatus() != Status.FORMAT_PASSED) {
            throw ApiException.invalidState("Manual screening applies only to a thesis that passed the format check and is awaiting screening.");
        }
        if (similarity == null || ai == null) throw ApiException.validation("Enter both the similarity index and the AI score.");
        checkPct(similarity);
        checkPct(ai);
        if (providerReport == null || providerReport.isEmpty()) throw ApiException.validation("Upload the provider's PDF report.");
        UploadChecks.Checked checked = uploads.check(providerReport, EnumSet.of(FileTypes.PDF), settings.megabytes(SettingsService.THESIS_MAX_MB));
        String key = "theses/" + t.getStudent().getId() + "/screening-v" + t.getVersionNo() + "-" + UUID.randomUUID() + ".pdf";
        store.put(key, checked.bytes(), FileTypes.PDF.mime());
        t.setProviderReportKey(key);
        t.setScreeningManual(true);
        applyScreening(t, similarity, ai, null);
        return toDto(t, t.getStudent());
    }

    /** FR-THS-10: override a screening rejection with a written justification; audited and notified. */
    public ThesisDto override(UUID id, String reason) {
        if (blank(reason)) throw ApiException.validation("A written justification is required.");
        ThesisSubmission t = theses.findWithStudentById(id).orElseThrow(() -> ApiException.notFound("Thesis"));
        if (t.getStatus() != Status.SCREENING_REJECTED) throw ApiException.invalidState("Only a screening rejection can be overridden.");
        latestOnly(t);
        t.setOverrideBy(CurrentUser.id());
        t.setOverrideReason(reason.trim());
        t.setOverrideAt(clock.instant());
        audit.record("THESIS_OVERRIDE", "ThesisSubmission", t.getId(), t.getStudent().getId(),
                Map.of("status", Status.SCREENING_REJECTED.name()), Map.of("status", Status.ACCEPTED_FOR_EXAMINATION.name(), "reason", reason.trim()));
        markAccepted(t, "after an admin override");
        return toDto(t, t.getStudent());
    }

    private void latestOnly(ThesisSubmission t) {
        if (theses.maxVersion(t.getStudent().getId()) != t.getVersionNo()) {
            throw ApiException.invalidState("A newer version exists; act on the latest version.");
        }
    }

    // Pipeline hooks -------------------------------------------------------------------

    /** BR-11: similarity or AI score above the limit rejects the thesis; at or below passes and accepts it. */
    public void applyScreening(ThesisSubmission t, BigDecimal similarity, BigDecimal ai, String reportUrl) {
        BigDecimal simLimit = settings.decimal(SettingsService.SIMILARITY_LIMIT);
        BigDecimal aiLimit = settings.decimal(SettingsService.AI_LIMIT);
        t.setSimilarityPct(similarity);
        t.setAiPct(ai);
        t.setLimitAppliedPct(simLimit);
        t.setAiLimitAppliedPct(aiLimit);
        if (reportUrl != null) t.setProviderReportUrl(reportUrl);
        boolean rejected = similarity.compareTo(simLimit) > 0 || ai.compareTo(aiLimit) > 0;
        Student s = t.getStudent();
        Status next = rejected ? Status.SCREENING_REJECTED : Status.SCREENING_PASSED;
        audit.record(rejected ? "SCREENING_REJECTED" : "SCREENING_PASSED", "ThesisSubmission", t.getId(), s.getId(),
                Map.of("status", t.getStatus().name()), Map.of("status", next.name(), "similarityPct", similarity, "aiPct", ai,
                        "similarityLimit", simLimit, "aiLimit", aiLimit, "manual", t.isScreeningManual()));
        t.setStatus(next);
        List<UUID> recipients = new ArrayList<>(access.studentAndSupervisors(s));
        recipients.addAll(access.admins());
        if (rejected) {
            List<String> over = new ArrayList<>();
            if (similarity.compareTo(simLimit) > 0) over.add("similarity " + similarity.stripTrailingZeros().toPlainString() + "% (limit " + simLimit.stripTrailingZeros().toPlainString() + "%)");
            if (ai.compareTo(aiLimit) > 0) over.add("AI score " + ai.stripTrailingZeros().toPlainString() + "% (limit " + aiLimit.stripTrailingZeros().toPlainString() + "%)");
            notifications.notify(recipients, NotificationCode.N12, "Thesis screening rejected",
                    "Version " + t.getVersionNo() + " of " + s.getUser().getFullName() + "'s thesis was rejected: "
                            + String.join(" and ", over) + ". Revise and upload a new version.", "/student/thesis", true);
        } else {
            notifications.notify(recipients, NotificationCode.N12, "Thesis screening passed",
                    "Version " + t.getVersionNo() + " passed screening: similarity " + similarity.stripTrailingZeros().toPlainString()
                            + "%, AI score " + ai.stripTrailingZeros().toPlainString() + "%.", "/student/thesis");
            markAccepted(t, null);
        }
    }

    /** FR-THS-11 / FR-DLN-10: accepted for examination; completes the student when every milestone is approved. */
    private void markAccepted(ThesisSubmission t, String how) {
        Student s = t.getStudent();
        t.setStatus(Status.ACCEPTED_FOR_EXAMINATION);
        audit.record("THESIS_ACCEPTED", "ThesisSubmission", t.getId(), s.getId(), null,
                Map.of("status", Status.ACCEPTED_FOR_EXAMINATION.name(), "version", t.getVersionNo()));
        List<UUID> recipients = new ArrayList<>(access.studentAndSupervisors(s));
        recipients.addAll(access.admins());
        notifications.notify(recipients, NotificationCode.N13, "Thesis accepted for examination",
                "Version " + t.getVersionNo() + " of " + s.getUser().getFullName() + "'s thesis is accepted for examination"
                        + (how == null ? "." : " " + how + "."), "/student/achievements");
        boolean allApproved = milestones.findByStudentIdOrderBySequenceNo(s.getId()).stream()
                .allMatch(m -> m.getStatus() == MilestoneStatus.APPROVED);
        if (allApproved && !s.isClosed()) {
            StudentStatus old = s.getStatus();
            s.setStatus(StudentStatus.COMPLETED);
            s.setStatusReason("All milestones approved and thesis accepted for examination.");
            s.setCompletedAt(clock.instant());
            audit.record("STUDENT_COMPLETED", "Student", s.getId(), s.getId(), Map.of("status", old.name()), Map.of("status", "COMPLETED"));
        }
    }

    @Transactional(readOnly = true)
    public List<FormatCheckResult> rawResults(UUID thesisId) {
        return results.findByThesis(thesisId);
    }

    // Mapping ------------------------------------------------------------------------------

    ThesisDto toDto(ThesisSubmission t, Student s) {
        List<FormatCheckResult> rs = results.findByThesis(t.getId());
        long failures = rs.stream().filter(r -> !r.isPassed() && !r.isSkipped()).count();
        long skipped = rs.stream().filter(FormatCheckResult::isSkipped).count();
        return new ThesisDto(t.getId(), s.getId(), s.getUser().getFullName(), s.getUser().getStaffOrStudentNo(),
                s.getProgramme().getCode(), t.getVersionNo(), t.getTitle(), t.getAbstractText(), t.getKeywords(), t.getFileName(),
                t.getFileType(), t.getSizeBytes(), t.getStatus().name(), t.getPageCount(), t.getSimilarityPct(), t.getAiPct(),
                t.getLimitAppliedPct(), t.getAiLimitAppliedPct(), t.isScreeningManual(), t.getProviderReportKey() != null,
                t.getProviderReportUrl(), directory.name(t.getOverrideBy()), t.getOverrideReason(), t.getOverrideAt(),
                t.getCreatedAt(), t.getUpdatedAt(), failures, skipped);
    }

    private static void checkPct(BigDecimal v) {
        if (v.signum() < 0 || v.compareTo(BigDecimal.valueOf(100)) > 0) throw ApiException.validation("Scores must be between 0 and 100.");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
