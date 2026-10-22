package edu.university.grps.milestone.domain;

import edu.university.grps.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "panel_member")
public class PanelMember extends BaseEntity {

    public enum Role { CHAIR, MEMBER }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id")
    private PresentationAttempt attempt;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "external_name")
    private String externalName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
}
