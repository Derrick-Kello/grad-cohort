package edu.university.grps.thesis.service;

import edu.university.grps.audit.AuditService;
import edu.university.grps.notification.domain.NotificationCode;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.storage.FileStore;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.service.StudentAccess;
import edu.university.grps.thesis.domain.FormatCheckResult;
import edu.university.grps.thesis.domain.FormatRule;
import edu.university.grps.thesis.domain.ThesisSubmission;
import edu.university.grps.thesis.domain.ThesisSubmission.Status;
import edu.university.grps.thesis.formatcheck.FormatChecker;
import edu.university.grps.thesis.formatcheck.FormatReport;
import edu.university.grps.thesis.formatcheck.RuleResult;
import edu.university.grps.thesis.repository.ThesisRepositories.FormatCheckResultRepository;
import edu.university.grps.thesis.repository.ThesisRepositories.FormatRuleRepository;
import edu.university.grps.thesis.repository.ThesisRepositories.ThesisSubmissionRepository;
import edu.university.grps.thesis.screening.ScreeningProvider;
import edu.university.grps.thesis.service.ThesisService.ThesisUploadedEvent;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Asynchronous thesis gates (PRD section 24): format check on upload, then screening once the format passes.
 * Runs on the bounded pipeline executor after the upload commits.
 */
@Component
public class ThesisPipeline {

    private static final Logger log = LoggerFactory.getLogger(ThesisPipeline.class);

    private final ThesisSubmissionRepository theses;
    private final FormatCheckResultRepository results;
    private final FormatRuleRepository rules;
    private final FormatChecker checker;
    private final ScreeningProvider provider;
    private final ThesisService thesisService;
    private final FileStore store;
    private final StudentAccess access;
    private final NotificationService notifications;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public ThesisPipeline(ThesisSubmissionRepository theses, FormatCheckResultRepository results, FormatRuleRepository rules,
                          FormatChecker checker, ScreeningProvider provider, ThesisService thesisService, FileStore store,
                          StudentAccess access, NotificationService notifications, AuditService audit,
                          TransactionTemplate tx) {
        this.theses = theses;
        this.results = results;
        this.rules = rules;
        this.checker = checker;
        this.provider = provider;
        this.thesisService = thesisService;
        this.store = store;
        this.access = access;
        this.notifications = notifications;
        this.audit = audit;
        this.tx = tx;
    }

    @Async("pipelineExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUploaded(ThesisUploadedEvent event) {
        try {
            if (runFormatCheck(event.thesisId())) startScreening(event.thesisId());
        } catch (RuntimeException e) {
            log.error("Thesis pipeline failed for {}", event.thesisId(), e);
        }
    }

    /** FR-THS-03: FORMAT_CHECKING then FORMAT_PASSED or FORMAT_FAILED. Returns true when the format passed. */
    public boolean runFormatCheck(UUID thesisId) {
        record Job(String key, String fileType, Map<String, Map<String, Object>> active) {}
        Job job = tx.execute(s -> {
            ThesisSubmission t = theses.findWithStudentById(thesisId).orElseThrow();
            if (t.getStatus() != Status.UPLOADED && t.getStatus() != Status.FORMAT_CHECKING) return null;
            t.setStatus(Status.FORMAT_CHECKING);
            Map<String, Map<String, Object>> active = new HashMap<>();
            for (FormatRule r : rules.findByProgramme(t.getStudent().getProgramme().getId())) {
                if (r.isActive()) active.put(r.getRuleCode(), r.getParameters());
            }
            return new Job(t.getStorageKey(), t.getFileType(), active);
        });
        if (job == null) return false;
        byte[] bytes;
        try (InputStream in = store.get(job.key())) {
            bytes = in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Could not read thesis file", e);
        }
        long started = System.currentTimeMillis();
        FormatReport report = checker.check(bytes, job.fileType(), job.active());
        log.info("Format check of thesis {} took {} ms: {}", thesisId, System.currentTimeMillis() - started,
                report.passed() ? "passed" : "failed");
        Boolean passed = tx.execute(s -> {
            ThesisSubmission t = theses.findWithStudentById(thesisId).orElseThrow();
            results.deleteByThesis(thesisId);
            for (RuleResult r : report.results()) {
                FormatCheckResult row = new FormatCheckResult();
                row.setThesis(t);
                row.setRuleCode(r.ruleCode());
                row.setPassed(r.passed());
                row.setSkipped(r.skipped());
                row.setDetail(r.detail());
                row.setLocation(r.location());
                results.save(row);
            }
            t.setPageCount(report.pageCount());
            Status next = report.passed() ? Status.FORMAT_PASSED : Status.FORMAT_FAILED;
            t.setStatus(next);
            long failed = report.results().stream().filter(r -> !r.passed() && !r.skipped()).count();
            audit.record(next == Status.FORMAT_PASSED ? "FORMAT_PASSED" : "FORMAT_FAILED", "ThesisSubmission", t.getId(),
                    t.getStudent().getId(), Map.of("status", Status.FORMAT_CHECKING.name()),
                    Map.of("status", next.name(), "failedRules", failed));
            Student student = t.getStudent();
            notifications.notify(access.studentAndSupervisors(student), NotificationCode.N11,
                    report.passed() ? "Thesis format check passed" : "Thesis format check failed",
                    report.passed()
                            ? "Version " + t.getVersionNo() + " passed the format check and is now being screened."
                            : "Version " + t.getVersionNo() + " failed " + failed + " format rule(s). Download the report, fix the "
                                    + "document and upload a new version.",
                    "/student/thesis", !report.passed());
            return report.passed();
        });
        return Boolean.TRUE.equals(passed);
    }

    /** FR-THS-06: passing the format check starts screening automatically. */
    public void startScreening(UUID thesisId) {
        record Sub(ScreeningProvider.Submission submission) {}
        Sub sub = tx.execute(s -> {
            ThesisSubmission t = theses.findWithStudentById(thesisId).orElseThrow();
            if (t.getStatus() != Status.FORMAT_PASSED) return null;
            t.setStatus(Status.SCREENING);
            byte[] bytes;
            try (InputStream in = store.get(t.getStorageKey())) {
                bytes = in.readAllBytes();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return new Sub(new ScreeningProvider.Submission(t.getFileName(), t.getTitle(), t.getStudent().getUser().getFullName(), bytes));
        });
        if (sub == null) return;
        String ref;
        try {
            ref = provider.submit(sub.submission());
        } catch (RuntimeException e) {
            log.warn("Screening submission failed for thesis {}: {}", thesisId, e.getMessage());
            tx.executeWithoutResult(s -> {
                ThesisSubmission t = theses.findWithStudentById(thesisId).orElseThrow();
                notifications.notify(access.admins(), NotificationCode.N12, "Screening needs manual entry",
                        "The screening provider could not be reached for " + t.getStudent().getUser().getFullName()
                                + "'s thesis (version " + t.getVersionNo() + "). Enter the scores manually.", "/admin/theses");
            });
            return;
        }
        tx.executeWithoutResult(s -> {
            ThesisSubmission t = theses.findWithStudentById(thesisId).orElseThrow();
            t.setProviderRef(ref);
            audit.record("SCREENING_SUBMITTED", "ThesisSubmission", t.getId(), t.getStudent().getId(), null,
                    Map.of("provider", provider.name(), "ref", ref));
        });
        poll(thesisId);
    }

    /** ScreeningPollJob: fetch pending results from the provider. */
    public int pollAll() {
        List<UUID> pending = tx.execute(s -> {
            List<UUID> ids = new ArrayList<>();
            theses.findByStatusIn(List.of(Status.SCREENING)).forEach(t -> { if (t.getProviderRef() != null) ids.add(t.getId()); });
            return ids;
        });
        int done = 0;
        for (UUID id : pending == null ? List.<UUID>of() : pending) if (poll(id)) done++;
        return done;
    }

    private boolean poll(UUID thesisId) {
        String ref = tx.execute(s -> theses.findById(thesisId).filter(t -> t.getStatus() == Status.SCREENING)
                .map(ThesisSubmission::getProviderRef).orElse(null));
        if (ref == null) return false;
        var result = provider.fetchResult(ref);
        if (result.isEmpty()) return false;
        tx.executeWithoutResult(s -> {
            ThesisSubmission t = theses.findWithStudentById(thesisId).orElseThrow();
            if (t.getStatus() != Status.SCREENING) return;
            thesisService.applyScreening(t, result.get().similarityPct(), result.get().aiPct(), result.get().reportUrl());
        });
        return true;
    }
}
