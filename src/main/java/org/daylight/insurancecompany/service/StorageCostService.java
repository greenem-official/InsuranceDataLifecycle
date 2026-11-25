package org.daylight.insurancecompany.service;

import org.daylight.insurancecompany.dto.LifecycleStatus;
import org.daylight.insurancecompany.dto.StorageCostReportDto;
import org.daylight.insurancecompany.entity.Dataset;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class StorageCostService {
    // Базовые удельные показатели для страхового хранилища (на 1 ГБ в месяц)
    private static final double RUB_PER_GB_MONTH = 15.0;
    private static final double KWH_PER_GB_MONTH = 0.12;
    private static final int STALE_DAYS_THRESHOLD = 365;

    public StorageCostReportDto calculate(List<Dataset> datasets) {
        if (datasets == null || datasets.isEmpty()) {
            return StorageCostReportDto.builder().build();
        }

        double totalMb = 0.0;
        double wastedMb = 0.0;
        Instant now = Instant.now();

        for (Dataset ds : datasets) {
            double mb = ds.getSizeMb() != null ? ds.getSizeMb().doubleValue() : 0.0;
            totalMb += mb;

            boolean isArchivedOrPendingDelete = ds.getStatus() == LifecycleStatus.ARCHIVED
                    || ds.getStatus() == LifecycleStatus.DELETE_REQUESTED
                    || ds.getStatus() == LifecycleStatus.APPROVED_FOR_DELETE;

            long daysSinceAccess = ds.getLastAccessedAt() != null
                    ? ChronoUnit.DAYS.between(ds.getLastAccessedAt(), now)
                    : 0L;

            int limitDays = ds.getRetentionDays() != null ? ds.getRetentionDays() : STALE_DAYS_THRESHOLD;
            boolean expiredByPolicy = daysSinceAccess > Math.min(limitDays, STALE_DAYS_THRESHOLD);

            if (isArchivedOrPendingDelete || expiredByPolicy) {
                wastedMb += mb;
            }
        }

        double totalGb = round4(totalMb / 1024.0);
        double wastedGb = round4(wastedMb / 1024.0);

        double monthlyCost = round2(totalGb * RUB_PER_GB_MONTH);
        double annualCost = round2(monthlyCost * 12.0);
        double potentialMonthlySavings = round2(wastedGb * RUB_PER_GB_MONTH);
        double potentialAnnualSavings = round2(potentialMonthlySavings * 12.0);

        double monthlyKwh = round4(totalGb * KWH_PER_GB_MONTH);
        double annualKwh = round4(monthlyKwh * 12.0);
        double potentialMonthlyKwh = round4(wastedGb * KWH_PER_GB_MONTH);
        double potentialAnnualKwh = round4(potentialMonthlyKwh * 12.0);

        return StorageCostReportDto.builder()
                .totalSizeGb(totalGb)
                .wastedSizeGb(wastedGb)
                .monthlyCostRub(monthlyCost)
                .annualCostRub(annualCost)
                .potentialMonthlySavingsRub(potentialMonthlySavings)
                .potentialAnnualSavingsRub(potentialAnnualSavings)
                .monthlyEnergyKwh(monthlyKwh)
                .annualEnergyKwh(annualKwh)
                .potentialMonthlyEnergySavingsKwh(potentialMonthlyKwh)
                .potentialAnnualEnergySavingsKwh(potentialAnnualKwh)
                .build();
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double round4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
