package org.daylight.insurancecompany.repositories.mongodb;

import org.daylight.insurancecompany.documents.BackupMetadata;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BackupMetadataRepository extends MongoRepository<BackupMetadata, String> {
    List<BackupMetadata> findAllByOrderByCreatedAtDesc();
}
