package org.daylight.insurancecompamy.controller;

import lombok.RequiredArgsConstructor;
import org.daylight.insurancecompamy.documents.AuditLog;
import org.daylight.insurancecompamy.documents.BackupMetadata;
import org.daylight.insurancecompamy.dto.AuditAction;
import org.daylight.insurancecompamy.dto.BackupType;
import org.daylight.insurancecompamy.dto.EcologyReportDto;
import org.daylight.insurancecompamy.dto.Role;
import org.daylight.insurancecompamy.entity.Dataset;
import org.daylight.insurancecompamy.entity.MetricsHistory;
import org.daylight.insurancecompamy.entity.User;
import org.daylight.insurancecompamy.repositories.mongodb.AuditLogRepository;
import org.daylight.insurancecompamy.repositories.mongodb.BackupMetadataRepository;
import org.daylight.insurancecompamy.repositories.postgresql.DatasetRepository;
import org.daylight.insurancecompamy.repositories.postgresql.MetricsHistoryRepository;
import org.daylight.insurancecompamy.repositories.postgresql.UserRepository;
import org.daylight.insurancecompamy.service.EcologyService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class DashboardController {
    private final UserRepository userRepository;
    private final DatasetRepository datasetRepository;
    private final MetricsHistoryRepository metricsHistoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final BackupMetadataRepository backupMetadataRepository;
    private final EcologyService ecologyService;

    @GetMapping("/")
    public String dashboard(@AuthenticationPrincipal UserDetails principal, Model model) {
        User currentUser = userRepository.findByEmail(principal.getUsername()).orElseThrow();

        // OWNER видит только свои данные, остальные роли — все наборы компании
        List<Dataset> datasets = (currentUser.getRole() == Role.OWNER)
                ? datasetRepository.findByOwner_Id(currentUser.getId())
                : datasetRepository.findAll();

        List<MetricsHistory> history = metricsHistoryRepository.findAll();
        List<AuditLog> logs = auditLogRepository.findAllByOrderByTimestampDesc();
        List<BackupMetadata> backups = backupMetadataRepository.findAllByOrderByCreatedAtDesc();

        model.addAttribute("user", currentUser);
        model.addAttribute("datasets", datasets);
        model.addAttribute("history", history);
        model.addAttribute("logs", logs.stream().limit(10).toList());
        model.addAttribute("backups", backups.stream().limit(5).toList());

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
                                RedirectAttributes redirectAttributes) {
        User currentUser = userRepository.findByEmail(principal.getUsername()).orElseThrow();

        Dataset saved = datasetRepository.save(Dataset.builder()
                .owner(currentUser)
                .name(name)
                .category(category)
                .sizeMb(sizeMb)
                .recordCount(recordCount)
                .checksumMd5(checksumMd5)
                .lastAccessedAt(Instant.now().minus(daysSinceLastAccess, ChronoUnit.DAYS))
                .retentionDays(retentionDays)
                .build());

        auditLogRepository.save(AuditLog.builder()
                .timestamp(Instant.now())
                .userId(currentUser.getId())
                .action(AuditAction.CREATE)
                .details(Map.of("datasetId", saved.getId(), "name", saved.getName()))
                .build());

        EcologyReportDto report = ecologyService.evaluateAndSave(saved.getId(), currentUser.getId());
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

    @PostMapping("/admin/backup")
    public String createBackup(@AuthenticationPrincipal UserDetails principal) {
        User currentUser = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        long totalBytes = datasetRepository.findAll().stream()
                .mapToLong(d -> d.getSizeMb() != null ? d.getSizeMb().longValue() * 1024 * 1024 : 0L)
                .sum();

        backupMetadataRepository.save(BackupMetadata.builder()
                .createdAt(Instant.now())
                .type(BackupType.FULL)
                .sizeBytes(totalBytes)
                .checksum(UUID.randomUUID().toString().replace("-", ""))
                .build());

        auditLogRepository.save(AuditLog.builder()
                .timestamp(Instant.now())
                .userId(currentUser.getId())
                .action(AuditAction.BACKUP)
                .details(Map.of("type", "FULL", "sizeBytes", totalBytes))
                .build());

        return "redirect:/";
    }
}
