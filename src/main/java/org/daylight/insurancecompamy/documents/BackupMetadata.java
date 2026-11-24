package org.daylight.insurancecompamy.documents;

import jakarta.persistence.Id;
import lombok.*;
import org.daylight.insurancecompamy.dto.AuditAction;
import org.daylight.insurancecompamy.dto.BackupType;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "backup_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupMetadata {
    @Id
    private String id;

    private Instant createdAt;

    private BackupType type;

    private Long sizeBytes;

    private String checksum;
}