package edu.university.grps.settings.service;

import edu.university.grps.audit.AuditService;
import edu.university.grps.common.ApiException;
import edu.university.grps.common.ErrorCode;
import edu.university.grps.settings.domain.SystemSetting;
import edu.university.grps.settings.repository.SystemSettingRepository;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every threshold in the PRD is a setting, not code (section 15). Changes apply from the time of change
 * and past decisions are not recalculated (FR-SET-02).
 */
@Service
public class SettingsService {

    public static final String SIMILARITY_LIMIT = "screening.similarity_limit_pct";
    public static final String AI_LIMIT = "screening.ai_limit_pct";
    public static final String ATTENDANCE_THRESHOLD = "attendance.threshold_pct";
    public static final String ATTENDANCE_WARNING = "attendance.warning_pct";
    public static final String LECTURER_EDIT_DAYS = "attendance.lecturer_edit_days";
    public static final String MAX_ATTEMPTS = "milestone.max_attempts";
    public static final String SECRETARY_CORRECTION_HOURS = "milestone.secretary_correction_hours";
    public static final String REMINDER_HOURS = "reminder.hours";
    public static final String EVIDENCE_MAX_MB = "upload.evidence_max_mb";
    public static final String EVIDENCE_MAX_FILES = "upload.evidence_max_files";
    public static final String PUBLICATION_MAX_MB = "upload.publication_max_mb";
    public static final String THESIS_MAX_MB = "upload.thesis_max_mb";

    private final SystemSettingRepository repository;
    private final AuditService audit;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SettingsService(SystemSettingRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    public BigDecimal decimal(String key) {
        return new BigDecimal(raw(key));
    }

    public int integer(String key) {
        return Integer.parseInt(raw(key));
    }

    public List<Integer> integers(String key) {
        return Arrays.stream(raw(key).split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Integer::parseInt).toList();
    }

    public long megabytes(String key) {
        return integer(key) * 1024L * 1024L;
    }

    private String raw(String key) {
        return cache.computeIfAbsent(key, k -> repository.findBySettingKey(k)
                .map(SystemSetting::getValue)
                .orElseThrow(() -> new IllegalStateException("Missing setting " + k)));
    }

    @Transactional(readOnly = true)
    public List<SystemSetting> all() {
        return repository.findAll(Sort.by("settingKey"));
    }

    public record SettingChange(String key, String value, Long version) {}

    @Transactional
    public List<SystemSetting> update(List<SettingChange> changes) {
        for (SettingChange change : changes) {
            SystemSetting setting = repository.findBySettingKey(change.key())
                    .orElseThrow(() -> ApiException.notFound("Setting " + change.key()));
            if (change.version() != null && !Objects.equals(change.version(), setting.getVersion())) {
                throw new ApiException(ErrorCode.CONCURRENT_UPDATE,
                        "Setting " + change.key() + " was changed by someone else. Reload and try again.");
            }
            String value = change.value() == null ? "" : change.value().trim();
            validate(setting, value);
            if (value.equals(setting.getValue())) continue;
            String old = setting.getValue();
            setting.setValue(value);
            audit.record("SETTING_CHANGED", "SystemSetting", setting.getId(), null,
                    Map.of("key", setting.getSettingKey(), "value", old), Map.of("key", setting.getSettingKey(), "value", value));
        }
        cache.clear();
        return all();
    }

    private static void validate(SystemSetting setting, String value) {
        try {
            switch (setting.getValueType()) {
                case "DECIMAL" -> {
                    BigDecimal d = new BigDecimal(value);
                    if (d.signum() < 0 || (setting.getSettingKey().endsWith("_pct") && d.compareTo(BigDecimal.valueOf(100)) > 0)) {
                        throw new NumberFormatException();
                    }
                }
                case "INTEGER" -> {
                    if (Integer.parseInt(value) < 0) throw new NumberFormatException();
                }
                case "LIST" -> Arrays.stream(value.split(",")).map(String::trim).forEach(Integer::parseInt);
                default -> { }
            }
        } catch (NumberFormatException e) {
            throw ApiException.validation("Invalid value \"" + value + "\" for " + setting.getSettingKey() + ".");
        }
    }
}
