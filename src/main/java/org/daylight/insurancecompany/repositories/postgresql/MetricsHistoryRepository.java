package org.daylight.insurancecompany.repositories.postgresql;

import org.daylight.insurancecompany.entity.MetricsHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MetricsHistoryRepository extends JpaRepository<MetricsHistory, Long> {
    List<MetricsHistory> findByDataset_IdOrderByCalculatedAtDesc(Long datasetId);
}
