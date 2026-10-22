package edu.university.grps.evidence.api;

import edu.university.grps.evidence.domain.EvidenceFile;
import edu.university.grps.evidence.service.EvidenceService;
import edu.university.grps.evidence.service.EvidenceService.EvidenceDto;
import edu.university.grps.storage.SignedLinks;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class EvidenceController {

    private final EvidenceService service;

    public EvidenceController(EvidenceService service) {
        this.service = service;
    }

    @PostMapping(path = "/milestones/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public EvidenceDto upload(@PathVariable UUID id, @RequestPart("file") MultipartFile file,
                              @RequestParam(defaultValue = "OTHER") EvidenceFile.Kind kind,
                              @RequestParam(required = false) UUID replaces) {
        return service.upload(id, file, kind, replaces);
    }

    @GetMapping("/milestones/{id}/evidence")
    @PreAuthorize("hasAnyRole('STUDENT','SUPERVISOR','ADMIN','SECRETARY')")
    public List<EvidenceDto> list(@PathVariable UUID id) {
        return service.list(id);
    }

    @GetMapping("/evidence/{id}/download")
    @PreAuthorize("hasAnyRole('STUDENT','SUPERVISOR','ADMIN','SECRETARY')")
    public SignedLinks.Link download(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean inline) {
        return service.downloadLink(id, inline);
    }
}
