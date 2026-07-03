package com.risk.dupdetect.service;

import com.risk.dupdetect.domain.DuplicateRecord;
import com.risk.dupdetect.repository.DuplicateRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating duplicate reports and aggregate metrics.
 */
@Service
@Slf4j
public class ReportService {

    private final DuplicateRecordRepository duplicateRecordRepository;

    @Autowired
    public ReportService(DuplicateRecordRepository duplicateRecordRepository) {
        this.duplicateRecordRepository = duplicateRecordRepository;
    }

    /**
     * Returns a time-grouped summary of duplicates caught per period.
     */
    public List<Map<String, Object>> getDuplicateReport(Instant from, Instant to, String groupBy) {
        List<DuplicateRecord> all = duplicateRecordRepository.findAll();

        // Filter by time range
        List<DuplicateRecord> filtered = all.stream()
                .filter(r -> r.getDetectedAt() != null &&
                        !r.getDetectedAt().isBefore(from) &&
                        !r.getDetectedAt().isAfter(to))
                .collect(Collectors.toList());

        // Group by hour or day
        Map<String, Long> grouped = new TreeMap<>();
        for (DuplicateRecord record : filtered) {
            String period;
            if ("hour".equalsIgnoreCase(groupBy)) {
                period = record.getDetectedAt().truncatedTo(ChronoUnit.HOURS).toString();
            } else {
                period = record.getDetectedAt().truncatedTo(ChronoUnit.DAYS).toString();
            }
            grouped.merge(period, 1L, Long::sum);
        }

        // Count by tier
        long exactCount = filtered.stream().filter(r -> r.getMatchTier() != null &&
                r.getMatchTier().name().equals("EXACT")).count();
        long probableCount = filtered.stream().filter(r -> r.getMatchTier() != null &&
                r.getMatchTier().name().equals("PROBABLE")).count();

        List<Map<String, Object>> result = new ArrayList<>();
        grouped.forEach((period, count) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("period", period);
            entry.put("totalDuplicates", count);
            result.add(entry);
        });

        // Add summary
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("period", "TOTAL");
        summary.put("totalDuplicates", (long) filtered.size());
        summary.put("exactDuplicates", exactCount);
        summary.put("probableDuplicates", probableCount);
        result.add(summary);

        return result;
    }

    /**
     * Exports duplicates as a CSV string.
     */
    public String exportCsv(Instant from, Instant to) {
        List<DuplicateRecord> all = duplicateRecordRepository.findAll();
        List<DuplicateRecord> filtered = all.stream()
                .filter(r -> r.getDetectedAt() != null &&
                        !r.getDetectedAt().isBefore(from) &&
                        !r.getDetectedAt().isAfter(to))
                .collect(Collectors.toList());

        StringBuilder csv = new StringBuilder();
        csv.append("id,matchTier,timeDeltaMs,resolution,detectedAt,originalTxnId,duplicateTxnId\n");
        for (DuplicateRecord r : filtered) {
            csv.append(r.getId()).append(",")
               .append(r.getMatchTier()).append(",")
               .append(r.getTimeDeltaMs()).append(",")
               .append(r.getResolution()).append(",")
               .append(r.getDetectedAt()).append(",")
               .append(r.getOriginalTransaction() != null ? r.getOriginalTransaction().getId() : "").append(",")
               .append(r.getDuplicateTransaction() != null ? r.getDuplicateTransaction().getId() : "")
               .append("\n");
        }
        return csv.toString();
    }
}
