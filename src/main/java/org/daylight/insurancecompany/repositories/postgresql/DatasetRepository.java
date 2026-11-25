package org.daylight.insurancecompany.repositories.postgresql;

import org.daylight.insurancecompany.entity.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {
    List<Dataset> findByChecksumMd5(String checksumMd5);

    List<Dataset> findByOwner_Id(Long ownerId);

    @Query("""
            SELECT d FROM Dataset d
            WHERE (:ownerId IS NULL OR d.owner.id = :ownerId)
              AND (:query IS NULL OR :query = ''
                   OR LOWER(d.name) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(COALESCE(d.category, '')) LIKE LOWER(CONCAT('%', :query, '%')))
              AND (:category IS NULL OR :category = '' OR LOWER(d.category) = LOWER(:category))
            """)
    List<Dataset> searchDatasets(@Param("ownerId") Long ownerId,
                                 @Param("query") String query,
                                 @Param("category") String category);
}
