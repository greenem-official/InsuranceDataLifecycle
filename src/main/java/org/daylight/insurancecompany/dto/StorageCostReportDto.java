package org.daylight.insurancecompany.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageCostReportDto {
    private double totalSizeGb;
    private double wastedSizeGb;

    private double monthlyCostRub;
    private double annualCostRub;
    private double potentialMonthlySavingsRub;
    private double potentialAnnualSavingsRub;

    private double monthlyEnergyKwh;
    private double annualEnergyKwh;
    private double potentialMonthlyEnergySavingsKwh;
    private double potentialAnnualEnergySavingsKwh;
}
