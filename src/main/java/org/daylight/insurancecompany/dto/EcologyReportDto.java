package org.daylight.insurancecompany.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EcologyReportDto {
    private Long datasetId;
    private String datasetName;

    // Нормированные метрики в диапазоне [0..1]
    private double volumeMetric;      // V: объёмная метрика (размер в МБ)
    private double duplicatesMetric;  // D: доля дубликатов
    private double staleMetric;       // S: доля «тёмных данных» (> 365 дней без обращений)[cite: 1]
    private double qualityMetric;     // Q: доля записей с пустыми обязательными полями[cite: 1]

    // Интегральный индекс экологичности [0..1]
    private double integralIndex;

    // Практические рекомендации и прогнозируемый эффект[cite: 1]
    private List<RecommendationDto> recommendations;

    @Data
    @Builder
    public static class RecommendationDto {
        private String action;           // например: DELETE_DUPLICATES, ARCHIVE_STALE, FIX_QUALITY
        private String description;      // например: «Удалить дубликаты — освободить 120.00 МБ»[cite: 1]
        private double freedMb;          // прогнозируемый объём освобождения (МБ)[cite: 1]
        private double projectedIndex;   // прогнозируемый индекс экологичности после действия[cite: 1]
    }
}
