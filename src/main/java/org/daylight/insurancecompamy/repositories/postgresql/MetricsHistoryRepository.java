package org.daylight.insurancecompamy.repositories.postgresql;

import org.daylight.insurancecompamy.entity.Dataset;
import org.daylight.insurancecompamy.entity.MetricsHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MetricsHistoryRepository extends JpaRepository<MetricsHistory, Long> {
    List<MetricsHistory> findByDataset_IdOrderByCalculatedAtDesc(Long datasetId);
}
