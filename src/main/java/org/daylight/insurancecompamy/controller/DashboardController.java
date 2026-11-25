package org.daylight.insurancecompamy.controller;

import lombok.RequiredArgsConstructor;
import org.daylight.insurancecompamy.documents.AuditLog;
import org.daylight.insurancecompamy.documents.BackupMetadata;
import org.daylight.insurancecompamy.dto.*;
import org.daylight.insurancecompamy.entity.Dataset;
import org.daylight.insurancecompamy.entity.MetricsHistory;
import org.daylight.insurancecompamy.entity.RetentionRule;
import org.daylight.insurancecompamy.entity.User;
import org.daylight.insurancecompamy.repositories.mongodb.AuditLogRepository;
import org.daylight.insurancecompamy.repositories.mongodb.BackupMetadataRepository;
import org.daylight.insurancecompamy.repositories.postgresql.DatasetRepository;
import org.daylight.insurancecompamy.repositories.postgresql.MetricsHistoryRepository;
import org.daylight.insurancecompamy.repositories.postgresql.RetentionRuleRepository;
import org.daylight.insurancecompamy.repositories.postgresql.UserRepository;
import org.daylight.insurancecompamy.service.BackupService;
import org.daylight.insurancecompamy.service.CsvInventoryService;
import org.daylight.insurancecompamy.service.EcologyService;
import org.daylight.insurancecompamy.service.StorageCostService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Controller
@RequiredArgsConstructor
public class DashboardController {
    private final UserRepository userRepository;
    private final DatasetRepository datasetRepository;
    private final MetricsHistoryRepository metricsHistoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final BackupMetadataRepository backupMetadataRepository;
    private final EcologyService ecologyService;
    private final CsvInventoryService csvInventoryService;
    private final RetentionRuleRepository retentionRuleRepository;
    private final BackupService backupService;
    private final StorageCostService storageCostService;

    @GetMapping("/")
    public String dashboard(@AuthenticationPrincipal UserDetails principal,
                            @RequestParam(required = false) String q,
                            @RequestParam(required = false) String category,
                            @RequestParam(defaultValue = "id") String sortBy,
                            @RequestParam(defaultValue = "desc") String dir,
                            Model model) {
        User currentUser = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        Long ownerFilterId = (currentUser.getRole() == Role.OWNER) ? currentUser.getId() : null;

        List<Dataset> datasets = datasetRepository.searchDatasets(ownerFilterId, q, category);
        List<MetricsHistory> history = metricsHistoryRepository.findAll();

        // Последний рассчитанный индекс для каждого набора данных
        Map<Long, Float> latestIndexByDataset = new HashMap<>();
        for (MetricsHistory mh : history) {
            if (mh.getDataset() != null && mh.getIntegralIndex() != null) {
                latestIndexByDataset.merge(mh.getDataset().getId(), mh.getIntegralIndex(), (oldV, newV) -> newV);
            }
        }

        Comparator<Dataset> comparator = switch (sortBy) {
            case "name" -> Comparator.comparing(d -> Optional.ofNullable(d.getName()).orElse(""), String.CASE_INSENSITIVE_ORDER);
            case "size" -> Comparator.comparing(d -> Optional.ofNullable(d.getSizeMb()).orElse(BigDecimal.ZERO));
            case "records" -> Comparator.comparing(d -> Optional.ofNullable(d.getRecordCount()).orElse(0L));
            case "lastAccess" -> Comparator.comparing(d -> Optional.ofNullable(d.getLastAccessedAt()).orElse(Instant.EPOCH));
            case "index" -> Comparator.comparing(d -> latestIndexByDataset.getOrDefault(d.getId(), -1f));
            default -> Comparator.comparing(d -> Optional.ofNullable(d.getId()).orElse(0L));
        };

        if ("desc".equalsIgnoreCase(dir)) {
            comparator = comparator.reversed();
        }
        datasets = datasets.stream().sorted(comparator).toList();

        List<AuditLog> logs = auditLogRepository.findAllByOrderByTimestampDesc();
        List<BackupMetadata> backups = backupMetadataRepository.findAllByOrderByCreatedAtDesc();
        StorageCostReportDto costReport = storageCostService.calculate(datasets);

        model.addAttribute("user", currentUser);
        model.addAttribute("datasets", datasets);
        model.addAttribute("latestIndexByDataset", latestIndexByDataset);
        model.addAttribute("history", history);
        model.addAttribute("logs", logs.stream().limit(15).toList());
        model.addAttribute("backups", backups.stream().limit(5).toList());
        model.addAttribute("q", q);
        model.addAttribute("category", category);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("dir", dir);
        model.addAttribute("retentionRules", retentionRuleRepository.findAll());
        model.addAttribute("costReport", costReport);

        return "dashboard";
    }

    @PostMapping("/datasets/create")
    public String createDataset(@AuthenticationPrincipal UserDetails principal,
                                @RequestParam String name,
                                @RequestParam(required = false) String category,
                                @RequestParam(required = false) BigDecimal sizeMb,
                                @RequestParam(required = false) Long recordCount,
                                @RequestParam(required = false) String checksumMd5,
                                @RequestParam(defaultValue = "0") int daysSinceLastAccess,
                                @RequestParam(defaultValue = "1825") Integer retentionDays,
                                @RequestParam(value = "file", required = false) MultipartFile file,
                                RedirectAttributes redirectAttributes) {
        User currentUser = userRepository.findByEmail(principal.getUsername()).orElseThrow();

        BigDecimal finalSizeMb = sizeMb;
        Long finalRecordCount = recordCount;
        String finalMd5 = checksumMd5;
        Double csvDupRatio = null;
        Double csvMissRatio = null;

        if (file != null && !file.isEmpty()) {
            CsvInventoryResult inv = csvInventoryService.analyze(file);
            finalSizeMb = inv.getSizeMb();
            finalRecordCount = inv.getRecordCount();
            if (finalMd5 == null || finalMd5.isBlank()) {
                finalMd5 = inv.getChecksumMd5();
            }
            csvDupRatio = inv.getDuplicateRowRatio();
            csvMissRatio = inv.getMissingFieldRatio();
        }

        Dataset saved = datasetRepository.save(Dataset.builder()
                .owner(currentUser)
                .name(name)
                .category(category)
                .sizeMb(finalSizeMb)
                .recordCount(finalRecordCount)
                .checksumMd5(finalMd5)
                .lastAccessedAt(Instant.now().minus(daysSinceLastAccess, ChronoUnit.DAYS))
                .retentionDays(retentionDays)
                .build());

        auditLogRepository.save(AuditLog.builder()
                .timestamp(Instant.now())
                .userId(currentUser.getId())
                .action(AuditAction.CREATE)
                .details(Map.of(
                        "datasetId", saved.getId(),
                        "name", saved.getName(),
                        "fromCsv", file != null && !file.isEmpty()
                ))
                .build());

        EcologyReportDto report = ecologyService.evaluateAndSave(
                saved.getId(), currentUser.getId(), csvDupRatio, csvMissRatio);
        redirectAttributes.addFlashAttribute("lastReport", report);
        return "redirect:/";
    }

    @PostMapping("/datasets/{id}/evaluate")
    public String evaluateDataset(@PathVariable Long id,
                                  @AuthenticationPrincipal UserDetails principal,
                                  RedirectAttributes redirectAttributes) {
        User currentUser = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        EcologyReportDto report = ecologyService.evaluateAndSave(id, currentUser.getId());
        redirectAttributes.addFlashAttribute("lastReport", report);
        return "redirect:/";
    }

    @PostMapping("/datasets/{id}/delete")
    public String deleteDataset(@PathVariable Long id,
                                @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        if (currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.STEWARD) {
            datasetRepository.deleteById(id);
            auditLogRepository.save(AuditLog.builder()
                    .timestamp(Instant.now())
                    .userId(currentUser.getId())
                    .action(AuditAction.DELETE)
                    .details(Map.of("deletedDatasetId", id))
                    .build());
        }
        return "redirect:/";
    }

    @PostMapping("/retention-rules")
    public String createRetentionRule(@AuthenticationPrincipal UserDetails principal,
                                      @RequestParam String category,
                                      @RequestParam Integer daysToKeep,
                                      @RequestParam RetentionAction action) {
        User currentUser = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        if (currentUser.getRole() != Role.STEWARD && currentUser.getRole() != Role.ADMIN) {
            return "redirect:/";
        }

        RetentionRule rule = retentionRuleRepository.findFirstByCategoryIgnoreCase(category.trim())
                .orElseGet(RetentionRule::new);
        rule.setCategory(category.trim());
        rule.setDaysToKeep(daysToKeep);
        rule.setAction(action);
        retentionRuleRepository.save(rule);

        auditLogRepository.save(AuditLog.builder()
                .timestamp(Instant.now())
                .userId(currentUser.getId())
                .action(AuditAction.EDIT)
                .details(Map.of(
                        "event", "RETENTION_RULE_SAVED",
                        "category", rule.getCategory(),
                        "daysToKeep", rule.getDaysToKeep(),
                        "action", rule.getAction().name()
                ))
                .build());

        return "redirect:/";
    }

    // 1. OWNER запрашивает удаление или архивацию своего набора
    @PostMapping("/datasets/{id}/request-removal")
    public String requestRemoval(@PathVariable Long id,
                                 @RequestParam(defaultValue = "DELETE") RetentionAction action,
                                 @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        Dataset ds = datasetRepository.findById(id).orElseThrow();

        boolean isOwner = ds.getOwner() != null && ds.getOwner().getId().equals(currentUser.getId());
        if (currentUser.getRole() == Role.OWNER && isOwner) {
            ds.setStatus(action == RetentionAction.ARCHIVE
                    ? LifecycleStatus.ARCHIVE_REQUESTED
                    : LifecycleStatus.DELETE_REQUESTED);
            datasetRepository.save(ds);

            auditLogRepository.save(AuditLog.builder()
                    .timestamp(Instant.now())
                    .userId(currentUser.getId())
                    .action(AuditAction.EDIT)
                    .details(Map.of(
                            "event", "REMOVAL_REQUESTED",
                            "datasetId", ds.getId(),
                            "requestedAction", action.name()
                    ))
                    .build());
        }
        return "redirect:/";
    }

    // 2. STEWARD проверяет правило хранения и одобряет заявку
    @PostMapping("/datasets/{id}/approve-removal")
    public String approveRemoval(@PathVariable Long id,
                                 @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        if (currentUser.getRole() != Role.STEWARD && currentUser.getRole() != Role.ADMIN) {
            return "redirect:/";
        }

        Dataset ds = datasetRepository.findById(id).orElseThrow();
        if (ds.getStatus() == LifecycleStatus.ARCHIVE_REQUESTED) {
            ds.setStatus(LifecycleStatus.ARCHIVED);
        } else if (ds.getStatus() == LifecycleStatus.DELETE_REQUESTED) {
            ds.setStatus(LifecycleStatus.APPROVED_FOR_DELETE);
        }
        datasetRepository.save(ds);

        auditLogRepository.save(AuditLog.builder()
                .timestamp(Instant.now())
                .userId(currentUser.getId())
                .action(AuditAction.EDIT)
                .details(Map.of(
                        "event", "REMOVAL_APPROVED_BY_STEWARD",
                        "datasetId", ds.getId(),
                        "newStatus", ds.getStatus().name()
                ))
                .build());

        return "redirect:/";
    }

    // 3. ADMIN исполняет подтверждённое удаление
    @PostMapping("/datasets/{id}/execute-delete")
    public String executeDelete(@PathVariable Long id,
                                @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        if (currentUser.getRole() != Role.ADMIN) {
            return "redirect:/";
        }

        Dataset ds = datasetRepository.findById(id).orElseThrow();
        if (ds.getStatus() == LifecycleStatus.APPROVED_FOR_DELETE) {
            datasetRepository.delete(ds);
            auditLogRepository.save(AuditLog.builder()
                    .timestamp(Instant.now())
                    .userId(currentUser.getId())
                    .action(AuditAction.DELETE)
                    .details(Map.of(
                            "event", "PHYSICAL_DELETE_EXECUTED",
                            "deletedDatasetId", id,
                            "name", ds.getName()
                    ))
                    .build());
        }
        return "redirect:/";
    }

    @PostMapping("/admin/backup")
    public String createBackup(@AuthenticationPrincipal UserDetails principal,
                               @RequestParam(defaultValue = "FULL") BackupType type,
                               RedirectAttributes redirectAttributes) {
        User currentUser = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        if (currentUser.getRole() != Role.ADMIN) {
            return "redirect:/";
        }

        BackupMetadata backup = backupService.createBackup(type, currentUser.getId());
        redirectAttributes.addFlashAttribute("statusMessage",
                "Создана резервная копия (" + backup.getType() + "), объектов: "
                        + backup.getItemCount() + ", SHA-256: " + backup.getChecksum().substring(0, 12) + "...");
        return "redirect:/";
    }

    @PostMapping("/admin/backup/{id}/verify")
    public String verifyBackup(@PathVariable String id,
                               @AuthenticationPrincipal UserDetails principal,
                               RedirectAttributes redirectAttributes) {
        User currentUser = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        if (currentUser.getRole() != Role.ADMIN) {
            return "redirect:/";
        }

        boolean valid = backupService.verifyIntegrity(id);
        redirectAttributes.addFlashAttribute("statusMessage",
                valid ? "Проверка целостности пройдена: контрольная сумма SHA-256 совпадает."
                        : "Внимание: контрольная сумма копии не совпадает!");
        return "redirect:/";
    }

    @PostMapping("/admin/backup/{id}/restore")
    public String restoreBackup(@PathVariable String id,
                                @AuthenticationPrincipal UserDetails principal,
                                RedirectAttributes redirectAttributes) {
        User currentUser = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        if (currentUser.getRole() != Role.ADMIN) {
            return "redirect:/";
        }

        try {
            int restored = backupService.restoreFromBackup(id, currentUser.getId());
            redirectAttributes.addFlashAttribute("statusMessage",
                    "Восстановление выполнено успешно. Восстановлено наборов данных: " + restored);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/";
    }
}
