package com.risk.dupdetect.controller;

import com.risk.dupdetect.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * REST controller for duplicate reports and exports.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    @Autowired
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/duplicates")
    public ResponseEntity<List<Map<String, Object>>> getDuplicateReport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "day") String groupBy) {

        Instant fromInstant = from != null ? Instant.parse(from) : Instant.now().minus(7, ChronoUnit.DAYS);
        Instant toInstant = to != null ? Instant.parse(to) : Instant.now();

        List<Map<String, Object>> report = reportService.getDuplicateReport(fromInstant, toInstant, groupBy);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/duplicates/export")
    public ResponseEntity<String> exportReport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "csv") String format) {

        Instant fromInstant = from != null ? Instant.parse(from) : Instant.now().minus(7, ChronoUnit.DAYS);
        Instant toInstant = to != null ? Instant.parse(to) : Instant.now();

        String content = reportService.exportCsv(fromInstant, toInstant);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"duplicates.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(content);
    }
}
