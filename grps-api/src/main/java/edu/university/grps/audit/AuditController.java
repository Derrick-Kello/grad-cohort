package edu.university.grps.audit;

import edu.university.grps.common.PageResponse;
import edu.university.grps.user.repository.AppUserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import edu.university.grps.common.Times;

@RestController
@RequestMapping("/api/v1/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService auditService;
    private final AppUserRepository users;

    public AuditController(AuditService auditService, AppUserRepository users) {
        this.auditService = auditService;
        this.users = users;
    }

    public record AuditEntryDto(UUID id, Instant at, UUID actorId, String actorName, String action, String entityType,
                                UUID entityId, UUID studentId, Map<String, Object> oldValue,
                                Map<String, Object> newValue, String ipAddress) {}

    @GetMapping
    public PageResponse<AuditEntryDto> search(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Instant fromInstant = from == null ? null : from.atStartOfDay(Times.ZONE).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(Times.ZONE).toInstant();
        var result = auditService.search(userId, entityType, entityId, studentId, fromInstant, toInstant,
                PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "at")));
        var actorIds = result.getContent().stream().map(AuditLog::getActorId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = users.findAllById(actorIds).stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getFullName()));
        return PageResponse.of(result, a -> new AuditEntryDto(a.getId(), a.getAt(), a.getActorId(),
                a.getActorId() == null ? "System" : names.getOrDefault(a.getActorId(), "Unknown"), a.getAction(),
                a.getEntityType(), a.getEntityId(), a.getStudentId(), a.getOldValue(), a.getNewValue(),
                a.getIpAddress()));
    }
}
