package org.daylight.insurancecompany.service;

import lombok.RequiredArgsConstructor;
import org.daylight.insurancecompany.config.EcologyProperties;
import org.daylight.insurancecompany.documents.AuditLog;
import org.daylight.insurancecompany.dto.AuditAction;
import org.daylight.insurancecompany.dto.EcologyReportDto;
import org.daylight.insurancecompany.entity.Dataset;
import org.daylight.insurancecompany.entity.MetricsHistory;
import org.daylight.insurancecompany.repositories.mongodb.AuditLogRepository;
import org.daylight.insurancecompany.repositories.postgresql.DatasetRepository;
import org.daylight.insurancecompany.repositories.postgresql.MetricsHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EcologyService {
    private final DatasetRepository datasetRepository;
    private final MetricsHistoryRepository metricsHistoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final EcologyProperties props;

    @Transactional
    public EcologyReportDto evaluateAndSave(Long datasetId, Long actorUserId) {
        return evaluateAndSave(datasetId, actorUserId, null, null);
    }

    @Transactional
    public EcologyReportDto evaluateAndSave(Long datasetId,
                                            Long actorUserId,
                                            Double csvDuplicateRatio,
                                            Double csvMissingRatio) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Набор данных не найден: " + datasetId));

        // V — объёмная метрика [0..1][cite: 1]
        double sizeMb = dataset.getSizeMb() != null ? dataset.getSizeMb().doubleValue() : 0.0;
        double v = clamp01(sizeMb / props.getMaxSizeMb());

        // D — доля дубликатов (по MD5 наборов и по строкам CSV)
        double dByHash = 0.0;
        if (dataset.getChecksumMd5() != null && !dataset.getChecksumMd5().isBlank()) {
            long sameHashCount = datasetRepository.findByChecksumMd5(dataset.getChecksumMd5()).size();
            if (sameHashCount > 1) {
                dByHash = (double) (sameHashCount - 1) / sameHashCount;
            }
        }
        double d = clamp01(csvDuplicateRatio != null
                ? Math.max(dByHash, csvDuplicateRatio)
                : dByHash);

        // S — доля «тёмных данных» (без обращений > staleDays)[cite: 1]
        double s = 0.0;
        Instant cutoff = Instant.now().minus(props.getStaleDays(), ChronoUnit.DAYS);
        if (dataset.getLastAccessedAt() == null || dataset.getLastAccessedAt().isBefore(cutoff)) {
            s = 1.0;
        }

        // Q — доля записей/полей с дефектами качества (метаданные + строки CSV)
        double qMeta = calculateMissingFieldsRatio(dataset);
        double q = clamp01(csvMissingRatio != null
                ? Math.max(qMeta, csvMissingRatio)
                : qMeta);

        // Интегральный индекс экологичности I[cite: 1]
        var w = props.getWeights();
        double rawIndex = w.getVolume() * (1.0 - v)
                + w.getDuplicates() * (1.0 - d)
                + w.getStale() * (1.0 - s)
                + w.getQuality() * (1.0 - q);
        double integralIndex = clamp01(rawIndex);

        metricsHistoryRepository.save(MetricsHistory.builder()
                .dataset(dataset)
                .integralIndex((float) integralIndex)
                .calculatedAt(Instant.now())
                .build());

        List<EcologyReportDto.RecommendationDto> recommendations =
                buildRecommendations(dataset, sizeMb, v, d, s, q, integralIndex);

        if (actorUserId != null) {
            auditLogRepository.save(AuditLog.builder()
                    .timestamp(Instant.now())
                    .userId(actorUserId)
                    .action(AuditAction.EDIT)
                    .details(Map.of(
                            "event", "ECOLOGY_EVALUATION",
                            "datasetId", dataset.getId(),
                            "datasetName", dataset.getName(),
                            "integralIndex", integralIndex
                    ))
                    .build());
        }

        return EcologyReportDto.builder()
                .datasetId(dataset.getId())
                .datasetName(dataset.getName())
                .volumeMetric(round4(v))
                .duplicatesMetric(round4(d))
                .staleMetric(round4(s))
                .qualityMetric(round4(q))
                .integralIndex(round4(integralIndex))
                .recommendations(recommendations)
                .build();
    }

    private double calculateMissingFieldsRatio(Dataset dataset) {
        int totalChecks = 4;
        int missing = 0;

        if (dataset.getCategory() == null || dataset.getCategory().isBlank()) missing++;
        if (dataset.getChecksumMd5() == null || dataset.getChecksumMd5().isBlank()) missing++;
        if (dataset.getRecordCount() == null || dataset.getRecordCount() <= 0) missing++;
        if (dataset.getSizeMb() == null || dataset.getSizeMb().compareTo(BigDecimal.ZERO) <= 0) missing++;

        return (double) missing / totalChecks;
    }

    private List<EcologyReportDto.RecommendationDto> buildRecommendations(
            Dataset dataset, double sizeMb, double v, double d, double s, double q, double currentIndex) {
        List<EcologyReportDto.RecommendationDto> list = new ArrayList<>();
        var w = props.getWeights();

        if (d > 0.0) {
            double freedMb = round2(sizeMb * d);
            double newV = clamp01((sizeMb - freedMb) / props.getMaxSizeMb());
            double projected = clamp01(w.getVolume() * (1.0 - newV)
                    + w.getDuplicates() * 1.0
                    + w.getStale() * (1.0 - s)
                    + w.getQuality() * (1.0 - q));

            list.add(EcologyReportDto.RecommendationDto.builder()
                    .action("DELETE_DUPLICATES")
                    .description("Удалить дубликаты набора «" + dataset.getName() + "» — освободить " + freedMb + " МБ")
                    .freedMb(freedMb)
                    .projectedIndex(round4(Math.max(currentIndex, projected)))
                    .build());
        }

        if (s > 0.0) {
            double freedMb = round2(sizeMb * s);
            double projected = clamp01(w.getVolume() * (1.0 - v)
                    + w.getDuplicates() * (1.0 - d)
                    + w.getStale() * 1.0
                    + w.getQuality() * (1.0 - q));

            list.add(EcologyReportDto.RecommendationDto.builder()
                    .action("ARCHIVE_STALE")
                    .description("Архивировать неиспользуемые данные («" + dataset.getName() + "») — освободить " + freedMb + " МБ")
                    .freedMb(freedMb)
                    .projectedIndex(round4(Math.max(currentIndex, projected)))
                    .build());
        }

        if (q > 0.0) {
            double projected = clamp01(w.getVolume() * (1.0 - v)
                    + w.getDuplicates() * (1.0 - d)
                    + w.getStale() * (1.0 - s)
                    + w.getQuality() * 1.0);

            list.add(EcologyReportDto.RecommendationDto.builder()
                    .action("FIX_QUALITY")
                    .description("Заполнить недостающие метаданные и обязательные поля для «" + dataset.getName() + "»")
                    .freedMb(0.0)
                    .projectedIndex(round4(Math.max(currentIndex, projected)))
                    .build());
        }

        return list;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
