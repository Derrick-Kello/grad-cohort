package edu.university.grps.programme.domain;

import edu.university.grps.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "venue")
public class Venue extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    private Integer capacity;

    @Column(nullable = false)
    private boolean active = true;
}
