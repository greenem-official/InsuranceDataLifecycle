package org.daylight.insurancecompany.repositories.mongodb;

import org.daylight.insurancecompany.documents.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    List<AuditLog> findAllByOrderByTimestampDesc();
}
