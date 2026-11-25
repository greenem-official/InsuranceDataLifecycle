package org.daylight.insurancecompamy.service;

import org.daylight.insurancecompamy.dto.CsvInventoryResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

@Service
public class CsvInventoryService {
    public CsvInventoryResult analyze(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String md5 = calculateMd5(bytes);

            double mb = bytes.length / (1024.0 * 1024.0);
            BigDecimal sizeMb = BigDecimal.valueOf(mb).setScale(4, RoundingMode.HALF_UP);
            if (sizeMb.compareTo(BigDecimal.ZERO) <= 0 && bytes.length > 0) {
                sizeMb = BigDecimal.valueOf(0.01);
            }

            long totalDataRows = 0;
            long duplicateRows = 0;
            long rowsWithMissingFields = 0;

            Set<String> seenRows = new HashSet<>();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean isFirstLine = true;

                while ((line = reader.readLine()) != null) {
                    if (isFirstLine) {
                        isFirstLine = false;
                        continue; // пропускаем строку заголовков CSV
                    }
                    if (line.isBlank()) {
                        continue;
                    }

                    totalDataRows++;

                    if (!seenRows.add(line.trim())) {
                        duplicateRows++;
                    }

                    // Разбиваем по запятой или точке с запятой (часто встречается в выгрузках из Excel/1С)
                    String[] cells = line.split("[,;]", -1);
                    boolean hasEmpty = false;
                    for (String cell : cells) {
                        if (cell == null || cell.trim().isEmpty()) {
                            hasEmpty = true;
                            break;
                        }
                    }
                    if (hasEmpty) {
                        rowsWithMissingFields++;
                    }
                }
            }

            double dupRatio = totalDataRows > 0 ? (double) duplicateRows / totalDataRows : 0.0;
            double missRatio = totalDataRows > 0 ? (double) rowsWithMissingFields / totalDataRows : 0.0;

            return CsvInventoryResult.builder()
                    .sizeMb(sizeMb)
                    .recordCount(totalDataRows)
                    .checksumMd5(md5)
                    .duplicateRowRatio(round4(dupRatio))
                    .missingFieldRatio(round4(missRatio))
                    .build();
        } catch (IOException e) {
            throw new IllegalArgumentException("Не удалось прочитать CSV-файл: " + e.getMessage(), e);
        }
    }

    private String calculateMd5(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Алгоритм MD5 недоступен", e);
        }
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
