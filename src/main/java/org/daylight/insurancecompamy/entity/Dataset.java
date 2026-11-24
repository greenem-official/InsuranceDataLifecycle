package org.daylight.insurancecompamy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "datasets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dataset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 100)
    private String category;

    @Column(name = "size_mb", precision = 12, scale = 2)
    private BigDecimal sizeMb;

    @Column(name = "record_count")
    private Long recordCount;

    @Column(name = "checksum_md5", length = 32)
    private String checksumMd5;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    @Column(name = "retention_days")
    private Integer retentionDays;
}
