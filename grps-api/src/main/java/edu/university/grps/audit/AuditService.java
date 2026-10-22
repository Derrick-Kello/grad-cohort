package edu.university.grps.audit;

import edu.university.grps.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Writes the audit trail inside the caller's transaction, so a change and its audit entry commit together. */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, UUID entityId, UUID studentId,
                       Map<String, ?> oldValue, Map<String, ?> newValue) {
        AuditLog entry = new AuditLog();
        entry.setActorId(CurrentUser.optionalId().orElse(null));
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setStudentId(studentId);
        entry.setOldValue(copy(oldValue));
        entry.setNewValue(copy(newValue));
        entry.setIpAddress(clientIp());
        entry.setAt(clock.instant());
        repository.save(entry);
    }

    public void record(String action, String entityType, UUID entityId, Map<String, ?> newValue) {
        record(action, entityType, entityId, null, null, newValue);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(UUID actorId, String entityType, UUID entityId, UUID studentId,
                                 Instant from, Instant to, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (actorId != null) p.add(cb.equal(root.get("actorId"), actorId));
            if (entityType != null && !entityType.isBlank()) p.add(cb.equal(root.get("entityType"), entityType));
            if (entityId != null) p.add(cb.equal(root.get("entityId"), entityId));
            if (studentId != null) p.add(cb.equal(root.get("studentId"), studentId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("at"), from));
            if (to != null) p.add(cb.lessThan(root.get("at"), to));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, pageable);
    }

    private static Map<String, Object> copy(Map<String, ?> values) {
        if (values == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        values.forEach((k, v) -> out.put(k, v == null ? null : (v instanceof Number || v instanceof Boolean) ? v : v.toString()));
        return out;
    }

    private static String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) return null;
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
