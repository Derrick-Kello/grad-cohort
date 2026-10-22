package edu.university.grps.settings.domain;

import edu.university.grps.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "system_setting")
public class SystemSetting extends BaseEntity {

    @Column(name = "key", nullable = false, unique = true)
    private String settingKey;

    @Column(nullable = false)
    private String value;

    @Column(name = "value_type", nullable = false)
    private String valueType;

    private String description;
}
