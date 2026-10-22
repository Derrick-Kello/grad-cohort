package edu.university.grps.publication.service;

import edu.university.grps.audit.AuditService;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.ErrorCode;
import edu.university.grps.notification.domain.NotificationCode;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.publication.domain.Publication;
import edu.university.grps.publication.domain.Publication.ReviewStatus;
import edu.university.grps.publication.repository.PublicationRepository;
import edu.university.grps.security.CurrentUser;
import edu.university.grps.settings.service.SettingsService;
import edu.university.grps.storage.FileStore;
import edu.university.grps.storage.FileTypes;
import edu.university.grps.storage.SignedLinks;
import edu.university.grps.storage.UploadChecks;
import edu.university.grps.student.domain.Student;
import edu.university.grps.student.service.StudentAccess;
import edu.university.grps.user.domain.RoleCode;
import edu.university.grps.user.service.UserDirectory;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
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

/** Publication submission and librarian review (section 10). */
@Service
@Transactional
public class PublicationService {

    private final PublicationRepository repository;
    private final StudentAccess access;
    private final UploadChecks uploads;
    private final FileStore store;
    private final SignedLinks links;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final UserDirectory directory;
    private final AuditService audit;
    private final Clock clock;

    public PublicationService(PublicationRepository repository, StudentAccess access, UploadChecks uploads, FileStore store,
                              SignedLinks links, SettingsService settings, NotificationService notifications,
                              UserDirectory directory, AuditService audit, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.uploads = uploads;
        this.store = store;
        this.links = links;
        this.settings = settings;
        this.notifications = notifications;
        this.directory = directory;
        this.audit = audit;
        this.clock = clock;
    }

    public record PublicationInput(String title, Publication.Type type, String venueName, String authors, Integer year,
                                   String volume, String issue, String pages, String doi, String url, String issnIsbn,
                                   Publication.PubStatus pubStatus) {}

    public record ChecklistInput(boolean venueVerified, boolean authorshipVerified, boolean indexingChecked) {}

    public record PublicationDto(UUID id, UUID studentId, String studentName, String studentNumber, String programmeCode,
                                 String title, String type, String venueName, String authors, Integer year, String volume,
                                 String issue, String pages, String doi, String url, String issnIsbn, String pubStatus,
                                 boolean hasFile, String fileName, String reviewStatus, Instant submittedAt,
                                 Instant reviewedAt, String reviewedByName, String reviewNote, boolean venueVerified,
                                 boolean authorshipVerified, boolean indexingChecked, long daysWaiting,
                                 List<String> duplicateWarnings, Instant updatedAt, Long version) {}

    // Student ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PublicationDto> mine() {
        Student s = access.currentStudent();
        return repository.findByStudentIdOrderByUpdatedAtDesc(s.getId()).stream().map(p -> toDto(p, s, false)).toList();
    }

    public PublicationDto create(PublicationInput in) {
        Student s = access.currentStudent();
        access.requireWritable(s);
        Publication p = new Publication();
        p.setStudent(s);
        apply(p, in);
        p.setReviewStatus(ReviewStatus.DRAFT);
        p = repository.save(p);
        audit.record("PUBLICATION_CREATED", "Publication", p.getId(), s.getId(), null, Map.of("title", p.getTitle()));
        return toDto(p, s, true);
    }

    public PublicationDto update(UUID id, PublicationInput in) {
        Publication p = ownEditable(id);
        apply(p, in);
        audit.record("PUBLICATION_UPDATED", "Publication", p.getId(), p.getStudent().getId(), null, Map.of("title", p.getTitle()));
        return toDto(p, p.getStudent(), true);
    }

    public PublicationDto uploadFile(UUID id, MultipartFile file) {
        Publication p = ownEditable(id);
        UploadChecks.Checked checked = uploads.check(file, EnumSet.of(FileTypes.PDF), settings.megabytes(SettingsService.PUBLICATION_MAX_MB));
        if (checked.pendingScan()) throw ApiException.invalidState("Virus scanning is unavailable right now. Try again shortly.");
        String key = "publications/" + p.getStudent().getId() + "/" + p.getId() + "/" + UUID.randomUUID() + ".pdf";
        store.put(key, checked.bytes(), FileTypes.PDF.mime());
        p.setStorageKey(key);
        p.setFileName(checked.fileName());
        audit.record("PUBLICATION_FILE_UPLOADED", "Publication", p.getId(), p.getStudent().getId(), null,
                Map.of("fileName", checked.fileName(), "sha256", checked.sha256()));
        return toDto(p, p.getStudent(), true);
    }

    public void deleteDraft(UUID id) {
        Publication p = ownEditable(id);
        if (p.getReviewStatus() != ReviewStatus.DRAFT) throw ApiException.invalidState("Only drafts can be deleted.");
        audit.record("PUBLICATION_DELETED", "Publication", p.getId(), p.getStudent().getId(), Map.of("title", p.getTitle()), null);
        repository.delete(p);
    }

    /** FR-PUB-02: Submit moves the record to SUBMITTED and notifies the librarians (N-09). */
    public PublicationDto submit(UUID id) {
        Publication p = ownEditable(id);
        access.requireWritable(p.getStudent());
        List<String> missing = new ArrayList<>();
        if (blank(p.getVenueName())) missing.add("journal or conference name");
        if (p.getYear() == null) missing.add("year");
        if (blank(p.getDoi()) && blank(p.getUrl())) missing.add("DOI or URL");
        if (p.getStorageKey() == null) missing.add("full-text PDF");
        if (!missing.isEmpty()) throw ApiException.validation("Add the " + String.join(", ", missing) + " before submitting.");
        ReviewStatus old = p.getReviewStatus();
        p.setReviewStatus(ReviewStatus.SUBMITTED);
        p.setSubmittedAt(clock.instant());
        p.setVenueVerified(false);
        p.setAuthorshipVerified(false);
        p.setIndexingChecked(false);
        audit.record("PUBLICATION_SUBMITTED", "Publication", p.getId(), p.getStudent().getId(), Map.of("status", old.name()),
                Map.of("status", "SUBMITTED"));
        notifications.notify(access.librarians(), NotificationCode.N09, "Publication submitted for review",
                p.getStudent().getUser().getFullName() + " submitted \"" + p.getTitle() + "\".", "/librarian/queue");
        return toDto(p, p.getStudent(), true);
    }

    private Publication ownEditable(UUID id) {
        Publication p = repository.findWithStudentById(id).orElseThrow(() -> ApiException.notFound("Publication"));
        if (!access.isOwnStudent(p.getStudent())) throw ApiException.notFound("Publication");
        if (!p.isEditableByStudent()) {
            throw ApiException.invalidState(p.getReviewStatus() == ReviewStatus.APPROVED
                    ? "Approved publications are read-only." : "This publication cannot be edited in its current state.");
        }
        return p;
    }

    private void apply(Publication p, PublicationInput in) {
        if (blank(in.title())) throw ApiException.validation("Title is required.");
        if (in.type() == null) throw ApiException.validation("Choose a publication type.");
        if (blank(in.authors())) throw ApiException.validation("List the authors.");
        if (in.pubStatus() == null) throw ApiException.validation("Choose accepted or published.");
        int thisYear = clock.instant().atZone(java.time.ZoneOffset.UTC).getYear();
        if (in.year() != null && (in.year() < 1950 || in.year() > thisYear + 1)) throw ApiException.validation("Enter a valid year.");
        String doi = normaliseDoi(in.doi());
        if (doi != null && !doi.matches("^10\\.\\d{4,9}/\\S+$")) throw ApiException.validation("The DOI should look like 10.1234/abcd.");
        if (!blank(in.url()) && !in.url().trim().matches("(?i)^https?://\\S+$")) throw ApiException.validation("The URL must start with http:// or https://.");
        p.setTitle(in.title().trim());
        p.setType(in.type());
        p.setVenueName(trim(in.venueName()));
        p.setAuthors(in.authors().trim());
        p.setYear(in.year());
        p.setVolume(trim(in.volume()));
        p.setIssue(trim(in.issue()));
        p.setPages(trim(in.pages()));
        p.setDoi(doi);
        p.setUrl(trim(in.url()));
        p.setIssnIsbn(trim(in.issnIsbn()));
        p.setPubStatus(in.pubStatus());
    }

    // Viewing ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PublicationDto get(UUID id) {
        Publication p = repository.findWithStudentById(id).orElseThrow(() -> ApiException.notFound("Publication"));
        Student s = p.getStudent();
        boolean own = access.isOwnStudent(s);
        boolean staff = CurrentUser.isAdmin() || CurrentUser.hasRole(RoleCode.LIBRARIAN) || access.isSupervisorOf(s.getId());
        if (!own && !(staff && p.getReviewStatus() != ReviewStatus.DRAFT)) throw ApiException.notFound("Publication");
        return toDto(p, s, true);
    }

    @Transactional(readOnly = true)
    public SignedLinks.Link fileLink(UUID id, boolean inline) {
        PublicationDto dto = get(id);
        Publication p = repository.findById(dto.id()).orElseThrow();
        if (p.getStorageKey() == null) throw ApiException.notFound("File");
        return links.create(p.getStorageKey(), p.getFileName(), FileTypes.PDF.mime(), inline);
    }

    /** FR-PUB-09: supervisors see supervisees' submitted and approved publications, read-only. */
    @Transactional(readOnly = true)
    public List<PublicationDto> forSupervisor() {
        List<UUID> ids = access.supervisedStudentIds(CurrentUser.id());
        if (ids.isEmpty()) return List.of();
        return repository.findVisibleForStudents(ids).stream().map(p -> toDto(p, p.getStudent(), false)).toList();
    }

    @Transactional(readOnly = true)
    public List<PublicationDto> approvedForStudent(UUID studentId) {
        return repository.findByStudentIdOrderByUpdatedAtDesc(studentId).stream()
                .filter(p -> p.getReviewStatus() == ReviewStatus.APPROVED)
                .map(p -> toDto(p, p.getStudent(), false)).toList();
    }

    // Librarian ----------------------------------------------------------------

    /** FR-PUB-04: oldest first, filterable by programme, type and status, with days waiting. */
    @Transactional(readOnly = true)
    public List<PublicationDto> queue(String programmeCode, Publication.Type type, ReviewStatus status) {
        Set<ReviewStatus> statuses = status == null ? EnumSet.of(ReviewStatus.SUBMITTED) : EnumSet.of(status);
        if (statuses.contains(ReviewStatus.DRAFT)) statuses.remove(ReviewStatus.DRAFT);
        return repository.findByReviewStatusInOrderBySubmittedAtAsc(statuses).stream()
                .filter(p -> programmeCode == null || programmeCode.isBlank() || p.getStudent().getProgramme().getCode().equalsIgnoreCase(programmeCode))
                .filter(p -> type == null || p.getType() == type)
                .map(p -> toDto(p, p.getStudent(), true)).toList();
    }

    @Transactional(readOnly = true)
    public List<PublicationDto> approved() {
        return repository.findApproved().stream().map(p -> toDto(p, p.getStudent(), false)).toList();
    }

    public PublicationDto updateChecklist(UUID id, ChecklistInput in) {
        Publication p = underReview(id);
        p.setVenueVerified(in.venueVerified());
        p.setAuthorshipVerified(in.authorshipVerified());
        p.setIndexingChecked(in.indexingChecked());
        audit.record("PUBLICATION_CHECKLIST", "Publication", p.getId(), p.getStudent().getId(), null,
                Map.of("venueVerified", in.venueVerified(), "authorshipVerified", in.authorshipVerified(),
                        "indexingChecked", in.indexingChecked()));
        return toDto(p, p.getStudent(), true);
    }

    /** FR-PUB-06: approval links the publication to the student and notifies student and supervisor (N-10). */
    public PublicationDto approve(UUID id, String note) {
        Publication p = underReview(id);
        if (p.getDoi() != null && repository.findByDoi(p.getDoi(), p.getId()).stream()
                .anyMatch(x -> x.getReviewStatus() == ReviewStatus.APPROVED)) {
            throw new ApiException(ErrorCode.DUPLICATE, "Another approved publication already has DOI " + p.getDoi() + ".");
        }
        decide(p, ReviewStatus.APPROVED, trim(note));
        notifications.notify(access.studentAndSupervisors(p.getStudent()), NotificationCode.N10, "Publication approved",
                "\"" + p.getTitle() + "\" was verified by the library and added to " + p.getStudent().getUser().getFullName()
                        + "'s record.", "/student/achievements");
        return toDto(p, p.getStudent(), true);
    }

    /** FR-PUB-07: CHANGES_REQUESTED lets the student edit and resubmit. */
    public PublicationDto requestChanges(UUID id, String reason) {
        requireReason(reason);
        Publication p = underReview(id);
        decide(p, ReviewStatus.CHANGES_REQUESTED, reason.trim());
        notifications.notify(access.studentAndSupervisors(p.getStudent()), NotificationCode.N10, "Changes requested on a publication",
                "The library asked for changes to \"" + p.getTitle() + "\": " + reason.trim(), "/student/publications", true);
        return toDto(p, p.getStudent(), true);
    }

    /** FR-PUB-07: REJECTED is final. */
    public PublicationDto reject(UUID id, String reason) {
        requireReason(reason);
        Publication p = underReview(id);
        decide(p, ReviewStatus.REJECTED, reason.trim());
        notifications.notify(access.studentAndSupervisors(p.getStudent()), NotificationCode.N10, "Publication rejected",
                "The library rejected \"" + p.getTitle() + "\": " + reason.trim(), "/student/publications", true);
        return toDto(p, p.getStudent(), true);
    }

    /** FR-PUB-08: only an admin can revoke an approval, with a reason; the student can then correct and resubmit. */
    public PublicationDto revoke(UUID id, String reason) {
        requireReason(reason);
        Publication p = repository.findWithStudentById(id).orElseThrow(() -> ApiException.notFound("Publication"));
        if (p.getReviewStatus() != ReviewStatus.APPROVED) throw ApiException.invalidState("Only an approved publication can be revoked.");
        decide(p, ReviewStatus.CHANGES_REQUESTED, "Approval revoked: " + reason.trim());
        notifications.notify(access.studentAndSupervisors(p.getStudent()), NotificationCode.N10, "Publication approval revoked",
                "The graduate school revoked the approval of \"" + p.getTitle() + "\": " + reason.trim(), "/student/publications", true);
        return toDto(p, p.getStudent(), true);
    }

    private Publication underReview(UUID id) {
        Publication p = repository.findWithStudentById(id).orElseThrow(() -> ApiException.notFound("Publication"));
        if (p.getReviewStatus() != ReviewStatus.SUBMITTED) throw ApiException.invalidState("This publication is not awaiting review.");
        return p;
    }

    private void decide(Publication p, ReviewStatus status, String note) {
        ReviewStatus old = p.getReviewStatus();
        p.setReviewStatus(status);
        p.setReviewedBy(CurrentUser.id());
        p.setReviewedAt(clock.instant());
        p.setReviewNote(note);
        audit.record("PUBLICATION_" + status.name(), "Publication", p.getId(), p.getStudent().getId(), Map.of("status", old.name()),
                note == null ? Map.of("status", status.name()) : Map.of("status", status.name(), "note", note));
    }

    // DTOs and duplicate checks (FR-PUB-03) ------------------------------------------

    private PublicationDto toDto(Publication p, Student s, boolean withWarnings) {
        Instant now = clock.instant();
        long days = p.getReviewStatus() == ReviewStatus.SUBMITTED && p.getSubmittedAt() != null
                ? ChronoUnit.DAYS.between(p.getSubmittedAt(), now) : 0;
        return new PublicationDto(p.getId(), s.getId(), s.getUser().getFullName(), s.getUser().getStaffOrStudentNo(),
                s.getProgramme().getCode(), p.getTitle(), p.getType().name(), p.getVenueName(), p.getAuthors(), p.getYear(),
                p.getVolume(), p.getIssue(), p.getPages(), p.getDoi(), p.getUrl(), p.getIssnIsbn(), p.getPubStatus().name(),
                p.getStorageKey() != null, p.getFileName(), p.getReviewStatus().name(), p.getSubmittedAt(), p.getReviewedAt(),
                directory.name(p.getReviewedBy()), p.getReviewNote(), p.isVenueVerified(), p.isAuthorshipVerified(),
                p.isIndexingChecked(), days, withWarnings ? duplicateWarnings(p) : List.of(), p.getUpdatedAt(), p.getVersion());
    }

    List<String> duplicateWarnings(Publication p) {
        List<String> warnings = new ArrayList<>();
        UUID exclude = p.getId() == null ? new UUID(0, 0) : p.getId();
        if (p.getDoi() != null) {
            for (Publication other : repository.findByDoi(p.getDoi(), exclude)) {
                warnings.add("DOI " + p.getDoi() + " is already linked to " + other.getStudent().getUser().getFullName()
                        + " (" + other.getReviewStatus().name().toLowerCase().replace('_', ' ') + ").");
            }
        }
        Set<String> mine = words(p.getTitle());
        if (mine.size() >= 3) {
            for (Publication other : repository.findComparable(exclude)) {
                if (similarity(mine, words(other.getTitle())) >= 0.9) {
                    warnings.add("A near-identical title already exists: \"" + other.getTitle() + "\".");
                    break;
                }
            }
        }
        return warnings;
    }

    static Set<String> words(String title) {
        return Arrays.stream(title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").split("\\s+"))
                .filter(w -> w.length() > 2).collect(Collectors.toCollection(HashSet::new));
    }

    static double similarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return inter.size() / (double) union.size();
    }

    static String normaliseDoi(String doi) {
        if (doi == null || doi.isBlank()) return null;
        return doi.trim().replaceFirst("(?i)^(https?://)?(dx\\.)?doi\\.org/", "").replaceFirst("(?i)^doi:\\s*", "");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return blank(s) ? null : s.trim();
    }

    private static void requireReason(String reason) {
        if (blank(reason)) throw ApiException.validation("A reason is required.");
    }

    @Transactional(readOnly = true)
    public long approvedCount(UUID studentId) {
        return repository.countByStudentIdAndReviewStatus(studentId, ReviewStatus.APPROVED);
    }

    @Transactional(readOnly = true)
    public List<Publication> approvedEntities(Collection<UUID> studentIds) {
        return repository.findApproved().stream().filter(p -> studentIds.contains(p.getStudent().getId())).toList();
    }
}
