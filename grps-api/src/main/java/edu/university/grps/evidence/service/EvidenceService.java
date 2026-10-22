package edu.university.grps.evidence.service;

import edu.university.grps.audit.AuditService;
import edu.university.grps.common.ApiException;
import edu.university.grps.evidence.domain.EvidenceFile;
import edu.university.grps.evidence.repository.EvidenceFileRepository;
import edu.university.grps.milestone.domain.MilestoneStatus;
import edu.university.grps.milestone.domain.StudentMilestone;
import edu.university.grps.milestone.repository.MilestoneRepositories.StudentMilestoneRepository;
import edu.university.grps.notification.domain.NotificationCode;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.settings.service.SettingsService;
import edu.university.grps.storage.FileStore;
import edu.university.grps.storage.FileTypes;
import edu.university.grps.storage.SignedLinks;
import edu.university.grps.storage.UploadChecks;
import edu.university.grps.storage.VirusScanner;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.service.StudentAccess;
import edu.university.grps.user.service.UserDirectory;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class EvidenceService {

    static final Set<FileTypes> ALLOWED = EnumSet.of(FileTypes.PDF, FileTypes.DOCX, FileTypes.PPTX, FileTypes.JPG, FileTypes.PNG);
    static final Set<MilestoneStatus> UPLOADABLE =
            EnumSet.of(MilestoneStatus.SCHEDULED, MilestoneStatus.AWAITING_VERIFICATION, MilestoneStatus.RETURNED);

    private final EvidenceFileRepository repository;
    private final StudentMilestoneRepository milestones;
    private final StudentAccess access;
    private final UploadChecks uploads;
    private final FileStore store;
    private final SignedLinks links;
    private final VirusScanner scanner;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final UserDirectory directory;
    private final AuditService audit;

    public EvidenceService(EvidenceFileRepository repository, StudentMilestoneRepository milestones, StudentAccess access,
                           UploadChecks uploads, FileStore store, SignedLinks links, VirusScanner scanner,
                           SettingsService settings, NotificationService notifications, UserDirectory directory,
                           AuditService audit) {
        this.repository = repository;
        this.milestones = milestones;
        this.access = access;
        this.uploads = uploads;
        this.store = store;
        this.links = links;
        this.scanner = scanner;
        this.settings = settings;
        this.notifications = notifications;
        this.directory = directory;
        this.audit = audit;
    }

    public record EvidenceDto(UUID id, UUID milestoneId, String fileName, String contentType, long sizeBytes,
                              String kind, int fileVersion, boolean current, UUID supersededBy, String scanStatus,
                              boolean previewable, Instant uploadedAt, String uploadedBy) {}

    /** FR-EVD-01 / FR-EVD-04: upload or replace evidence until the milestone is approved. */
    public EvidenceDto upload(UUID milestoneId, MultipartFile file, EvidenceFile.Kind kind, UUID replacesId) {
        StudentMilestone m = milestones.findWithStudentById(milestoneId).orElseThrow(() -> ApiException.notFound("Milestone"));
        Student student = m.getStudent();
        if (!access.isOwnStudent(student)) throw ApiException.notFound("Milestone");
        access.requireWritable(student);
        if (m.getStatus() == MilestoneStatus.APPROVED) {
            throw ApiException.invalidState("This milestone is approved; its evidence is read-only.");
        }
        if (!UPLOADABLE.contains(m.getStatus())) {
            throw ApiException.invalidState("Evidence can be added once the presentation is scheduled.");
        }
        EvidenceFile replaced = null;
        if (replacesId != null) {
            replaced = repository.findById(replacesId)
                    .filter(e -> e.getMilestone().getId().equals(milestoneId) && e.isCurrent())
                    .orElseThrow(() -> ApiException.notFound("Evidence file"));
        } else if (repository.countCurrent(milestoneId) >= settings.integer(SettingsService.EVIDENCE_MAX_FILES)) {
            throw ApiException.rule("A milestone can have at most " + settings.integer(SettingsService.EVIDENCE_MAX_FILES)
                    + " evidence files. Replace an existing file instead.");
        }
        UploadChecks.Checked checked = uploads.check(file, ALLOWED, settings.megabytes(SettingsService.EVIDENCE_MAX_MB));

        EvidenceFile e = new EvidenceFile();
        e.setMilestone(m);
        e.setFileName(checked.fileName());
        e.setContentType(checked.type().mime());
        e.setSizeBytes(checked.bytes().length);
        e.setSha256(checked.sha256());
        e.setKind(kind == null ? EvidenceFile.Kind.OTHER : kind);
        e.setScanStatus(checked.pendingScan() ? EvidenceFile.ScanStatus.PENDING_SCAN : EvidenceFile.ScanStatus.CLEAN);
        e.setFileVersion(replaced == null ? 1 : replaced.getFileVersion() + 1);
        String key = "evidence/" + student.getId() + "/" + milestoneId + "/" + UUID.randomUUID() + "." + checked.type().extension();
        e.setStorageKey(key);
        store.put(key, checked.bytes(), checked.type().mime());
        e = repository.save(e);
        if (replaced != null) replaced.setSupersededBy(e.getId());

        boolean resubmitted = m.getStatus() == MilestoneStatus.RETURNED;
        if (resubmitted) m.setStatus(MilestoneStatus.AWAITING_VERIFICATION);
        audit.record(replaced == null ? "EVIDENCE_UPLOADED" : "EVIDENCE_REPLACED", "EvidenceFile", e.getId(), student.getId(),
                replaced == null ? null : Map.of("replaced", replaced.getId(), "fileName", replaced.getFileName()),
                Map.of("fileName", e.getFileName(), "kind", e.getKind().name(), "milestone", m.label(), "sha256", e.getSha256()));
        if (m.getStatus() == MilestoneStatus.AWAITING_VERIFICATION) {
            notifications.notify(access.admins(), NotificationCode.N06,
                    resubmitted ? "Returned milestone resubmitted" : "Evidence uploaded",
                    student.getUser().getFullName() + " uploaded " + e.getFileName() + " for " + m.label() + " (" + m.getTitle() + ").",
                    "/admin/verification");
        }
        Map<UUID, String> names = new HashMap<>();
        if (e.getCreatedBy() != null) names.put(e.getCreatedBy(), student.getUser().getFullName());
        return toDto(e, names);
    }

    @Transactional(readOnly = true)
    public List<EvidenceDto> list(UUID milestoneId) {
        StudentMilestone m = milestones.findWithStudentById(milestoneId).orElseThrow(() -> ApiException.notFound("Milestone"));
        if (!access.canView(m.getStudent())) throw ApiException.notFound("Milestone");
        List<EvidenceFile> files = repository.findByMilestone(milestoneId);
        Map<UUID, String> names = directory.names(files.stream().map(EvidenceFile::getCreatedBy).toList());
        return files.stream().map(f -> toDto(f, names)).toList();
    }

    @Transactional(readOnly = true)
    public SignedLinks.Link downloadLink(UUID evidenceId, boolean inline) {
        EvidenceFile e = repository.findWithMilestoneById(evidenceId).orElseThrow(() -> ApiException.notFound("Evidence file"));
        if (!access.canView(e.getMilestone().getStudent())) throw ApiException.notFound("Evidence file");
        if (e.getScanStatus() != EvidenceFile.ScanStatus.CLEAN) {
            throw ApiException.invalidState("This file is waiting for its virus scan.");
        }
        boolean preview = inline && (e.getContentType().equals("application/pdf") || e.getContentType().startsWith("image/"));
        return links.create(e.getStorageKey(), e.getFileName(), e.getContentType(), preview);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> countCurrent(Collection<UUID> milestoneIds) {
        Map<UUID, Long> out = new HashMap<>();
        if (milestoneIds.isEmpty()) return out;
        for (Object[] row : repository.countCurrentByMilestones(milestoneIds)) out.put((UUID) row[0], (Long) row[1]);
        return out;
    }

    @Transactional(readOnly = true)
    public long countCurrentClean(UUID milestoneId) {
        return repository.countCurrentClean(milestoneId);
    }

    /** Rescans files stored while the scanner was unavailable. */
    public int rescanPending() {
        int done = 0;
        for (EvidenceFile e : repository.findByScanStatus(EvidenceFile.ScanStatus.PENDING_SCAN)) {
            byte[] bytes;
            try (InputStream in = store.get(e.getStorageKey())) {
                bytes = in.readAllBytes();
            } catch (IOException | RuntimeException ex) {
                continue;
            }
            VirusScanner.Result result = scanner.scan(bytes);
            if (result == VirusScanner.Result.UNAVAILABLE) break;
            e.setScanStatus(result == VirusScanner.Result.CLEAN ? EvidenceFile.ScanStatus.CLEAN : EvidenceFile.ScanStatus.INFECTED);
            if (result == VirusScanner.Result.INFECTED) store.delete(e.getStorageKey());
            done++;
        }
        return done;
    }

    private static EvidenceDto toDto(EvidenceFile f, Map<UUID, String> names) {
        return new EvidenceDto(f.getId(), f.getMilestone().getId(), f.getFileName(), f.getContentType(), f.getSizeBytes(),
                f.getKind().name(), f.getFileVersion(), f.isCurrent(), f.getSupersededBy(), f.getScanStatus().name(),
                f.getContentType().equals("application/pdf") || f.getContentType().startsWith("image/"),
                f.getCreatedAt(), names.get(f.getCreatedBy()));
    }
}
