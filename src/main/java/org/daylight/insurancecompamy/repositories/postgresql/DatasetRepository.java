package org.daylight.insurancecompamy.repositories.postgresql;

import org.daylight.insurancecompamy.entity.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {
    List<Dataset> findByChecksumMd5(String checksumMd5);
    List<Dataset> findByOwner_Id(Long ownerId);
}
