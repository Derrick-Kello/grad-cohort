package edu.university.grps.thesis.domain;

import edu.university.grps.common.BaseEntity;
import edu.university.grps.programme.domain.Programme;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A configurable thesis format rule per programme (FR-THS-05). */
@Getter
@Setter
@Entity
@Table(name = "format_rule")
public class FormatRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_id")
    private Programme programme;

    @Column(name = "rule_code", nullable = false)
    private String ruleCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    @Column(nullable = false)
    private boolean active = true;
}
