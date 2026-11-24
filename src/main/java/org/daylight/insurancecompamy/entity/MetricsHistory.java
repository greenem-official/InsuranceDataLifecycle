package org.daylight.insurancecompamy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.daylight.insurancecompamy.dto.Role;

import java.time.Instant;

@Entity
@Table(name = "metrics_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricsHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    @Column(name = "integral_index")
    private Float integralIndex;

    @Builder.Default
    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt = Instant.now();
}
