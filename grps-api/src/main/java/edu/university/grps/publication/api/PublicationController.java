package edu.university.grps.publication.api;

import edu.university.grps.publication.domain.Publication;
import edu.university.grps.publication.service.DoiResolver;
import edu.university.grps.publication.service.DoiResolver.DoiMetadata;
import edu.university.grps.publication.service.PublicationService;
import edu.university.grps.publication.service.PublicationService.ChecklistInput;
import edu.university.grps.publication.service.PublicationService.PublicationDto;
import edu.university.grps.publication.service.PublicationService.PublicationInput;
import edu.university.grps.storage.SignedLinks;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class PublicationController {

    private final PublicationService service;
    private final DoiResolver doiResolver;

    public PublicationController(PublicationService service, DoiResolver doiResolver) {
        this.service = service;
        this.doiResolver = doiResolver;
    }

    public record NoteRequest(String note) {}

    public record ReasonRequest(String reason) {}

    // Student

    @GetMapping("/publications")
    @PreAuthorize("hasRole('STUDENT')")
    public List<PublicationDto> mine() {
        return service.mine();
    }

    @PostMapping("/publications")
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicationDto create(@RequestBody PublicationInput input) {
        return service.create(input);
    }

    @GetMapping("/publications/{id}")
    public PublicationDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/publications/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public PublicationDto update(@PathVariable UUID id, @RequestBody PublicationInput input) {
        return service.update(id, input);
    }

    @DeleteMapping("/publications/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deleteDraft(id);
    }

    @PostMapping(path = "/publications/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public PublicationDto upload(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return service.uploadFile(id, file);
    }

    @GetMapping("/publications/{id}/file")
    public SignedLinks.Link file(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean inline) {
        return service.fileLink(id, inline);
    }

    @PostMapping("/publications/{id}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public PublicationDto submit(@PathVariable UUID id) {
        return service.submit(id);
    }

    @GetMapping("/publications/doi-lookup")
    @PreAuthorize("hasAnyRole('STUDENT','LIBRARIAN')")
    public DoiMetadata doiLookup(@RequestParam String doi) {
        return doiResolver.lookup(doi);
    }

    // Supervisor

    @GetMapping("/supervisor/publications")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public List<PublicationDto> supervisees() {
        return service.forSupervisor();
    }

    // Librarian

    @GetMapping("/librarian/publications/queue")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public List<PublicationDto> queue(@RequestParam(required = false) String programme,
                                      @RequestParam(required = false) Publication.Type type,
                                      @RequestParam(required = false) Publication.ReviewStatus status) {
        return service.queue(programme, type, status);
    }

    @GetMapping("/librarian/publications/approved")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public List<PublicationDto> approved() {
        return service.approved();
    }

    @PutMapping("/librarian/publications/{id}/checklist")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public PublicationDto checklist(@PathVariable UUID id, @RequestBody ChecklistInput input) {
        return service.updateChecklist(id, input);
    }

    @PostMapping("/librarian/publications/{id}/approve")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public PublicationDto approve(@PathVariable UUID id, @RequestBody(required = false) NoteRequest request) {
        return service.approve(id, request == null ? null : request.note());
    }

    @PostMapping("/librarian/publications/{id}/request-changes")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public PublicationDto requestChanges(@PathVariable UUID id, @RequestBody ReasonRequest request) {
        return service.requestChanges(id, request.reason());
    }

    @PostMapping("/librarian/publications/{id}/reject")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public PublicationDto reject(@PathVariable UUID id, @RequestBody ReasonRequest request) {
        return service.reject(id, request.reason());
    }

    // Admin

    @PostMapping("/admin/publications/{id}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public PublicationDto revoke(@PathVariable UUID id, @RequestBody ReasonRequest request) {
        return service.revoke(id, request.reason());
    }
}
