package org.daylight.insurancecompany.entity;

import jakarta.persistence.*;
import lombok.*;
import org.daylight.insurancecompany.dto.RetentionAction;

@Entity
@Table(name = "retention_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetentionRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(name = "days_to_keep", nullable = false)
    private Integer daysToKeep;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RetentionAction action;
}
