package org.daylight.insurancecompamy.repositories.mongodb;

import org.daylight.insurancecompamy.documents.AuditLog;
import org.daylight.insurancecompamy.documents.BackupMetadata;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BackupMetadataRepository extends MongoRepository<BackupMetadata, String> {
    List<BackupMetadata> findAllByOrderByCreatedAtDesc();
}
