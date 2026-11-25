package org.daylight.insurancecompamy.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CsvInventoryResult {
    private BigDecimal sizeMb;
    private long recordCount;
    private String checksumMd5;
    private double duplicateRowRatio;   // доля дублирующихся строк внутри файла [0..1]
    private double missingFieldRatio;   // доля строк с пустыми обязательными полями [0..1]
}
