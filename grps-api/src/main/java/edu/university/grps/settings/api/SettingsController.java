package edu.university.grps.settings.api;

import edu.university.grps.settings.domain.SystemSetting;
import edu.university.grps.settings.service.SettingsService;
import edu.university.grps.settings.service.SettingsService.SettingChange;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
public class SettingsController {

    private final SettingsService service;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    public record SettingDto(UUID id, String key, String value, String valueType, String description, Long version,
                             Instant updatedAt) {
        static SettingDto of(SystemSetting s) {
            return new SettingDto(s.getId(), s.getSettingKey(), s.getValue(), s.getValueType(), s.getDescription(),
                    s.getVersion(), s.getUpdatedAt());
        }
    }

    public record UpdateRequest(@NotEmpty List<SettingChange> settings) {}

    @GetMapping
    public List<SettingDto> list() {
        return service.all().stream().map(SettingDto::of).toList();
    }

    @PutMapping
    public List<SettingDto> update(@RequestBody @jakarta.validation.Valid UpdateRequest request) {
        return service.update(request.settings()).stream().map(SettingDto::of).toList();
    }
}
