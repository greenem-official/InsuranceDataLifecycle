package org.daylight.insurancecompamy.documents;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.daylight.insurancecompamy.dto.BackupType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "backup_metadata")
public class BackupMetadata {

    @Id
    private String id;

    private Instant createdAt;

    private BackupType type;

    private Long sizeBytes;

    private String checksum;

    private Integer itemCount;

    private List<DatasetSnapshotItem> snapshot;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatasetSnapshotItem {
        private String name;
        private String category;
        private String sizeMb;
        private Long recordCount;
        private String checksumMd5;
        private Integer retentionDays;
        private String ownerEmail;
    }
}