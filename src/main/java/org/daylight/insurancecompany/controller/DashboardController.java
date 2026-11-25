package org.daylight.insurancecompany.controller;

import lombok.RequiredArgsConstructor;
import org.daylight.insurancecompany.documents.AuditLog;
import org.daylight.insurancecompany.documents.BackupMetadata;
import org.daylight.insurancecompany.dto.*;
import org.daylight.insurancecompany.entity.Dataset;
import org.daylight.insurancecompany.entity.MetricsHistory;
import org.daylight.insurancecompany.entity.RetentionRule;
import org.daylight.insurancecompany.entity.User;
import org.daylight.insurancecompany.repositories.mongodb.AuditLogRepository;
import org.daylight.insurancecompany.repositories.mongodb.BackupMetadataRepository;
import org.daylight.insurancecompany.repositories.postgresql.DatasetRepository;
import org.daylight.insurancecompany.repositories.postgresql.MetricsHistoryRepository;
import org.daylight.insurancecompany.repositories.postgresql.RetentionRuleRepository;
import org.daylight.insurancecompany.repositories.postgresql.UserRepository;
import org.daylight.insurancecompany.service.BackupService;
import org.daylight.insurancecompany.service.CsvInventoryService;
import org.daylight.insurancecompany.service.EcologyService;
import org.daylight.insurancecompany.service.StorageCostService;
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

        List<RetentionRule> retentionRules = retentionRuleRepository.findAll();
        Map<String, Integer> retentionByCategory = new HashMap<>();
        for (RetentionRule r : retentionRules) {
            if (r.getCategory() != null && r.getDaysToKeep() != null) {
                retentionByCategory.put(r.getCategory().trim().toLowerCase(), r.getDaysToKeep());
            }
        }

        Set<Long> expiredDatasetIds = new HashSet<>();
        Instant now = Instant.now();
        for (Dataset ds : datasets) {
            if (ds.getCategory() != null && ds.getLastAccessedAt() != null) {
                Integer limit = retentionByCategory.get(ds.getCategory().trim().toLowerCase());
                if (limit != null && ChronoUnit.DAYS.between(ds.getLastAccessedAt(), now) > limit) {
                    expiredDatasetIds.add(ds.getId());
                }
            }
        }

        model.addAttribute("retentionRules", retentionRules);
        model.addAttribute("expiredDatasetIds", expiredDatasetIds);
        if (currentUser.getRole() == Role.ADMIN) {
            model.addAttribute("allUsers", userRepository.findAll());
        }

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

    @GetMapping("/login")
    public String loginPage() {
        return "login";
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

    // Смена роли пользователя администратором
    @PostMapping("/admin/users/{id}/role")
    public String updateUserRole(@PathVariable Long id,
                                 @RequestParam Role role,
                                 @AuthenticationPrincipal UserDetails principal,
                                 RedirectAttributes redirectAttributes) {
        User currentUser = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        if (currentUser.getRole() != Role.ADMIN) {
            return "redirect:/";
        }

        User targetUser = userRepository.findById(id).orElseThrow();
        Role oldRole = targetUser.getRole();
        targetUser.setRole(role);
        userRepository.save(targetUser);

        auditLogRepository.save(AuditLog.builder()
                .timestamp(Instant.now())
                .userId(currentUser.getId())
                .action(AuditAction.EDIT)
                .details(Map.of(
                        "event", "USER_ROLE_CHANGED",
                        "targetUserEmail", targetUser.getEmail(),
                        "oldRole", oldRole.name(),
                        "newRole", role.name()
                ))
                .build());

        redirectAttributes.addFlashAttribute("statusMessage",
                "Роль пользователя " + targetUser.getFullName() + " изменена на " + role.name());
        return "redirect:/";
    }

    // Выгрузка сводного отчёта об экологичности данных (для AUDITOR и руководства)
    @GetMapping(value = "/reports/ecology.csv", produces = "text/csv; charset=UTF-8")
    @ResponseBody
    public org.springframework.http.ResponseEntity<byte[]> exportEcologyReport(
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        Long ownerFilterId = (currentUser.getRole() == Role.OWNER) ? currentUser.getId() : null;

        List<Dataset> datasets = datasetRepository.searchDatasets(ownerFilterId, null, null);
        List<MetricsHistory> history = metricsHistoryRepository.findAll();

        Map<Long, MetricsHistory> latestMetrics = new HashMap<>();
        for (MetricsHistory mh : history) {
            if (mh.getDataset() != null) {
                MetricsHistory prev = latestMetrics.get(mh.getDataset().getId());
                if (prev == null || (mh.getCalculatedAt() != null && prev.getCalculatedAt() != null
                        && mh.getCalculatedAt().isAfter(prev.getCalculatedAt()))) {
                    latestMetrics.put(mh.getDataset().getId(), mh);
                }
            }
        }

        StringBuilder csv = new StringBuilder();
        // BOM для корректного открытия кириллицы в Excel
        csv.append('\uFEFF');
        csv.append("ID;Название;Категория;Размер_МБ;Записей;MD5;Статус;Владелец;Индекс_I;Дата_расчёта\n");

        for (Dataset ds : datasets) {
            MetricsHistory mh = latestMetrics.get(ds.getId());
            csv.append(ds.getId()).append(';')
                    .append(escapeCsv(ds.getName())).append(';')
                    .append(escapeCsv(ds.getCategory())).append(';')
                    .append(ds.getSizeMb() != null ? ds.getSizeMb().toPlainString() : "0").append(';')
                    .append(ds.getRecordCount() != null ? ds.getRecordCount() : 0).append(';')
                    .append(escapeCsv(ds.getChecksumMd5())).append(';')
                    .append(ds.getStatus() != null ? ds.getStatus().name() : "ACTIVE").append(';')
                    .append(ds.getOwner() != null ? escapeCsv(ds.getOwner().getFullName()) : "").append(';')
                    .append(mh != null && mh.getIntegralIndex() != null ? mh.getIntegralIndex() : "").append(';')
                    .append(mh != null && mh.getCalculatedAt() != null ? mh.getCalculatedAt().toString() : "").append('\n');
        }

        auditLogRepository.save(AuditLog.builder()
                .timestamp(Instant.now())
                .userId(currentUser.getId())
                .action(AuditAction.VIEW)
                .details(Map.of("event", "ECOLOGY_CSV_REPORT_EXPORTED", "rows", datasets.size()))
                .build());

        byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"insurance_ecology_report.csv\"")
                .body(bytes);
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        return "\"" + val.replace("\"", "\"\"") + "\"";
    }
}
