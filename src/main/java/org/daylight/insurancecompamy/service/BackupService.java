package org.daylight.insurancecompamy.service;

import lombok.RequiredArgsConstructor;
import org.daylight.insurancecompamy.documents.AuditLog;
import org.daylight.insurancecompamy.documents.BackupMetadata;
import org.daylight.insurancecompamy.dto.AuditAction;
import org.daylight.insurancecompamy.dto.BackupType;
import org.daylight.insurancecompamy.dto.LifecycleStatus;
import org.daylight.insurancecompamy.entity.Dataset;
import org.daylight.insurancecompamy.entity.User;
import org.daylight.insurancecompamy.repositories.mongodb.AuditLogRepository;
import org.daylight.insurancecompamy.repositories.mongodb.BackupMetadataRepository;
import org.daylight.insurancecompamy.repositories.postgresql.DatasetRepository;
import org.daylight.insurancecompamy.repositories.postgresql.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BackupService {

    private final DatasetRepository datasetRepository;
    private final UserRepository userRepository;
    private final BackupMetadataRepository backupMetadataRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BackupMetadata createBackup(BackupType type, Long adminUserId) {
        List<Dataset> allDatasets = datasetRepository.findAll();
        List<BackupMetadata> existingBackups = backupMetadataRepository.findAllByOrderByCreatedAtDesc();

        List<Dataset> targetDatasets;
        if (type == BackupType.INCREMENTAL && !existingBackups.isEmpty()) {
            Instant lastBackupTime = existingBackups.get(0).getCreatedAt();
            // Берём наборы, обращение/изменение по которым произошло после последнего бэкапа
            targetDatasets = allDatasets.stream()
                    .filter(d -> d.getLastAccessedAt() != null && d.getLastAccessedAt().isAfter(lastBackupTime))
                    .toList();
            if (targetDatasets.isEmpty()) {
                targetDatasets = allDatasets; // если новых нет — фиксируем текущую дельту
            }
        } else {
            type = BackupType.FULL;
            targetDatasets = allDatasets;
        }

        List<BackupMetadata.DatasetSnapshotItem> snapshot = targetDatasets.stream()
                .map(this::toSnapshotMap)
                .toList();

        byte[] serialized = serializeSnapshot(snapshot);
        String sha256 = calculateSha256(serialized);

        BackupMetadata saved = backupMetadataRepository.save(BackupMetadata.builder()
                .createdAt(Instant.now())
                .type(type)
                .sizeBytes((long) serialized.length)
                .checksum(sha256)
                .itemCount(snapshot.size())
                .snapshot(snapshot)
                .build());

        auditLogRepository.save(AuditLog.builder()
                .timestamp(Instant.now())
                .userId(adminUserId)
                .action(AuditAction.BACKUP)
                .details(Map.of(
                        "event", "BACKUP_CREATED",
                        "backupId", saved.getId(),
                        "type", type.name(),
                        "itemCount", snapshot.size(),
                        "checksum", sha256
                ))
                .build());

        return saved;
    }

    public boolean verifyIntegrity(String backupId) {
        BackupMetadata backup = backupMetadataRepository.findById(backupId)
                .orElseThrow(() -> new IllegalArgumentException("Резервная копия не найдена: " + backupId));
        if (backup.getSnapshot() == null || backup.getChecksum() == null) {
            return false;
        }
        String actualChecksum = calculateSha256(serializeSnapshot(backup.getSnapshot()));
        return actualChecksum.equalsIgnoreCase(backup.getChecksum());
    }

    @Transactional
    public int restoreFromBackup(String backupId, Long adminUserId) {
        BackupMetadata backup = backupMetadataRepository.findById(backupId)
                .orElseThrow(() -> new IllegalArgumentException("Резервная копия не найдена: " + backupId));

        if (!verifyIntegrity(backupId)) {
            throw new IllegalStateException("Ошибка проверки контрольной суммы SHA-256! Резервная копия повреждена.");
        }

        List<BackupMetadata.DatasetSnapshotItem> itemsToRestore = new ArrayList<>();
        // Если копия инкрементальная — сначала подтягиваем базовый FULL бэкап, затем накладываем инкремент
        if (backup.getType() == BackupType.INCREMENTAL) {
            backupMetadataRepository.findAllByOrderByCreatedAtDesc().stream()
                    .filter(b -> b.getType() == BackupType.FULL && !b.getCreatedAt().isAfter(backup.getCreatedAt()))
                    .findFirst()
                    .ifPresent(full -> {
                        if (full.getSnapshot() != null) {
                            itemsToRestore.addAll(full.getSnapshot());
                        }
                    });
        }
        if (backup.getSnapshot() != null) {
            itemsToRestore.addAll(backup.getSnapshot());
        }

        User fallbackAdmin = userRepository.findById(adminUserId).orElseThrow();
        Set<String> existingNames = new HashSet<>();
        datasetRepository.findAll().forEach(d -> existingNames.add(d.getName()));

        int restoredCount = 0;
        for (BackupMetadata.DatasetSnapshotItem row : itemsToRestore) {
            String name = Objects.toString(row.getName(), "Восстановленный набор");
            if (existingNames.contains(name)) {
                continue; // не дублируем уже существующие в БД наборы
            }

            String ownerEmail = Objects.toString(row.getOwnerEmail(), "");
            User owner = userRepository.findByEmail(ownerEmail).orElse(fallbackAdmin);

            BigDecimal sizeMb = row.getSizeMb() != null
                    ? new BigDecimal(row.getSizeMb().toString())
                    : BigDecimal.ZERO;
            Long recordCount = row.getRecordCount() != null
                    ? Long.valueOf(row.getRecordCount().toString())
                    : 0L;
            Integer retentionDays = row.getRetentionDays() != null
                    ? Integer.valueOf(row.getRetentionDays().toString())
                    : 1825;

            datasetRepository.save(Dataset.builder()
                    .owner(owner)
                    .name(name)
                    .category(Objects.toString(row.getCategory(), null))
                    .sizeMb(sizeMb)
                    .recordCount(recordCount)
                    .checksumMd5(Objects.toString(row.getChecksumMd5(), null))
                    .lastAccessedAt(Instant.now())
                    .retentionDays(retentionDays)
                    .status(LifecycleStatus.ACTIVE)
                    .build());

            existingNames.add(name);
            restoredCount++;
        }

        auditLogRepository.save(AuditLog.builder()
                .timestamp(Instant.now())
                .userId(adminUserId)
                .action(AuditAction.BACKUP)
                .details(Map.of(
                        "event", "BACKUP_RESTORED",
                        "backupId", backup.getId(),
                        "type", backup.getType().name(),
                        "restoredDatasets", restoredCount
                ))
                .build());

        return restoredCount;
    }

    private BackupMetadata.DatasetSnapshotItem toSnapshotMap(Dataset d) {
        BackupMetadata.DatasetSnapshotItem item = new BackupMetadata.DatasetSnapshotItem();
        item.setName(d.getName());
        item.setCategory(d.getCategory() != null ? d.getCategory() : "");
        item.setSizeMb(d.getSizeMb() != null ? d.getSizeMb().toPlainString() : "0");
        item.setRecordCount(d.getRecordCount() != null ? d.getRecordCount() : 0L);
        item.setChecksumMd5(d.getChecksumMd5() != null ? d.getChecksumMd5() : "");
        item.setRetentionDays(d.getRetentionDays() != null ? d.getRetentionDays() : 1825);
        item.setOwnerEmail(d.getOwner() != null ? d.getOwner().getEmail() : "");
        return item;
    }

    private byte[] serializeSnapshot(List<BackupMetadata.DatasetSnapshotItem> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot).getBytes(StandardCharsets.UTF_8);
        } catch (JacksonException e) {
            throw new IllegalStateException("Ошибка сериализации снимка резервной копии", e);
        }
    }

    private String calculateSha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Алгоритм SHA-256 недоступен", e);
        }
    }
}
